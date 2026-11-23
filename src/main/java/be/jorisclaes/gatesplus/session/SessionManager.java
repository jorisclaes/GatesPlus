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
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import be.jorisclaes.gatesplus.GatesPlus;
import be.jorisclaes.gatesplus.config.GatesPlusConfig;
import be.jorisclaes.gatesplus.gate.Gate;
import be.jorisclaes.gatesplus.gate.GateDirection;
import be.jorisclaes.gatesplus.gate.GateManager;
import be.jorisclaes.gatesplus.gate.GateService;
import be.jorisclaes.gatesplus.gate.InfoPanel;
import be.jorisclaes.gatesplus.marker.MarkerBlocks;
import be.jorisclaes.gatesplus.network.Network;
import be.jorisclaes.gatesplus.network.PanelPayload;
import be.jorisclaes.gatesplus.network.SelectionSyncPayload;
import be.jorisclaes.gatesplus.util.Messages;
import be.jorisclaes.gatesplus.wand.Wand;

/** Create/edit sessions per player, wand clicks, markers and the info panel. */
public final class SessionManager {
	private final GateService service;
	private final Map<UUID, CreateSession> sessions = new HashMap<>();
	/** Fallback markers (fake blocks) sent to players without the client mod, to restore later. */
	private final Map<UUID, Set<BlockPos>> fakeBlocks = new HashMap<>();
	private final Map<UUID, List<BlockPos>> invalid = new HashMap<>();

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
		Wand.give(player);
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
		Wand.give(player);
		sync(player);
		return s;
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
	private static void removeMarkerBlocks(ServerPlayer player, CreateSession s) {
		MarkerBlocks.removeItems(player);
		for (CreateSession.PlacedMarker m : s.placedMarkers()) {
			if (m.level().isLoaded(m.pos()) && MarkerBlocks.isMarker(m.level().getBlockState(m.pos()))) {
				m.level().removeBlock(m.pos(), false);
			}
		}
	}

	/**
	 * A marker was placed: it immediately sets its own corner (as if clicked with the wand), and an
	 * earlier marker of the same corner is removed, so there is at most one "1" and one "2".
	 * @return false when the marker cannot be used here (it is then removed again)
	 */
	public boolean onMarkerPlaced(ServerPlayer player, ServerLevel level, BlockPos pos) {
		CreateSession s = get(player);
		if (s == null) return false;
		CreateSession.Point corner = MarkerBlocks.cornerOf(level.getBlockState(pos));
		if (corner == null) return false;
		String dim = GateManager.dimensionId(level);
		if (s.dimension() != null && !s.dimension().equals(dim)) {
			Messages.actionBar(player, "wand.other_dimension");
			// give the item back (survival consumed it); the block itself is removed by the caller
			if (!player.hasInfiniteMaterials()) {
				player.getInventory().add(new net.minecraft.world.item.ItemStack(
						corner == CreateSession.Point.CORNER1 ? MarkerBlocks.TOP_LEFT_ITEM : MarkerBlocks.BOTTOM_RIGHT_ITEM));
			}
			return false;
		}
		s.setDimension(dim);

		CreateSession.PlacedMarker old = s.putPlacedMarker(corner, level, pos);
		if (old != null && !old.pos().equals(pos) && old.level().isLoaded(old.pos())
				&& MarkerBlocks.cornerOf(old.level().getBlockState(old.pos())) == corner) {
			old.level().removeBlock(old.pos(), false);
		}

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
			BlockState state = level.getBlockState(clicked);
			Gate tmp = boxOf(s);
			int max = GatesPlus.get().config().limits().maxTriggerDistance();
			if (tmp.contains(clicked.getX(), clicked.getY(), clicked.getZ())) {
				Messages.actionBar(player, "trigger.inside_gate");
			} else if (be.jorisclaes.gatesplus.gate.GateGeometry.distanceToBox(tmp, clicked.getX(), clicked.getY(), clicked.getZ()) > max) {
				Messages.actionBar(player, "trigger.too_far", max);
			} else {
				CreateSession.SetResult r = s.clickTrigger(clicked, BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
				Messages.actionBar(player, r == CreateSession.SetResult.TRIGGER ? "wand.trigger_set" : "wand.trigger_replaced",
						clicked.getX(), clicked.getY(), clicked.getZ());
			}
			sync(player);
			return;
		}

		// A corner marker selects itself and always sets its own corner (top-left = 1,
		// bottom-right = 2). Any other block selects the air in front of the clicked face
		// (gates are only built in air; markers are replaced by the gate).
		CreateSession.Point markerCorner = MarkerBlocks.cornerOf(level.getBlockState(clicked));
		boolean hadBox = s.hasBox();
		BlockPos pos = markerCorner != null ? clicked : clicked.relative(face);
		String suffix = markerCorner != null ? Messages.raw(player, "wand.on_marker") : "";
		CreateSession.SetResult r = markerCorner != null ? s.setCorner(markerCorner, pos) : s.clickCorner(pos);
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

	/** Right-click with the wand: undo the last point. */
	public void onRightClick(ServerPlayer player) {
		CreateSession s = get(player);
		if (s == null) {
			Messages.actionBar(player, "wand.no_session");
			return;
		}
		invalid.remove(player.getUUID());
		CreateSession.Point p = s.undo();
		if (p == null) Messages.actionBar(player, "wand.nothing_to_undo");
		else Messages.actionBar(player, "wand.undo." + p.name().toLowerCase(java.util.Locale.ROOT));
		sync(player);
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
		if (Network.hasClientMod(player)) {
			Network.send(player, new SelectionSyncPayload(true, Optional.ofNullable(s.corner1()),
					Optional.ofNullable(s.corner2()), Optional.ofNullable(s.trigger()),
					s.mode() == CreateSession.Mode.TRIGGER, GatesPlus.get().config().limits().maxTriggerDistance(),
					invalid.getOrDefault(player.getUUID(), List.of())));
			Network.send(player, InfoPanel.forSession(player, s));
		} else {
			sendFakeMarkers(player, s);
		}
	}

	private void clearMarkers(ServerPlayer player) {
		if (Network.hasClientMod(player)) {
			Network.send(player, SelectionSyncPayload.EMPTY);
			Network.send(player, PanelPayload.hide(PanelPayload.INFO));
		}
		restoreFakeBlocks(player);
	}

	/** Fallback without the client mod: fake blocks only this player sees. */
	private void sendFakeMarkers(ServerPlayer player, CreateSession s) {
		restoreFakeBlocks(player);
		if (s.dimension() != null && !s.dimension().equals(GateManager.dimensionId(player.level()))) return;
		Map<BlockPos, BlockState> fake = new HashMap<>();
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
							if (onEdge >= 2) fake.put(new BlockPos(x, y, z), edge);
						}
			}
		}
		if (s.corner1() != null) fake.put(s.corner1(), Blocks.GOLD_BLOCK.defaultBlockState());
		if (s.corner2() != null) fake.put(s.corner2(), Blocks.REDSTONE_BLOCK.defaultBlockState());
		if (s.trigger() != null) fake.put(s.trigger(), Blocks.DIAMOND_BLOCK.defaultBlockState());
		for (BlockPos p : invalid.getOrDefault(player.getUUID(), List.of())) {
			fake.put(p, Blocks.STAINED_GLASS.red().defaultBlockState());
		}
		for (var e : fake.entrySet()) player.connection.send(new ClientboundBlockUpdatePacket(e.getKey(), e.getValue()));
		fakeBlocks.put(player.getUUID(), new HashSet<>(fake.keySet()));
		Messages.actionBar(player, "session.summary_actionbar", InfoPanel.shortSummary(player, s));
	}

	private void restoreFakeBlocks(ServerPlayer player) {
		Set<BlockPos> sent = fakeBlocks.remove(player.getUUID());
		if (sent == null) return;
		ServerLevel level = player.level();
		for (BlockPos p : sent) player.connection.send(new ClientboundBlockUpdatePacket(level, p));
	}
}
