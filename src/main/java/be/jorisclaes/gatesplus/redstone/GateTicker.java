package be.jorisclaes.gatesplus.redstone;

import java.util.ArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import be.jorisclaes.gatesplus.GatesPlus;
import be.jorisclaes.gatesplus.config.GatesPlusConfig;
import be.jorisclaes.gatesplus.gate.Gate;
import be.jorisclaes.gatesplus.gate.GateService;
import be.jorisclaes.gatesplus.gate.GateStatus;
import be.jorisclaes.gatesplus.util.Messages;

/**
 * Checks the trigger block of every gate in the active set (trigger chunk loaded and ticking).
 * Checks are spread over the ticks: a gate is checked when tick % interval == id % interval.
 */
public final class GateTicker {
	private final GateService service;

	// dev stats
	private long tickNanosSum;
	private long tickNanosMax;
	private int tickSamples;
	private int checksThisSecond;
	private int checksPerSecond;
	private double lastAvgMs;
	private double lastMaxMs;

	public GateTicker(GateService service) {
		this.service = service;
	}

	public void tick(MinecraftServer server) {
		long start = System.nanoTime();
		int tick = server.getTickCount();
		GatesPlusConfig cfg = GatesPlus.get().config();
		int interval = cfg.redstone().checkIntervalTicks();

		for (Gate gate : new ArrayList<>(service.manager().activeGates())) {
			if (Math.floorMod(gate.id(), interval) != tick % interval) continue;
			ServerLevel level = service.level(gate);
			if (level == null) continue;
			BlockPos trigger = new BlockPos(gate.triggerX(), gate.triggerY(), gate.triggerZ());
			if (!level.shouldTickBlocksAt(trigger)) continue;
			checksThisSecond++;
			check(level, gate, trigger, cfg);
		}

		long took = System.nanoTime() - start;
		tickNanosSum += took;
		tickNanosMax = Math.max(tickNanosMax, took);
		if (++tickSamples >= 100) {
			lastAvgMs = tickNanosSum / 1_000_000.0 / tickSamples;
			lastMaxMs = tickNanosMax / 1_000_000.0;
			tickNanosSum = 0;
			tickNanosMax = 0;
			tickSamples = 0;
		}
		if (tick % 20 == 0) {
			checksPerSecond = checksThisSecond;
			checksThisSecond = 0;
		}
	}

	private void check(ServerLevel level, Gate gate, BlockPos trigger, GatesPlusConfig cfg) {
		if (service.isLocked(gate) || service.animator().isAnimating(gate)) return;

		BlockState triggerState = level.getBlockState(trigger);
		if (triggerState.isAir()) {
			service.markNoTrigger(level, gate);
			return;
		}
		if (gate.status() == GateStatus.NO_TRIGGER) {
			// a block is back on the trigger position: the gate works again immediately
			service.restoreTrigger(level, gate, BuiltInRegistries.BLOCK.getKey(triggerState.getBlock()).toString());
		}
		if (!gate.status().isFunctional()) {
			// broken / incomplete: the gate stays exactly as it is and ignores redstone; holes filled
			// by hand with the right block are picked up by verify()
			service.verify(level, gate, level.getGameTime(), false);
			return;
		}
		if (!service.verify(level, gate, level.getGameTime(), false)) return;

		boolean powered = isPowered(level, trigger, triggerState);
		boolean inverted = gate.redstoneInverted() && cfg.redstone().allowInverted();
		boolean wantOpen = powered != inverted;
		if (wantOpen == gate.isOpen()) return;

		if (wantOpen) {
			if (service.verify(level, gate, level.getGameTime(), true)) service.open(level, gate);
			return;
		}
		// closing: never close on players/mobs/items, never overwrite blocks placed in the opening
		if (service.entitiesInside(level, gate)) {
			for (ServerPlayer p : level.players()) {
				if (p.blockPosition().closerThan(trigger, 16)) Messages.actionBar(p, "gate.wants_to_close", gate.name());
			}
			return;
		}
		if (service.isObstructed(level, gate)) {
			service.notifyBlocked(gate);
			return;
		}
		service.close(level, gate);
	}

	/** Powered by a neighbour (wire, repeater, lever on it) or the trigger itself is a powered source. */
	static boolean isPowered(ServerLevel level, BlockPos pos, BlockState state) {
		if (level.hasNeighborSignal(pos)) return true;
		if (state.isSignalSource()) {
			for (Direction d : Direction.values()) if (state.getSignal(level, pos, d) > 0) return true;
		}
		return false;
	}

	public double avgMs() { return lastAvgMs; }
	public double maxMs() { return lastMaxMs; }
	public int checksPerSecond() { return checksPerSecond; }
}
