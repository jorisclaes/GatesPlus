package be.jorisclaes.gatesplus.gate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import be.jorisclaes.gatesplus.GatesPlus;
import be.jorisclaes.gatesplus.animation.GateAnimator;
import be.jorisclaes.gatesplus.config.GatesPlusConfig;
import be.jorisclaes.gatesplus.group.Group;
import be.jorisclaes.gatesplus.group.GroupManager;
import be.jorisclaes.gatesplus.marker.MarkerBlocks;
import be.jorisclaes.gatesplus.session.CreateSession;
import be.jorisclaes.gatesplus.session.SessionManager;
import be.jorisclaes.gatesplus.storage.StorageException;
import be.jorisclaes.gatesplus.util.Messages;
import be.jorisclaes.gatesplus.util.Perms;

/**
 * Central place for everything that changes gates in the world: building, opening/closing,
 * confirming create/edit sessions, removing, repairing and reacting to damage.
 */
public final class GateService {
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

	private final GateManager manager = new GateManager();
	private final GroupManager groups = new GroupManager();
	private final ReturnChestTracker returnChests = new ReturnChestTracker();
	private final GateAnimator animator = new GateAnimator(this::placeRow);
	private final SessionManager sessions = new SessionManager(this);
	/** Gates currently open in an edit session; they ignore redstone. */
	private final Set<Integer> locked = new HashSet<>();
	/** Gates whose owner was already told the gate can't close (avoids chat spam). */
	private final Set<Integer> blockedNotified = new HashSet<>();
	private final Map<Integer, Long> lastVerify = new HashMap<>();
	/**
	 * Closed gates that could not close completely because of a block in the opening: the positions
	 * left empty (see {@link GateGeometry#heldBack}). Not stored; recomputed after a restart.
	 */
	private final Map<Integer, Set<BlockPos>> heldBack = new HashMap<>();

	public GateManager manager() { return manager; }
	public GroupManager groups() { return groups; }
	public ReturnChestTracker returnChests() { return returnChests; }
	public GateAnimator animator() { return animator; }
	public SessionManager sessions() { return sessions; }

	private static GatesPlusConfig cfg() {
		return GatesPlus.get().config();
	}

	// ------------------------------------------------------------------ lifecycle

	/** Loads all data synchronously during server start. */
	public void loadAll() throws StorageException {
		var storage = GatesPlus.get().storage().blocking();
		manager.clear();
		groups.clear();
		for (Group g : storage.loadGroups()) groups.add(g);
		for (Gate g : storage.loadGates()) manager.add(g);
		returnChests.load(storage.loadReturnChests());
		GatesPlus.LOGGER.info("GatesPlus loaded {} gates and {} groups", manager.count(), groups.all().size());
	}

	/** Puts every animated gate in its final state; called when the server stops. */
	public void shutdown() {
		animator.cancelAll();
		sessions.cancelAll();
		locked.clear();
	}

	public ServerLevel level(Gate gate) {
		MinecraftServer server = GatesPlus.get().server();
		Identifier id = Identifier.tryParse(gate.dimension());
		return server == null || id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}

	public void save(Gate gate) {
		Gate copy = gate.copy();
		GatesPlus.get().storage().run(s -> s.updateGate(copy));
	}

	public boolean isLocked(Gate gate) {
		return locked.contains(gate.id());
	}

	public void lock(Gate gate) {
		locked.add(gate.id());
	}

	public void unlock(int gateId) {
		locked.remove(gateId);
	}

	// ------------------------------------------------------------------ world operations

	private BlockState stateOf(Gate gate) {
		return manager.blockOf(gate).defaultBlockState();
	}

	/** Marker blocks inside the gate box disappear when the gate is built (they are free). */
	public void clearMarkers(ServerLevel level, Gate gate) {
		BlockState air = Blocks.AIR.defaultBlockState();
		for (BlockPos pos : GateGeometry.positions(gate)) {
			if (MarkerBlocks.isMarker(level.getBlockState(pos))) level.setBlock(pos, air, Block.UPDATE_ALL);
		}
	}

	/** Turns the gate's own blocks back into air. Blocks of other types are left alone. */
	public void clearBlocks(ServerLevel level, Gate gate) {
		Block block = manager.blockOf(gate);
		BlockState air = Blocks.AIR.defaultBlockState();
		for (BlockPos pos : GateGeometry.positions(gate)) {
			if (level.getBlockState(pos).is(block)) level.setBlock(pos, air, Block.UPDATE_ALL);
		}
	}

	/** Positions of a closed gate that no longer hold the gate block. */
	public int countMissing(ServerLevel level, Gate gate) {
		Block block = manager.blockOf(gate);
		int missing = 0;
		for (BlockPos pos : GateGeometry.positions(gate)) if (!level.getBlockState(pos).is(block)) missing++;
		return missing;
	}

	/**
	 * Something in the opening that stops the gate from closing: items and vehicles always; players
	 * and mobs only with redstone.entity-in-gate = wait.
	 */
	public boolean entitiesInside(ServerLevel level, Gate gate) {
		AABB box = new AABB(gate.minX(), gate.minY(), gate.minZ(), gate.maxX() + 1, gate.maxY() + 1, gate.maxZ() + 1);
		boolean livingBlocks = cfg().redstone().entityInGate() == GatesPlusConfig.EntityInGate.WAIT;
		return !level.getEntities((Entity) null, box,
				e -> (livingBlocks && e instanceof LivingEntity) || e instanceof ItemEntity || e instanceof VehicleEntity).isEmpty();
	}

	/** Positions of the (open) gate that a block in the opening keeps empty when it closes. */
	private Set<BlockPos> computeHeldBack(ServerLevel level, Gate gate) {
		Block block = manager.blockOf(gate);
		return GateGeometry.heldBack(gate, pos -> {
			BlockState s = level.getBlockState(pos);
			return !s.isAir() && !s.is(block);
		});
	}

	private Set<BlockPos> heldBackOf(Gate gate) {
		return heldBack.getOrDefault(gate.id(), Set.of());
	}

	/**
	 * redstone.entity-in-gate = push: players and mobs in the given (just closed) positions are moved
	 * out of the gate to the nearest side, across its thin axis.
	 */
	private void pushOut(ServerLevel level, Gate gate, List<BlockPos> positions) {
		if (cfg().redstone().entityInGate() != GatesPlusConfig.EntityInGate.PUSH || positions.isEmpty()) return;
		AABB box = new AABB(gate.minX(), gate.minY(), gate.minZ(), gate.maxX() + 1, gate.maxY() + 1, gate.maxZ() + 1);
		boolean depthIsZ = GateGeometry.alongX(gate);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator())) {
			AABB bb = e.getBoundingBox();
			boolean hit = false;
			for (BlockPos p : positions) {
				if (bb.intersects(new AABB(p))) {
					hit = true;
					break;
				}
			}
			if (!hit) continue;
			double half = e.getBbWidth() / 2 + 0.05;
			double c = depthIsZ ? e.getZ() : e.getX();
			double lo = depthIsZ ? gate.minZ() : gate.minX();
			double hi = (depthIsZ ? gate.maxZ() : gate.maxX()) + 1;
			double target = c - lo <= hi - c ? lo - half : hi + half;
			if (depthIsZ) e.teleportTo(e.getX(), e.getY(), target);
			else e.teleportTo(target, e.getY(), e.getZ());
		}
	}

	/**
	 * A closed gate held back by a block: once that block is gone, the line closes further
	 * (players/mobs are handled as configured). Called by the ticker.
	 */
	public void extendClosed(ServerLevel level, Gate gate) {
		Set<BlockPos> held = heldBack.get(gate.id());
		if (held == null || held.isEmpty() || gate.isOpen() || animator.isAnimating(gate)) return;
		Set<BlockPos> now = computeHeldBack(level, gate);
		List<BlockPos> free = new ArrayList<>();
		for (BlockPos pos : held) if (!now.contains(pos) && level.getBlockState(pos).isAir()) free.add(pos);
		if (free.isEmpty()) return;
		boolean wait = cfg().redstone().entityInGate() == GatesPlusConfig.EntityInGate.WAIT;
		Set<BlockPos> stillHeld = new HashSet<>(now);
		List<BlockPos> place = new ArrayList<>();
		for (BlockPos pos : free) {
			boolean occupied = !level.getEntities((Entity) null, new AABB(pos),
					e -> (wait && e instanceof LivingEntity) || e instanceof ItemEntity || e instanceof VehicleEntity).isEmpty();
			if (occupied) stillHeld.add(pos);
			else place.add(pos);
		}
		BlockState state = stateOf(gate);
		pushOut(level, gate, place);
		for (BlockPos pos : place) level.setBlock(pos, state, Block.UPDATE_ALL);
		heldBack.put(gate.id(), stillHeld);
		if (stillHeld.isEmpty()) blockedNotified.remove(gate.id());
	}

	private boolean shouldAnimate(ServerLevel level, Gate gate) {
		return cfg().animation().enabled() && gate.animationEnabled()
				&& GateGeometry.allChunksLoaded(level, gate)
				&& animator.runningCount() < cfg().limits().maxConcurrentAnimations();
	}

	/** Opens a functional gate (redstone). */
	public void open(ServerLevel level, Gate gate) {
		animator.cancel(gate);
		heldBack.remove(gate.id());
		clearBlocks(level, gate);
		gate.setOpen(true);
		if (shouldAnimate(level, gate)) animator.start(level, gate, stateOf(gate), true, level.getServer().getTickCount());
		save(gate);
	}

	/** Closes a functional gate (redstone). The caller checked for entities and obstructions. */
	public void close(ServerLevel level, Gate gate) {
		animator.cancel(gate);
		// a block in the opening: every line closes up to that block (never replacing it)
		Set<BlockPos> held = computeHeldBack(level, gate);
		heldBack.put(gate.id(), held);
		gate.setOpen(false);
		if (shouldAnimate(level, gate)) {
			animator.start(level, gate, stateOf(gate), false, level.getServer().getTickCount(), held);
		} else {
			List<BlockPos> rows = new ArrayList<>(GateGeometry.positions(gate));
			rows.removeAll(held);
			placeRow(level, gate, rows);
		}
		if (held.isEmpty()) blockedNotified.remove(gate.id());
		else notifyBlocked(gate);
		save(gate);
	}

	/** A closing row finished animating: its real blocks appear (only on air, never replacing anything). */
	private void placeRow(ServerLevel level, Gate gate, List<BlockPos> positions) {
		if (gate.isOpen() || gate.status() != GateStatus.OK || manager.byId(gate.id()) != gate) return;
		BlockState state = stateOf(gate);
		Set<BlockPos> held = heldBackOf(gate);
		List<BlockPos> place = new ArrayList<>();
		for (BlockPos pos : positions) {
			if (!held.contains(pos) && level.getBlockState(pos).isAir()) place.add(pos);
		}
		pushOut(level, gate, place);
		for (BlockPos pos : place) level.setBlock(pos, state, Block.UPDATE_ALL);
	}

	/** Instantly opens (no animation), e.g. when broken or before an edit is applied. */
	public void forceOpen(ServerLevel level, Gate gate) {
		animator.cancel(gate);
		heldBack.remove(gate.id());
		if (!gate.isOpen() && level != null) clearBlocks(level, gate);
		gate.setOpen(true);
	}

	/** Called by protection hooks and verification when gate blocks disappeared. */
	/**
	 * Gate blocks disappeared. The gate stays exactly as it is (remaining blocks stay in place, the
	 * hole stays a hole) and simply stops working until it is repaired.
	 */
	public void markBroken(ServerLevel level, Gate gate, int lost) {
		if (lost <= 0) return;
		animator.cancel(gate); // finish a running animation first so all rows are real blocks
		gate.lose(lost);
		if (gate.status() != GateStatus.NO_TRIGGER) gate.setStatus(GateStatus.BROKEN);
		save(gate);
		// no chat message for normal players: /gp list shows the gate in red; dev mode still gets it
		notifyGateDev(gate, "gate.broken", gate.name(), gate.placedCount(), gate.totalCount());
	}

	public void markNoTrigger(ServerLevel level, Gate gate) {
		if (gate.status() == GateStatus.NO_TRIGGER) return;
		gate.setStatus(GateStatus.NO_TRIGGER);
		forceOpen(level, gate);
		save(gate);
		notifyGate(gate, "gate.no_trigger", gate.name());
	}

	/**
	 * A block was placed back on the trigger position: the gate works again right away. It gets the
	 * status it would have without the trigger problem (OK, or INCOMPLETE when blocks are missing);
	 * the ticker then closes it when the trigger is unpowered.
	 */
	public void restoreTrigger(ServerLevel level, Gate gate, String triggerBlock) {
		if (gate.status() != GateStatus.NO_TRIGGER) return;
		gate.setTrigger(gate.triggerX(), gate.triggerY(), gate.triggerZ(), triggerBlock);
		gate.setStatus(gate.missingCount() == 0 ? GateStatus.OK : GateStatus.INCOMPLETE);
		if (gate.status() == GateStatus.INCOMPLETE) {
			// not all blocks invested: show the part that exists, the gate doesn't work yet
			gate.setOpen(false);
			placeBlocksUpTo(level, gate, gate.placedCount());
		}
		blockedNotified.remove(gate.id());
		save(gate);
		notifyGate(gate, "gate.trigger_restored", gate.name());
	}

	/**
	 * Safety net run before toggling and every few seconds: a closed gate with missing blocks
	 * (fire, pistons, commands, ...) is marked broken.
	 */
	public boolean verify(ServerLevel level, Gate gate, long now, boolean force) {
		// locked = in an edit session: markers may temporarily replace gate blocks
		if (gate.isOpen() || animator.isAnimating(gate) || isLocked(gate)) return true;
		if (!force) {
			Long last = lastVerify.get(gate.id());
			if (last != null && now - last < 100) return true;
		}
		lastVerify.put(gate.id(), now);
		int present = gate.totalCount() - countMissing(level, gate);
		if (gate.status() == GateStatus.OK) {
			// positions a block in the opening keeps empty are not missing (recomputed after a restart)
			Block block = manager.blockOf(gate);
			for (BlockPos pos : heldBack.computeIfAbsent(gate.id(), id -> computeHeldBack(level, gate))) {
				if (!level.getBlockState(pos).is(block)) present++;
			}
		}
		if (present < gate.placedCount()) {
			markBroken(level, gate, gate.placedCount() - present);
			return false;
		}
		if (present > gate.placedCount()) {
			// a player filled a hole by hand with the right block: it now belongs to the gate
			int added = present - gate.placedCount();
			gate.invest(added, added);
			if (gate.missingCount() == 0 && gate.status() != GateStatus.NO_TRIGGER) {
				gate.setStatus(GateStatus.OK);
				notifyGate(gate, "repair.done", gate.name(), gate.placedCount(), gate.totalCount());
			}
			save(gate);
		}
		return true;
	}

	/**
	 * Places the gate block on the first {@code count} air positions, bottom row first. Used for gates
	 * that are only partly paid for: that part is visible, the gate doesn't work until it is complete.
	 */
	public void placeBlocksUpTo(ServerLevel level, Gate gate, int count) {
		BlockState state = stateOf(gate);
		int placed = 0;
		for (BlockPos pos : GateGeometry.positions(gate)) {
			if (placed >= count) break;
			if (level.getBlockState(pos).is(state.getBlock())) {
				placed++;
			} else if (level.getBlockState(pos).isAir()) {
				level.setBlock(pos, state, Block.UPDATE_ALL);
				placed++;
			}
		}
	}

	/** A gate could only close up to a block in its opening; tells the owner once. */
	public void notifyBlocked(Gate gate) {
		if (blockedNotified.add(gate.id())) notifyGate(gate, "gate.blocked", gate.name());
	}

	/** Sends a message to the gate owner and every online member of the gate's group. */
	public void notifyGate(Gate gate, String key, Object... args) {
		notify(gate, false, key, args);
	}

	/** Like {@link #notifyGate}, but only for players with dev mode (/gp devstats) on. */
	public void notifyGateDev(Gate gate, String key, Object... args) {
		notify(gate, true, key, args);
	}

	private void notify(Gate gate, boolean devOnly, String key, Object... args) {
		MinecraftServer server = GatesPlus.get().server();
		if (server == null) return;
		Set<UUID> targets = new HashSet<>();
		targets.add(gate.ownerUuid());
		Group group = groups.byId(gate.groupId());
		if (group != null) {
			targets.add(group.ownerUuid());
			targets.addAll(group.members().keySet());
		}
		for (UUID uuid : targets) {
			ServerPlayer p = server.getPlayerList().getPlayer(uuid);
			if (p != null && (!devOnly || GatesPlus.get().devStats().isOn(p))) Messages.send(p, key, args);
		}
	}

	// ------------------------------------------------------------------ lookup for commands

	/**
	 * Resolves "12", "name" or "group:name" for a player. Sends an error and returns null when not
	 * found or ambiguous. Admins (admin.edit) may address any gate by id.
	 */
	public Gate resolve(ServerPlayer player, String arg, boolean admin) {
		if (arg.matches("\\d+")) {
			Gate g = manager.byId(Integer.parseInt(arg));
			if (g != null && (admin || groups.canSee(g, player.getUUID()))) return g;
			Messages.send(player, "gate.not_found", arg);
			return null;
		}
		int colon = arg.indexOf(':');
		if (colon > 0) {
			Group group = groups.byName(arg.substring(0, colon));
			Gate g = group == null ? null : manager.byName(Gate.scopeKey(null, group.id()), arg.substring(colon + 1));
			if (g != null && (admin || groups.canSee(g, player.getUUID()))) return g;
			Messages.send(player, "gate.not_found", arg);
			return null;
		}
		List<Gate> found = new ArrayList<>();
		Gate personal = manager.byName(Gate.scopeKey(player.getUUID(), null), arg);
		if (personal != null) return personal;
		for (Group group : groups.groupsOf(player.getUUID())) {
			Gate g = manager.byName(Gate.scopeKey(null, group.id()), arg);
			if (g != null) found.add(g);
		}
		if (found.size() == 1) return found.getFirst();
		if (found.isEmpty()) {
			Messages.send(player, "gate.not_found", arg);
		} else {
			StringBuilder sb = new StringBuilder();
			for (Gate g : found) sb.append(" #").append(g.id()).append(" ").append(groups.scopeLabel(g)).append(':').append(g.name());
			Messages.send(player, "gate.ambiguous", arg, sb.toString().trim());
		}
		return null;
	}

	// ------------------------------------------------------------------ confirm (create / edit)

	/** @return true when the gate was created/updated and the session can end */
	public boolean confirm(ServerPlayer player, CreateSession s, String nameArg) {
		GatesPlusConfig cfg = cfg();
		ServerLevel level = player.level();
		boolean edit = s.type() == CreateSession.Type.EDIT;
		Gate existing = edit ? manager.byId(s.gateId()) : null;
		if (edit && existing == null) return fail(player, "edit.gone");
		if (!s.hasBox()) return fail(player, "confirm.no_box");
		if (s.trigger() == null) return fail(player, "confirm.no_trigger");
		if (!GateManager.dimensionId(level).equals(s.dimension())) return fail(player, "confirm.wrong_dimension");

		String name = nameArg != null ? nameArg : existing != null ? existing.name() : null;
		if (name == null) return fail(player, "confirm.need_name");
		if (!NAME.matcher(name).matches() || name.matches("\\d+")) return fail(player, "confirm.bad_name", name);

		Block block = BlockFilter.allowedBlock(s.block);
		if (block == null) return fail(player, "set.block.not_allowed", s.block);

		Gate cand = edit ? existing.copy()
				: new Gate(0, player.getUUID(), player.getGameProfile().name(), name, System.currentTimeMillis());
		cand.setName(name);
		cand.setDimension(s.dimension());
		BlockPos c1 = s.corner1(), c2 = s.corner2();
		cand.setBox(c1.getX(), c1.getY(), c1.getZ(), c2.getX(), c2.getY(), c2.getZ());
		cand.setTrigger(s.trigger().getX(), s.trigger().getY(), s.trigger().getZ(), s.triggerBlock());
		cand.setBlock(BuiltInRegistries.BLOCK.getKey(block).toString());
		cand.setIndestructible(s.indestructible);
		cand.setFireproof(s.fireproof);
		cand.setAnimationEnabled(s.animationEnabled);
		cand.setDirection(s.direction);
		cand.setAnimationTicks(s.animationTicks);
		cand.setRedstoneInverted(s.redstoneInverted);
		cand.setGroupId(s.groupId);

		// limits
		boolean bypass = Perms.BYPASS_LIMITS.check(player);
		var lim = cfg.limits();
		int width = Math.max(cand.sizeX(), cand.sizeZ()), depth = Math.min(cand.sizeX(), cand.sizeZ());
		if (!bypass) {
			if (width > lim.maxWidth() || cand.sizeY() > lim.maxHeight() || depth > lim.maxDepth()) {
				return fail(player, "confirm.too_big_dims", width, cand.sizeY(), depth, lim.maxWidth(), lim.maxHeight(),
						lim.maxDepth());
			}
			if (cand.totalCount() > lim.maxGateBlocks()) {
				return fail(player, "confirm.too_big", cand.totalCount(), lim.maxGateBlocks());
			}
			if (!edit && manager.ownedBy(player.getUUID()).size() >= lim.maxGatesPerPlayer()) {
				return fail(player, "create.max_gates", lim.maxGatesPerPlayer());
			}
		}

		// group + unique name
		if (cand.groupId() != null) {
			Group group = groups.byId(cand.groupId());
			if (group == null || (!group.contains(player.getUUID()) && !Perms.ADMIN_EDIT.check(player))) {
				return fail(player, "set.group.not_member");
			}
		}
		Gate sameName = manager.byName(cand.scopeKey(), name);
		if (sameName != null && sameName != existing) return fail(player, "confirm.name_taken", name);

		if (!GateGeometry.allChunksLoaded(level, cand)) return fail(player, "confirm.not_loaded");

		// trigger
		BlockPos trigger = s.trigger();
		if (cand.contains(trigger.getX(), trigger.getY(), trigger.getZ())) return fail(player, "trigger.inside_gate");
		if (GateGeometry.distanceToBox(cand, trigger.getX(), trigger.getY(), trigger.getZ()) > lim.maxTriggerDistance()) {
			return fail(player, "trigger.too_far", lim.maxTriggerDistance());
		}
		if (level.getBlockState(trigger).isAir()) return fail(player, "trigger.is_air");
		Gate triggerOwner = manager.gateWithTrigger(level, trigger);
		if (triggerOwner != null && triggerOwner != existing) return fail(player, "trigger.taken", triggerOwner.name());
		Gate triggerInGate = manager.gateAt(level, trigger);
		if (triggerInGate != null && triggerInGate != existing) return fail(player, "trigger.inside_other");

		// overlap + air-only
		Block oldBlock = existing != null ? manager.blockOf(existing) : null;
		List<BlockPos> invalid = new ArrayList<>();
		for (BlockPos pos : GateGeometry.positions(cand)) {
			Gate other = manager.gateAt(level, pos);
			if (other != null && other != existing) return fail(player, "confirm.overlap", other.name());
			Gate otherTrigger = manager.gateWithTrigger(level, pos);
			if (otherTrigger != null && otherTrigger != existing) return fail(player, "confirm.overlap_trigger", otherTrigger.name());
			BlockState st = level.getBlockState(pos);
			boolean ownBlock = existing != null && existing.contains(pos.getX(), pos.getY(), pos.getZ()) && st.is(oldBlock);
			if (!st.isAir() && !ownBlock && !MarkerBlocks.isMarker(st)) invalid.add(pos);
		}
		if (!invalid.isEmpty()) {
			sessions.showInvalid(player, invalid);
			return fail(player, "confirm.not_air", invalid.size());
		}

		if (edit) sessions.restoreReplacedGateBlocks(s);
		return edit ? applyEdit(player, level, existing, cand, block, oldBlock) : applyCreate(player, level, cand, block);
	}

	private boolean applyCreate(ServerPlayer player, ServerLevel level, Gate cand, Block block) {
		Materials.Investment inv = Materials.invest(player, block, cand.totalCount());
		cand.setPlacedCount(0);
		cand.setPaidCount(0);
		cand.invest(inv.placed(), inv.paid());
		boolean complete = cand.placedCount() == cand.totalCount();
		cand.setStatus(complete ? GateStatus.OK : GateStatus.INCOMPLETE);
		cand.setOpen(false);

		int id;
		try {
			// One small insert while confirming; the generated id is needed before anything is built.
			id = GatesPlus.get().storage().call(s -> s.insertGate(cand)).get(10, TimeUnit.SECONDS);
		} catch (Exception e) {
			GatesPlus.LOGGER.error("Could not save gate {}", cand.name(), e);
			Materials.refund(level, player, block, inv.paid());
			return fail(player, "storage.error");
		}
		cand.setId(id);
		manager.add(cand);
		manager.refreshActive(level, cand);
		clearMarkers(level, cand);
		placeBlocksUpTo(level, cand, cand.placedCount());

		Messages.send(player, "confirm.created", cand.name(), cand.id());
		if (!complete) Messages.send(player, "gate.incomplete", cand.name(), cand.placedCount(), cand.totalCount());
		return true;
	}

	private boolean applyEdit(ServerPlayer player, ServerLevel level, Gate gate, Gate cand, Block newBlock, Block oldBlock) {
		boolean blockChanged = newBlock != oldBlock;
		boolean boxChanged = gate.minX() != cand.minX() || gate.minY() != cand.minY() || gate.minZ() != cand.minZ()
				|| gate.maxX() != cand.maxX() || gate.maxY() != cand.maxY() || gate.maxZ() != cand.maxZ();

		// take the old gate out of the world first
		if (blockChanged || boxChanged) forceOpen(level, gate);

		if (blockChanged) {
			Materials.refund(level, player, oldBlock, gate.paidCount());
			cand.setPlacedCount(0);
			cand.setPaidCount(0);
			Materials.Investment inv = Materials.invest(player, newBlock, cand.totalCount());
			cand.invest(inv.placed(), inv.paid());
		} else if (cand.placedCount() > cand.totalCount()) {
			int paidBefore = cand.paidCount();
			cand.lose(cand.placedCount() - cand.totalCount());
			Materials.refund(level, player, newBlock, paidBefore - cand.paidCount());
		} else if (cand.placedCount() < cand.totalCount() && boxChanged) {
			Materials.Investment inv = Materials.invest(player, newBlock, cand.totalCount() - cand.placedCount());
			cand.invest(inv.placed(), inv.paid());
		}

		boolean complete = cand.placedCount() == cand.totalCount();
		GateStatus before = gate.status();
		cand.setStatus(complete ? GateStatus.OK : before == GateStatus.BROKEN ? GateStatus.BROKEN : GateStatus.INCOMPLETE);
		// A new box or block is built closed, and so is an open gate that isn't complete (e.g. its
		// trigger was missing): its paid part becomes visible. Otherwise the gate stays as it was.
		boolean rebuild = blockChanged || boxChanged || (!complete && gate.isOpen());
		cand.setOpen(rebuild ? false : gate.isOpen());

		manager.update(gate, cand);
		heldBack.remove(gate.id());
		manager.refreshActive(level, gate);
		blockedNotified.remove(gate.id());
		clearMarkers(level, gate);
		if (rebuild) placeBlocksUpTo(level, gate, gate.placedCount());
		save(gate);

		Messages.send(player, "confirm.updated", gate.name(), gate.id());
		if (!complete) Messages.send(player, "gate.incomplete", gate.name(), gate.placedCount(), gate.totalCount());
		return true;
	}

	private static boolean fail(ServerPlayer player, String key, Object... args) {
		Messages.send(player, key, args);
		return false;
	}

	// ------------------------------------------------------------------ remove / repair

	public void remove(ServerPlayer remover, Gate gate) {
		ServerLevel level = level(gate);
		sessions.cancelEditsOf(gate);
		if (level != null) {
			forceOpen(level, gate);
			if (remover != null) Materials.refund(level, remover, manager.blockOf(gate), gate.paidCount());
		}
		manager.remove(gate);
		heldBack.remove(gate.id());
		locked.remove(gate.id());
		blockedNotified.remove(gate.id());
		lastVerify.remove(gate.id());
		int id = gate.id();
		GatesPlus.get().storage().run(s -> s.deleteGate(id));
	}

	public void repair(ServerPlayer player, Gate gate) {
		heldBack.remove(gate.id());
		if (gate.missingCount() == 0) {
			if (gate.status() == GateStatus.NO_TRIGGER) Messages.send(player, "repair.need_trigger", gate.name());
			else Messages.send(player, "repair.not_needed", gate.name());
			return;
		}
		Block block = manager.blockOf(gate);
		ServerLevel level = level(gate);
		if (level == null || !GateGeometry.allChunksLoaded(level, gate)) {
			Messages.send(player, "confirm.not_loaded");
			return;
		}
		// An open gate (trigger missing) has no blocks in the world: only the count is topped up.
		// A closed gate gets its holes filled for real, bottom row first.
		List<BlockPos> holes = new ArrayList<>();
		if (!gate.isOpen()) {
			for (BlockPos pos : GateGeometry.positions(gate)) if (level.getBlockState(pos).isAir()) holes.add(pos);
		}
		int wanted = gate.isOpen() ? gate.missingCount() : Math.min(gate.missingCount(), holes.size());
		if (wanted == 0) {
			Messages.send(player, "repair.obstructed", gate.name());
			return;
		}
		Materials.Investment inv = Materials.invest(player, block, wanted);
		if (inv.placed() == 0) {
			Messages.send(player, "repair.no_blocks", gate.name(), gate.placedCount(), gate.totalCount(),
					block.getName().getString());
			return;
		}
		BlockState state = block.defaultBlockState();
		for (int i = 0; i < inv.placed() && i < holes.size(); i++) level.setBlock(holes.get(i), state, Block.UPDATE_ALL);
		gate.invest(inv.placed(), inv.paid());
		lastVerify.put(gate.id(), level.getGameTime());
		if (gate.missingCount() == 0 && gate.status() == GateStatus.NO_TRIGGER) {
			Messages.send(player, "repair.need_trigger", gate.name());
		} else if (gate.missingCount() == 0) {
			gate.setStatus(GateStatus.OK);
			blockedNotified.remove(gate.id());
			Messages.send(player, "repair.done", gate.name(), gate.placedCount(), gate.totalCount());
		} else {
			Messages.send(player, "repair.partial", gate.name(), gate.placedCount(), gate.totalCount());
			if (!gate.isOpen() && wanted < gate.missingCount() && inv.placed() == wanted) {
				Messages.send(player, "repair.obstructed", gate.name());
			}
		}
		save(gate);
	}

	// ------------------------------------------------------------------ group deletion

	/** Gates of a deleted group become personal again; name clashes get a -2, -3 ... suffix. */
	public void detachGroup(Group group) {
		for (Gate g : manager.inGroup(group.id())) {
			Gate updated = g.copy();
			updated.setGroupId(null);
			String base = g.name();
			String name = base;
			for (int i = 2; manager.byName(updated.scopeKey(), name) != null; i++) name = base + "-" + i;
			updated.setName(name);
			manager.update(g, updated);
			save(g);
		}
	}
}
