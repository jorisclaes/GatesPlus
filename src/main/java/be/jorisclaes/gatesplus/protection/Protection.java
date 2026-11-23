package be.jorisclaes.gatesplus.protection;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import be.jorisclaes.gatesplus.marker.MarkerBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import be.jorisclaes.gatesplus.GatesPlus;
import be.jorisclaes.gatesplus.gate.Gate;
import be.jorisclaes.gatesplus.gate.GateService;
import be.jorisclaes.gatesplus.util.Messages;
import be.jorisclaes.gatesplus.util.Perms;
import be.jorisclaes.gatesplus.wand.Wand;

/**
 * Indestructible / fireproof gates and damage detection. The static methods are called from mixins
 * and must stay cheap: they are one hash lookup when no gate is nearby.
 */
public final class Protection {
	private Protection() {}

	private static GateService gates() {
		GatesPlus mod = GatesPlus.get();
		return mod == null ? null : mod.gates();
	}

	public static void register() {
		// markers break with one click (like the old instabreak blocks) and drop nothing; doing it on
		// the attack also keeps the client mod (which shows them as instabreak blocks) in sync
		net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return net.minecraft.world.InteractionResult.PASS;
			if (Wand.isWand(sp.getMainHandItem()) || sp.isSpectator()) return net.minecraft.world.InteractionResult.PASS;
			GateService s = gates();
			if (s == null || s.sessions().markerAt(level, pos) == null) return net.minecraft.world.InteractionResult.PASS;
			level.removeBlock(pos, false);
			s.sessions().onMarkerBroken(sl, pos);
			return net.minecraft.world.InteractionResult.SUCCESS;
		});

		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel)) return true;
			if (Wand.isWand(sp.getMainHandItem())) return false;
			GateService s = gates();
			// a marker: removed without drops, its corner leaves the selection
			if (s != null && s.sessions().markerAt(level, pos) != null) {
				level.removeBlock(pos, false);
				s.sessions().onMarkerBroken((ServerLevel) level, pos);
				return false;
			}
			Gate gate = s == null ? null : s.manager().gateBlockAt(level, pos, state);
			if (gate == null || !gate.indestructible()) return true;
			boolean adminBreak = sp.isCreative() && GatesPlus.get().config().protection().allowOpBreak()
					&& Perms.ADMIN_BREAK.check(sp);
			if (adminBreak) return true;
			// only shown to players with the dev panel on (/gp devstats), otherwise breaking just fails
			if (GatesPlus.get().devStats().isOn(sp)) Messages.actionBar(sp, "protect.indestructible", gate.name());
			return false;
		});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (!(level instanceof ServerLevel sl)) return;
			GateService s = gates();
			if (s == null) return;
			if (MarkerBlocks.isCustomMarker(state)) return; // untracked leftover, drops nothing
			Gate gate = s.manager().gateAt(level, pos);
			if (gate != null && !gate.isOpen() && state.is(s.manager().blockOf(gate))) {
				s.markBroken(sl, gate, 1);
			}
			Gate triggerOf = s.manager().gateWithTrigger(level, pos);
			if (triggerOf != null && level.getBlockState(pos).isAir()) s.markNoTrigger(sl, triggerOf);
		});
	}

	/** Gate whose actual block sits at pos (closed gate, matching block). */
	private static Gate gateBlock(Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel)) return null;
		GateService s = gates();
		if (s == null) return null;
		Gate g = s.manager().gateAt(level, pos);
		if (g == null || g.isOpen()) return null;
		BlockState state = level.getBlockState(pos);
		return state.is(s.manager().blockOf(g)) ? g : null;
	}

	/** Explosions: drop protected positions, re-check damaged unprotected gates right after. */
	public static List<BlockPos> filterExplosion(ServerLevel level, List<BlockPos> positions) {
		GateService s = gates();
		if (s == null || positions.isEmpty()) return positions;
		Set<Gate> damaged = new HashSet<>();
		List<BlockPos> out = new java.util.ArrayList<>(positions.size());
		for (BlockPos pos : positions) {
			if (s.sessions().markerAt(level, pos) != null) continue; // markers stay
			Gate g = gateBlock(level, pos);
			if (g == null) {
				out.add(pos);
				if (s.manager().gateWithTrigger(level, pos) != null) damaged.add(s.manager().gateWithTrigger(level, pos));
			} else if (!g.indestructible()) {
				out.add(pos);
				damaged.add(g);
			}
		}
		if (!damaged.isEmpty()) scheduleVerify(level, damaged);
		return out;
	}

	/** Fire must not burn a fireproof gate block. Unprotected gates are re-checked afterwards. */
	public static boolean preventBurn(Level level, BlockPos pos) {
		Gate g = gateBlock(level, pos);
		if (g == null) return false;
		if (g.fireproof()) return true;
		scheduleVerify((ServerLevel) level, Set.of(g));
		return false;
	}

	/** True when a block of a closed, fireproof gate touches {@code pos} (6 directions). */
	public static boolean nextToFireproofGate(net.minecraft.world.level.LevelReader reader, BlockPos pos) {
		if (!(reader instanceof ServerLevel level)) return false;
		for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
			Gate g = gateBlock(level, pos.relative(d));
			if (g != null && g.fireproof()) return true;
		}
		return false;
	}

	/** Pistons must not move indestructible gate blocks. */
	public static boolean preventPush(Level level, BlockPos pos) {
		GateService s = gates();
		if (s != null && level instanceof ServerLevel sl && s.sessions().markerAt(level, pos) != null) {
			resyncLater(sl, pos);
			return true;
		}
		Gate g = gateBlock(level, pos);
		if (g == null) return false;
		if (g.indestructible()) {
			resyncLater((ServerLevel) level, pos);
			return true;
		}
		scheduleVerify((ServerLevel) level, Set.of(g));
		return false;
	}

	/** Falling blocks (sand, gravel) that are part of a gate stay in place. */
	public static boolean lockFalling(Level level, BlockPos pos) {
		return gateBlock(level, pos) != null;
	}

	/**
	 * A sticky piston retracts on the client too (block event), and the client does not know the
	 * block is protected: it pulls it along. A few ticks later the real blocks around it are sent
	 * again, so the client shows what the server has.
	 */
	private static final int RESYNC_DELAY_TICKS = 4;
	private static final java.util.Map<ServerLevel, java.util.Map<BlockPos, Long>> RESYNC = new java.util.HashMap<>();

	private static void resyncLater(ServerLevel level, BlockPos pos) {
		RESYNC.computeIfAbsent(level, l -> new java.util.HashMap<>())
				.putIfAbsent(pos.immutable(), level.getGameTime() + RESYNC_DELAY_TICKS);
	}

	/** Every tick: sends due positions (and their neighbours, where a pulled block would land). */
	public static void tick(net.minecraft.server.MinecraftServer server) {
		if (RESYNC.isEmpty()) return;
		var levels = RESYNC.entrySet().iterator();
		while (levels.hasNext()) {
			var e = levels.next();
			ServerLevel level = e.getKey();
			long now = level.getGameTime();
			var it = e.getValue().entrySet().iterator();
			while (it.hasNext()) {
				var p = it.next();
				if (p.getValue() > now) continue;
				it.remove();
				BlockPos pos = p.getKey();
				if (!level.isLoaded(pos)) continue;
				level.getChunkSource().blockChanged(pos);
				for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
					BlockPos n = pos.relative(d);
					if (level.isLoaded(n)) level.getChunkSource().blockChanged(n);
				}
			}
			if (e.getValue().isEmpty()) levels.remove();
		}
	}

	private static void scheduleVerify(ServerLevel level, Set<Gate> gates) {
		level.getServer().execute(() -> {
			GateService s = gates();
			if (s == null) return;
			for (Gate g : gates) {
				if (s.manager().byId(g.id()) != g) continue;
				s.verify(level, g, level.getGameTime(), true);
				if (level.getBlockState(new BlockPos(g.triggerX(), g.triggerY(), g.triggerZ())).isAir()) {
					s.markNoTrigger(level, g);
				}
			}
		});
	}
}
