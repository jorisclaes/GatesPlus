package be.jorisclaes.gatesplus.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.math.Transformation;

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import be.jorisclaes.gatesplus.GatesPlus;
import be.jorisclaes.gatesplus.config.GatesPlusConfig;
import be.jorisclaes.gatesplus.gate.Gate;
import be.jorisclaes.gatesplus.gate.GateDirection;
import be.jorisclaes.gatesplus.gate.GateManager;
import be.jorisclaes.gatesplus.gate.GateService;
import be.jorisclaes.gatesplus.gate.InfoPanel;
import be.jorisclaes.gatesplus.marker.MarkerBlocks;
import be.jorisclaes.gatesplus.marker.MarkerLabels;
import be.jorisclaes.gatesplus.mixin.BlockDisplayAccessor;
import be.jorisclaes.gatesplus.mixin.DisplayAccessor;
import be.jorisclaes.gatesplus.network.MarkersPayload;
import be.jorisclaes.gatesplus.network.Network;
import be.jorisclaes.gatesplus.network.PanelPayload;
import be.jorisclaes.gatesplus.network.SelectionSyncPayload;
import be.jorisclaes.gatesplus.util.Messages;
import be.jorisclaes.gatesplus.util.Panels;
import be.jorisclaes.gatesplus.wand.Wand;

/** Create/edit sessions per player, wand clicks, markers and the info panel. */
public final class SessionManager {
	private final GateService service;
	private final Map<UUID, CreateSession> sessions = new HashMap<>();
	/** Fallback markers (fake blocks) sent to players without the client mod, to restore later. */
	private final Map<UUID, Set<BlockPos>> fakeBlocks = new HashMap<>();
	/** Blocks in the way of the selected box (kept up to date while the session runs). */
	private final Map<UUID, List<BlockPos>> invalid = new HashMap<>();
	/** Without the client mod: glowing outlines (fake block displays) on the blocks in the way. */
	private final Map<UUID, Map<BlockPos, Highlight>> highlights = new HashMap<>();

	/** A fake block display only one player sees; state = the real block it outlines. */
	private record Highlight(int entityId, BlockState state) {}

	/** Glow colour of blocks in the way. */
	private static final int BLOCKED_GLOW = 0xFF2020;
	/** Larger boxes are not scanned live (confirm still checks every block). */
	private static final int MAX_SCAN_VOLUME = 32_768;
	/** Players without the client mod whose fake blocks must be sent again next tick. */
	private final Set<UUID> resendFake = new HashSet<>();

	public SessionManager(GateService service) {
		this.service = service;
	}

	public CreateSession get(ServerPlayer player) {
		return sessions.get(player.getUUID());
	}

	public int count() {
		return sessions.size();
	}

	public boolean isEditing(Gate gate) {
		for (CreateSession s : sessions.values()) {
			if (s.type() == CreateSession.Type.EDIT && s.gateId() == gate.id()) return true;
		}
		return false;
	}

	// ------------------------------------------------------------------ start / end

	public CreateSession startCreate(ServerPlayer player) {
		end(player, false);
		GatesPlusConfig.Defaults d = GatesPlus.get().config().defaults();
		CreateSession s = new CreateSession(CreateSession.Type.CREATE, player.getUUID(), 0);
		s.block = d.block();
		s.indestructible = d.indestructible();
		s.fireproof = d.fireproof();
		s.animationEnabled = d.animationEnabled();
		s.direction = GateDirection.parse(d.direction());
		s.animationTicks = d.animationTicks();
		s.redstoneInverted = false;
		s.groupId = null;
		sessions.put(player.getUUID(), s);
		giveTools(player);
		sync(player);
		return s;
	}

	public CreateSession startEdit(ServerPlayer player, Gate gate) {
		end(player, false);
		CreateSession s = new CreateSession(CreateSession.Type.EDIT, player.getUUID(), gate.id());
		s.prefill(gate.dimension(), new BlockPos(gate.minX(), gate.maxY(), gate.minZ()),
				new BlockPos(gate.maxX(), gate.minY(), gate.maxZ()),
				new BlockPos(gate.triggerX(), gate.triggerY(), gate.triggerZ()), gate.triggerBlock());
		s.block = gate.block();
		s.indestructible = gate.indestructible();
		s.fireproof = gate.fireproof();
		s.animationEnabled = gate.animationEnabled();
		s.direction = gate.direction();
		s.animationTicks = gate.animationTicks();
		s.redstoneInverted = gate.redstoneInverted();
		s.groupId = gate.groupId();
		sessions.put(player.getUUID(), s);
		service.lock(gate);
		giveTools(player);
		sync(player);
		return s;
	}

	/**
	 * The selection tool of config session.selection-tool: the axe (axe/both), or the two marker
	 * blocks instead of the axe (markers).
	 */
	private static void giveTools(ServerPlayer player) {
		if (GatesPlus.get().config().session().selectionTool().axe()) Wand.give(player);
		else MarkerBlocks.giveSet(player);
	}

	/** Ends the session: removes the wand, markers and panel. */
	public void end(ServerPlayer player, boolean notify) {
		CreateSession s = sessions.remove(player.getUUID());
		invalid.remove(player.getUUID());
		if (s == null) return;
		if (s.type() == CreateSession.Type.EDIT) service.unlock(s.gateId());
		Wand.removeAll(player);
		removeMarkerBlocks(player, s);
		clearMarkers(player);
		if (notify) Messages.send(player, "session.cancelled");
	}

	/**
	 * Marker blocks only exist during a session: unused ones leave the inventory, placed ones the world
	 * (markers that ended up inside a gate were already replaced by it).
	 */
	private void removeMarkerBlocks(ServerPlayer player, CreateSession s) {
		MarkerBlocks.removeItems(player);
		boolean had = !s.placedMarkers().isEmpty();
		for (CreateSession.PlacedMarker m : s.placedMarkers()) takeBack(m);
		s.placedMarkers().clear();
		if (had) broadcastMarkers();
	}

	/** All placed vanilla markers, for the client mod (it shows them as textured marker blocks). */
	public MarkersPayload markersPayload() {
		if (MarkerBlocks.custom()) return MarkersPayload.EMPTY;
		List<MarkersPayload.Entry> list = new ArrayList<>();
		for (CreateSession s : sessions.values()) {
			for (CreateSession.PlacedMarker m : s.placedMarkers()) {
				list.add(new MarkersPayload.Entry(GateManager.dimensionId(m.level()), m.pos(),
						m.corner() == CreateSession.Point.CORNER1 ? 1 : 2));
			}
		}
		return new MarkersPayload(list);
	}

	/** Sends the placed markers to every player with the client mod. */
	public void broadcastMarkers() {
		MinecraftServer server = GatesPlus.get().server();
		if (server == null || MarkerBlocks.custom()) return;
		MarkersPayload payload = markersPayload();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) Network.send(p, payload);
	}

	/**
	 * The marker placed in a session at this position, or null. Vanilla markers are ordinary blocks,
	 * so a marker only counts while the block is still the marker block of its corner.
	 */
	public CreateSession.PlacedMarker markerAt(net.minecraft.world.level.Level level, BlockPos pos) {
		if (sessions.isEmpty()) return null;
		for (CreateSession s : sessions.values()) {
			CreateSession.PlacedMarker m = s.placedMarkerAt(level, pos);
			if (m != null && level.getBlockState(pos).is(MarkerBlocks.blockFor(m.corner()))) return m;
		}
		return null;
	}

	/** A marker block at pos: a tracked marker, or any own (custom) marker block. */
	public boolean isMarker(net.minecraft.world.level.Level level, BlockPos pos) {
		return MarkerBlocks.isCustomMarker(level.getBlockState(pos)) || markerAt(level, pos) != null;
	}

	/** Removes a placed marker from the world; a gate block it replaced (edit session) comes back. */
	private static void takeBack(CreateSession.PlacedMarker m) {
		MarkerLabels.remove(m.level(), m.label());
		if (!m.level().isLoaded(m.pos()) || !m.level().getBlockState(m.pos()).is(MarkerBlocks.blockFor(m.corner()))) return;
		if (m.replaced() != null) m.level().setBlock(m.pos(), m.replaced(), net.minecraft.world.level.block.Block.UPDATE_ALL);
		else m.level().removeBlock(m.pos(), false);
	}

	/**
	 * Confirming an edit: markers that replaced gate blocks give them back first, so the gate is
	 * complete again before the edit is applied (markers on air are cleared by the build as usual).
	 */
	public void restoreReplacedGateBlocks(CreateSession s) {
		var it = s.placedMarkers().iterator();
		while (it.hasNext()) {
			CreateSession.PlacedMarker m = it.next();
			if (m.replaced() == null) continue;
			takeBack(m);
			it.remove();
		}
		broadcastMarkers();
	}

	/**
	 * A marker was broken: forget it, put back the gate block it replaced (if any), and remove the
	 * corner it marked from the selection of the session it belongs to.
	 */
	public void onMarkerBroken(ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		for (CreateSession s : new ArrayList<>(sessions.values())) {
			CreateSession.PlacedMarker m = s.removePlacedMarkerAt(level, pos);
			if (m == null) continue;
			MarkerLabels.remove(level, m.label());
			broadcastMarkers();
			CreateSession.Point corner = m.corner();
			if (m.replaced() != null && level.getBlockState(pos).isAir()) {
				level.setBlock(pos, m.replaced(), net.minecraft.world.level.block.Block.UPDATE_ALL);
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(s.player());
			// the marker item goes back to the session owner (no drop), unless they still have one
			if (owner != null && !m.fromWand()) MarkerBlocks.giveBack(owner, corner);
			BlockPos selected = corner == CreateSession.Point.CORNER1 ? s.corner1()
					: corner == CreateSession.Point.CORNER2 ? s.corner2() : null;
			if (!pos.equals(selected) || !s.clearCorner(corner)) continue;
			if (owner == null) continue;
			invalid.remove(owner.getUUID());
			Messages.actionBar(owner, "wand.undo." + corner.name().toLowerCase(java.util.Locale.ROOT));
			sync(owner);
		}
	}

	/**
	 * Edit session: right-click with a marker on a block of the gate being edited. The corner of a
	 * closed gate is a gate block, so the marker takes its place until the session ends.
	 * @return true when handled (the marker replaced the gate block)
	 */
	public boolean placeMarkerOnGate(ServerPlayer player, ServerLevel level, BlockPos pos,
			net.minecraft.world.item.ItemStack stack) {
		CreateSession s = get(player);
		if (s == null || s.type() != CreateSession.Type.EDIT) return false;
		Gate gate = service.manager().byId(s.gateId());
		if (gate == null || service.manager().gateAt(level, pos) != gate) return false;
		BlockState old = level.getBlockState(pos);
		if (!old.is(service.manager().blockOf(gate))) return false;
		CreateSession.Point corner = MarkerBlocks.cornerOfItem(stack);
		if (corner == null) return false;
		level.setBlock(pos, MarkerBlocks.blockFor(corner).defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
		if (!onMarkerPlaced(player, level, pos, corner, old)) {
			level.setBlock(pos, old, net.minecraft.world.level.block.Block.UPDATE_ALL);
			return true;
		}
		if (!player.hasInfiniteMaterials()) stack.shrink(1);
		return true;
	}

	/**
	 * A marker was placed: it immediately sets its own corner (as if clicked with the wand), and an
	 * earlier marker of the same corner is removed, so there is at most one "1" and one "2".
	 * @return false when the marker cannot be used here (it is then removed again)
	 */
	public boolean onMarkerPlaced(ServerPlayer player, ServerLevel level, BlockPos pos, CreateSession.Point corner) {
		return onMarkerPlaced(player, level, pos, corner, null);
	}

	/** @param replaced the gate block the marker replaced, or null when it was placed in air */
	private boolean onMarkerPlaced(ServerPlayer player, ServerLevel level, BlockPos pos, CreateSession.Point corner,
			BlockState replaced) {
		CreateSession s = get(player);
		if (s == null || corner == null) return false;
		String dim = GateManager.dimensionId(level);
		if (s.dimension() != null && !s.dimension().equals(dim)) {
			Messages.actionBar(player, "wand.other_dimension");
			// give the item back (survival consumed it); the block itself is removed by the caller
			if (replaced == null && !player.hasInfiniteMaterials()) {
				player.getInventory().add(MarkerBlocks.create(player, corner));
			}
			return false;
		}
		s.setDimension(dim);

		java.util.UUID label = MarkerBlocks.custom() ? null : MarkerLabels.spawn(level, pos, corner);
		CreateSession.PlacedMarker old = s.putPlacedMarker(corner, level, pos, replaced, label, false);
		if (old != null && !old.pos().equals(pos)) takeBack(old);
		else if (old != null) MarkerLabels.remove(old.level(), old.label());
		broadcastMarkers();
		s.touch();

		// config session.marker-sets-corner: false = the marker only marks, the wand sets the corner
		if (!GatesPlus.get().config().session().markersSetCorner()) return true;

		invalid.remove(player.getUUID());
		boolean hadBox = s.hasBox();
		CreateSession.SetResult r = s.setCorner(corner, pos);
		String key = r == CreateSession.SetResult.CORNER1 ? "wand.corner1" : "wand.corner2";
		String suffix = Messages.raw(player, "wand.on_marker");
		if (s.hasBox()) {
			Messages.actionBar(player, key + "_box", pos.getX(), pos.getY(), pos.getZ(), suffix,
					s.sizeX(), s.sizeY(), s.sizeZ(), s.volume());
		} else {
			Messages.actionBar(player, key, pos.getX(), pos.getY(), pos.getZ(), suffix);
		}
		if (!hadBox && s.hasBox()) Messages.send(player, "wand.hint_trigger");
		sync(player);
		return true;
	}

	/** Player left: drop the session without touching the (offline) inventory twice. */
	public void onDisconnect(ServerPlayer player) {
		CreateSession s = sessions.remove(player.getUUID());
		fakeBlocks.remove(player.getUUID());
		highlights.remove(player.getUUID());
		invalid.remove(player.getUUID());
		if (s == null) return;
		if (s.type() == CreateSession.Type.EDIT) service.unlock(s.gateId());
		Wand.removeAll(player);
		removeMarkerBlocks(player, s);
	}

	public void cancelEditsOf(Gate gate) {
		MinecraftServer server = GatesPlus.get().server();
		for (CreateSession s : new ArrayList<>(sessions.values())) {
			if (s.type() == CreateSession.Type.EDIT && s.gateId() == gate.id()) {
				ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(s.player());
				if (p != null) end(p, true);
				else sessions.remove(s.player());
			}
		}
	}

	public void cancelAll() {
		MinecraftServer server = GatesPlus.get().server();
		for (CreateSession s : new ArrayList<>(sessions.values())) {
			ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(s.player());
			if (p != null) end(p, false);
		}
		sessions.clear();
	}

	/** Every second: cancel sessions without activity for session.timeout-minutes. */
	public void tickTimeouts(MinecraftServer server) {
		long timeout = GatesPlus.get().config().session().timeoutMinutes() * 60_000L;
		long now = System.currentTimeMillis();
		for (CreateSession s : new ArrayList<>(sessions.values())) {
			if (now - s.lastActivityMillis() < timeout) continue;
			ServerPlayer p = server.getPlayerList().getPlayer(s.player());
			if (p != null) {
				end(p, false);
				Messages.send(p, "session.timeout", GatesPlus.get().config().session().timeoutMinutes());
			} else {
				sessions.remove(s.player());
			}
		}
	}

	// ------------------------------------------------------------------ wand clicks

	/** Left-click on a block face with the wand. */
	public void onLeftClick(ServerPlayer player, BlockPos clicked, Direction face) {
		CreateSession s = get(player);
		if (s == null) {
			Messages.actionBar(player, "wand.no_session");
			return;
		}
		if (!s.debounceClick()) return;
		ServerLevel level = player.level();
		String dim = GateManager.dimensionId(level);
		if (s.dimension() != null && !s.dimension().equals(dim)) {
			Messages.actionBar(player, "wand.other_dimension");
			return;
		}
		s.setDimension(dim);
		invalid.remove(player.getUUID());

		if (s.mode() == CreateSession.Mode.TRIGGER) {
			trySetTrigger(player, s, clicked);
			sync(player);
			return;
		}

		// A corner marker selects itself and always sets its own corner (top-left = 1,
		// bottom-right = 2). Any other block selects the air in front of the clicked face
		// (gates are only built in air; markers are replaced by the gate).
		CreateSession.PlacedMarker clickedMarker = markerAt(level, clicked);
		CreateSession.Point markerCorner = clickedMarker != null ? clickedMarker.corner()
				: MarkerBlocks.cornerOfCustom(level.getBlockState(clicked));
		boolean hadBox = s.hasBox();
		BlockPos pos = markerCorner != null ? clicked : clicked.relative(face);
		String suffix = markerCorner != null ? Messages.raw(player, "wand.on_marker") : "";
		CreateSession.SetResult r = markerCorner != null ? s.setCorner(markerCorner, pos) : s.clickCorner(pos);
		if (markerCorner == null) {
			placeWandMarker(player, level, s, pos,
					r == CreateSession.SetResult.CORNER1 ? CreateSession.Point.CORNER1 : CreateSession.Point.CORNER2);
		}
		removeStaleMarkers(player, s);
		String key = r == CreateSession.SetResult.CORNER1 ? "wand.corner1" : "wand.corner2";
		if (s.hasBox()) {
			Messages.actionBar(player, key + "_box", pos.getX(), pos.getY(), pos.getZ(), suffix,
					s.sizeX(), s.sizeY(), s.sizeZ(), s.volume());
		} else {
			Messages.actionBar(player, key, pos.getX(), pos.getY(), pos.getZ(), suffix);
		}
		if (!hadBox && s.hasBox()) Messages.send(player, "wand.hint_trigger");
		sync(player);
	}

	/**
	 * Makes the block at pos the trigger (wand click in trigger mode, or the block the player looks
	 * at with /gp set trigger when markers are the only selection tool). Needs both corners.
	 * @return true when the trigger was set
	 */
	private boolean trySetTrigger(ServerPlayer player, CreateSession s, BlockPos pos) {
		ServerLevel level = player.level();
		BlockState state = level.getBlockState(pos);
		Gate tmp = boxOf(s);
		int max = GatesPlus.get().config().limits().maxTriggerDistance();
		if (tmp.contains(pos.getX(), pos.getY(), pos.getZ())) {
			Messages.actionBar(player, "trigger.inside_gate");
			return false;
		}
		if (be.jorisclaes.gatesplus.gate.GateGeometry.distanceToBox(tmp, pos.getX(), pos.getY(), pos.getZ()) > max) {
			Messages.actionBar(player, "trigger.too_far", max);
			return false;
		}
		CreateSession.SetResult r = s.clickTrigger(pos, BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
		Messages.actionBar(player, r == CreateSession.SetResult.TRIGGER ? "wand.trigger_set" : "wand.trigger_replaced",
				pos.getX(), pos.getY(), pos.getZ());
		return true;
	}

	/**
	 * /gp set trigger without the axe: the block the player looks at becomes the trigger.
	 * @return false when the player looks at no block or the block can't be the trigger
	 */
	public boolean setTriggerLookedAt(ServerPlayer player) {
		CreateSession s = get(player);
		if (s == null || !s.hasBox()) return false;
		String dim = GateManager.dimensionId(player.level());
		if (s.dimension() != null && !s.dimension().equals(dim)) {
			Messages.actionBar(player, "wand.other_dimension");
			return false;
		}
		net.minecraft.world.phys.HitResult hit = player.pick(player.blockInteractionRange(), 1.0f, false);
		if (!(hit instanceof net.minecraft.world.phys.BlockHitResult bh) || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
			Messages.send(player, "set.trigger.look_at");
			return false;
		}
		invalid.remove(player.getUUID());
		boolean ok = trySetTrigger(player, s, bh.getBlockPos());
		sync(player);
		return ok;
	}

	/** Right-click with the wand: undo the last point. */
	public void onRightClick(ServerPlayer player) {
		CreateSession s = get(player);
		if (s == null) {
			Messages.actionBar(player, "wand.no_session");
			return;
		}
		if (!s.acceptRightClick(player.level().getServer().getTickCount())) return;
		invalid.remove(player.getUUID());
		CreateSession.Point p = s.undo();
		removeStaleMarkers(player, s);
		if (p == null) Messages.actionBar(player, "wand.nothing_to_undo");
		else Messages.actionBar(player, "wand.undo." + p.name().toLowerCase(java.util.Locale.ROOT));
		sync(player);
	}

	/**
	 * A wand click set a corner: a real marker block goes on that (air) position, exactly as if the
	 * player had placed the marker item, so the axe and the markers look and behave the same. The
	 * earlier marker of that corner disappears (a marker placed from an item comes back as item).
	 * Not air there (water, grass, ...): no block, only the fake marker shown to the player.
	 */
	private void placeWandMarker(ServerPlayer player, ServerLevel level, CreateSession s, BlockPos pos,
			CreateSession.Point corner) {
		CreateSession.PlacedMarker old = s.placedMarker(corner);
		if (old != null && old.level() == level && old.pos().equals(pos)) return;
		if (old != null) {
			s.removePlacedMarker(corner);
			takeBack(old);
			if (!old.fromWand()) MarkerBlocks.giveBack(player, corner);
		}
		if (level.getBlockState(pos).isAir() && !level.isOutsideBuildHeight(pos)) {
			level.setBlock(pos, MarkerBlocks.blockFor(corner).defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
			java.util.UUID label = MarkerBlocks.custom() ? null : MarkerLabels.spawn(level, pos, corner);
			s.putPlacedMarker(corner, level, pos, null, label, true);
		}
		broadcastMarkers();
	}

	/**
	 * Removes placed markers that no longer mark their corner (the corner was undone or moved), so
	 * a marker in the world always means "this corner is here". Markers placed from an item come
	 * back as item. With session.marker-sets-corner off, item markers only mark a spot and stay.
	 */
	private void removeStaleMarkers(ServerPlayer player, CreateSession s) {
		boolean itemsSetCorner = GatesPlus.get().config().session().markersSetCorner();
		boolean changed = false;
		for (CreateSession.Point corner : new CreateSession.Point[] { CreateSession.Point.CORNER1, CreateSession.Point.CORNER2 }) {
			CreateSession.PlacedMarker m = s.placedMarker(corner);
			if (m == null || (!m.fromWand() && !itemsSetCorner)) continue;
			BlockPos selected = corner == CreateSession.Point.CORNER1 ? s.corner1() : s.corner2();
			if (m.pos().equals(selected)) continue;
			s.removePlacedMarker(corner);
			takeBack(m);
			if (!m.fromWand()) MarkerBlocks.giveBack(player, corner);
			changed = true;
		}
		if (changed) broadcastMarkers();
	}

	/** A temporary gate holding just the session box (for distance checks). */
	public static Gate boxOf(CreateSession s) {
		Gate g = new Gate(0, s.player(), "", "", 0);
		BlockPos a = s.corner1(), b = s.corner2();
		g.setBox(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
		return g;
	}

	public void showInvalid(ServerPlayer player, List<BlockPos> positions) {
		invalid.put(player.getUUID(), List.copyOf(positions));
		sync(player);
	}

	// ------------------------------------------------------------------ markers + panel

	public void sync(ServerPlayer player) {
		CreateSession s = get(player);
		if (s == null) {
			clearMarkers(player);
			return;
		}
		s.touch();
		refresh(player, s);
	}

	/** Sends the selection, the blocks in the way and the panel again, without counting as activity. */
	private void refresh(ServerPlayer player, CreateSession s) {
		List<BlockPos> blocked = blockedPositions(player.level(), s);
		invalid.put(player.getUUID(), blocked);
		if (Network.hasClientMod(player)) {
			Network.send(player, new SelectionSyncPayload(true, Optional.ofNullable(s.corner1()),
					Optional.ofNullable(s.corner2()), Optional.ofNullable(s.trigger()),
					s.mode() == CreateSession.Mode.TRIGGER, GatesPlus.get().config().limits().maxTriggerDistance(),
					invalid.getOrDefault(player.getUUID(), List.of())));
		} else {
			sendFakeMarkers(player, s);
			showHighlights(player, blocked);
		}
		Panels.show(player, InfoPanel.forSession(player, s)); // HUD panel or sidebar
	}

	/**
	 * The blocks in the selected box that stop the gate from being built (the player has to remove
	 * them first), at most {@link SelectionSyncPayload#MAX_INVALID}.
	 */
	private List<BlockPos> blockedPositions(ServerLevel level, CreateSession s) {
		if (!s.hasBox() || s.volume() > MAX_SCAN_VOLUME) return List.of();
		if (s.dimension() != null && !s.dimension().equals(GateManager.dimensionId(level))) return List.of();
		Gate existing = s.type() == CreateSession.Type.EDIT ? service.manager().byId(s.gateId()) : null;
		Block oldBlock = existing != null ? service.manager().blockOf(existing) : null;
		BlockPos a = s.corner1(), b = s.corner2();
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(a, b)) {
			if (!level.isLoaded(p)) continue;
			if (service.inTheWay(level, p, existing, oldBlock)) {
				out.add(p.immutable());
				if (out.size() >= SelectionSyncPayload.MAX_INVALID) break;
			}
		}
		return out;
	}

	/**
	 * Every 10 ticks: blocks placed in or removed from the selected box show up (or disappear) as
	 * "in the way" without the player having to touch the selection.
	 */
	public void tickBlocked(MinecraftServer server) {
		for (CreateSession s : sessions.values()) {
			ServerPlayer p = server.getPlayerList().getPlayer(s.player());
			if (p == null) continue;
			List<BlockPos> blocked = blockedPositions(p.level(), s);
			boolean changed = !blocked.equals(invalid.getOrDefault(p.getUUID(), List.of()));
			if (!changed && !Network.hasClientMod(p)) {
				Map<BlockPos, Highlight> shown = highlights.getOrDefault(p.getUUID(), Map.of());
				changed = shown.size() != blocked.size();
				for (BlockPos pos : blocked) {
					Highlight h = shown.get(pos);
					if (h == null || h.state() != p.level().getBlockState(pos)) changed = true;
				}
			}
			if (changed) refresh(p, s);
		}
	}

	/** Outlines the blocks in the way with a red glow (fake entities only this player sees). */
	private void showHighlights(ServerPlayer player, List<BlockPos> blocked) {
		clearHighlights(player);
		if (blocked.isEmpty()) return;
		ServerLevel level = player.level();
		Map<BlockPos, Highlight> shown = new HashMap<>();
		for (BlockPos p : blocked) {
			BlockState real = level.getBlockState(p);
			Display.BlockDisplay d = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
			if (d == null) continue;
			// A slightly smaller copy hidden inside the real block: only its glow outline is seen
			// (through walls too). Liquids and the like have no block model: red glass instead.
			boolean model = real.getRenderShape() == RenderShape.MODEL;
			float scale = model ? 0.98f : 1f, off = (1f - scale) / 2f;
			d.setPos(p.getX(), p.getY(), p.getZ());
			((BlockDisplayAccessor) d).gatesplus$setBlockState(model ? real : Blocks.STAINED_GLASS.red().defaultBlockState());
			((DisplayAccessor) d).gatesplus$setTransformation(new Transformation(new Vector3f(off, off, off),
					new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
			((DisplayAccessor) d).gatesplus$setGlowColorOverride(BLOCKED_GLOW);
			d.setGlowingTag(true);
			player.connection.send(new ClientboundAddEntityPacket(d.getId(), d.getUUID(), p.getX(), p.getY(), p.getZ(),
					0f, 0f, EntityTypes.BLOCK_DISPLAY, 0, Vec3.ZERO, 0d));
			var data = d.getEntityData().getNonDefaultValues();
			if (data != null) player.connection.send(new ClientboundSetEntityDataPacket(d.getId(), data));
			shown.put(p.immutable(), new Highlight(d.getId(), real));
		}
		highlights.put(player.getUUID(), shown);
	}

	private void clearHighlights(ServerPlayer player) {
		Map<BlockPos, Highlight> shown = highlights.remove(player.getUUID());
		if (shown == null || shown.isEmpty()) return;
		player.connection.send(new ClientboundRemoveEntitiesPacket(
				shown.values().stream().mapToInt(Highlight::entityId).toArray()));
	}

	/**
	 * After a wand left-click Fabric sends the real block at the clicked position (air for a fake
	 * corner / edge block), which wipes a fake block the player clicked or (creative) kept breaking.
	 * The fake blocks are sent again on the next tick, after that update.
	 */
	public void resendFakeLater(ServerPlayer player) {
		if (!Network.hasClientMod(player)) resendFake.add(player.getUUID());
	}

	/** Every tick: send the fake blocks again for players queued by {@link #resendFakeLater}. */
	public void tickFakeResend(MinecraftServer server) {
		if (resendFake.isEmpty()) return;
		for (UUID id : resendFake) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			CreateSession s = p == null ? null : sessions.get(id);
			if (s != null) sendFakeMarkers(p, s);
		}
		resendFake.clear();
	}

	private void clearMarkers(ServerPlayer player) {
		if (Network.hasClientMod(player)) {
			Network.send(player, SelectionSyncPayload.EMPTY);
		}
		Panels.hide(player, PanelPayload.INFO);
		restoreFakeBlocks(player);
		clearHighlights(player);
	}

	/** Fallback without the client mod: fake blocks only this player sees. */
	private void sendFakeMarkers(ServerPlayer player, CreateSession s) {
		restoreFakeBlocks(player);
		if (s.dimension() != null && !s.dimension().equals(GateManager.dimensionId(player.level()))) return;
		Map<BlockPos, BlockState> fake = new HashMap<>();
		// never cover a block in the way: the player must see (and remove) it; it gets a red outline
		Set<BlockPos> blocked = new HashSet<>(invalid.getOrDefault(player.getUUID(), List.of()));
		if (s.hasBox()) {
			BlockPos a = s.corner1(), b = s.corner2();
			int x1 = Math.min(a.getX(), b.getX()), x2 = Math.max(a.getX(), b.getX());
			int y1 = Math.min(a.getY(), b.getY()), y2 = Math.max(a.getY(), b.getY());
			int z1 = Math.min(a.getZ(), b.getZ()), z2 = Math.max(a.getZ(), b.getZ());
			if ((long) (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1) <= 4096) {
				BlockState edge = Blocks.STAINED_GLASS.lime().defaultBlockState();
				for (int x = x1; x <= x2; x++)
					for (int y = y1; y <= y2; y++)
						for (int z = z1; z <= z2; z++) {
							int onEdge = (x == x1 || x == x2 ? 1 : 0) + (y == y1 || y == y2 ? 1 : 0) + (z == z1 || z == z2 ? 1 : 0);
							BlockPos p = new BlockPos(x, y, z);
							if (onEdge >= 2 && !blocked.contains(p)) fake.put(p, edge);
						}
			}
		}
		// corners look like the marker blocks; a real marker there is shown as itself
		ServerLevel level = player.level();
		fakeCorner(fake, level, s.corner1(), CreateSession.Point.CORNER1);
		fakeCorner(fake, level, s.corner2(), CreateSession.Point.CORNER2);
		if (s.trigger() != null) fake.put(s.trigger(), Blocks.DIAMOND_BLOCK.defaultBlockState());
		for (var e : fake.entrySet()) player.connection.send(new ClientboundBlockUpdatePacket(e.getKey(), e.getValue()));
		fakeBlocks.put(player.getUUID(), new HashSet<>(fake.keySet()));
	}

	private void fakeCorner(Map<BlockPos, BlockState> fake, ServerLevel level, BlockPos pos, CreateSession.Point corner) {
		if (pos == null) return;
		if (isMarker(level, pos)) fake.remove(pos);
		else fake.put(pos, MarkerBlocks.vanillaBlockFor(corner).defaultBlockState());
	}

	private void restoreFakeBlocks(ServerPlayer player) {
		Set<BlockPos> sent = fakeBlocks.remove(player.getUUID());
		if (sent == null) return;
		ServerLevel level = player.level();
		for (BlockPos p : sent) player.connection.send(new ClientboundBlockUpdatePacket(level, p));
	}
}
