package be.jorisclaes.gatesplus.animation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.math.Transformation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.state.BlockState;

import be.jorisclaes.gatesplus.gate.Gate;
import be.jorisclaes.gatesplus.gate.GateDirection;
import be.jorisclaes.gatesplus.gate.GateGeometry;
import be.jorisclaes.gatesplus.mixin.BlockDisplayAccessor;
import be.jorisclaes.gatesplus.mixin.DisplayAccessor;

/**
 * Smooth gate animation with vanilla block-display entities. The client interpolates the
 * transformation, so the movement is smooth even though the server only sends two states.
 *
 * Opening: real blocks become air, a display per block shrinks away row by row
 * (vertical: bottom row first, retracting upwards; horizontal: left column first, retracting right).
 * Closing is the reverse; the real blocks are placed when the animation ends.
 */
public final class GateAnimator {
	public static final String DISPLAY_TAG = "gatesplus_anim";

	private static final class Animation {
		final Gate gate;
		final ServerLevel level;
		final boolean opening;
		final List<Display.BlockDisplay> displays = new ArrayList<>();
		final List<Integer> delays = new ArrayList<>();
		final long startTick;
		final long endTick;
		final int rowDuration;
		boolean transformsApplied;

		Animation(Gate gate, ServerLevel level, boolean opening, long startTick, int total, int rowDuration) {
			this.gate = gate;
			this.level = level;
			this.opening = opening;
			this.startTick = startTick;
			this.endTick = startTick + 1 + total;
			this.rowDuration = rowDuration;
		}
	}

	/** Called when a closing animation ends and the real blocks should be placed. */
	public interface CloseFinisher {
		void finishClose(ServerLevel level, Gate gate);
	}

	private final Map<Integer, Animation> running = new HashMap<>();
	/** Displays spawned by running animations; any other tagged display is a leftover and removed. */
	private final java.util.Set<java.util.UUID> ownDisplays = new java.util.HashSet<>();
	private final CloseFinisher finisher;

	public GateAnimator(CloseFinisher finisher) {
		this.finisher = finisher;
	}

	public boolean isOwnDisplay(net.minecraft.world.entity.Entity entity) {
		return ownDisplays.contains(entity.getUUID());
	}

	public boolean isAnimating(Gate gate) {
		return running.containsKey(gate.id());
	}

	public int runningCount() {
		return running.size();
	}

	public int displayCount() {
		int n = 0;
		for (Animation a : running.values()) n += a.displays.size();
		return n;
	}

	/**
	 * Starts an animation. The caller has already removed the real blocks when opening.
	 * @param state the gate block state to show
	 */
	public void start(ServerLevel level, Gate gate, BlockState state, boolean opening, long now) {
		cancel(gate);
		int rows = GateGeometry.rowCount(gate);
		int total = Math.max(2, gate.animationTicks());
		int rowDuration = Math.max(2, Math.min(total, Math.round(total * 0.5f)));
		Animation anim = new Animation(gate, level, opening, now, total, rowDuration);

		for (BlockPos pos : GateGeometry.positions(gate)) {
			Display.BlockDisplay display = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
			if (display == null) continue;
			display.setPos(pos.getX(), pos.getY(), pos.getZ());
			display.addTag(DISPLAY_TAG);
			((BlockDisplayAccessor) display).gatesplus$setBlockState(state);
			((DisplayAccessor) display).gatesplus$setViewRange(1.5f);
			((DisplayAccessor) display).gatesplus$setTransformation(opening ? full() : collapsed(gate.direction(), gate));
			ownDisplays.add(display.getUUID());
			level.addFreshEntity(display);

			int row = GateGeometry.row(gate, pos);
			// opening: first row first; closing: last row first (reverse order)
			int order = opening ? row : rows - 1 - row;
			int delay = rows <= 1 ? 0 : Math.round((float) order * (total - rowDuration) / (rows - 1));
			anim.displays.add(display);
			anim.delays.add(delay);
		}
		running.put(gate.id(), anim);
	}

	public void tick(long now) {
		Iterator<Animation> it = running.values().iterator();
		while (it.hasNext()) {
			Animation a = it.next();
			if (!a.transformsApplied && now > a.startTick) {
				// One tick after spawning, so clients have the start state before interpolating.
				Transformation target = a.opening ? collapsed(a.gate.direction(), a.gate) : full();
				for (int i = 0; i < a.displays.size(); i++) {
					DisplayAccessor d = (DisplayAccessor) a.displays.get(i);
					d.gatesplus$setInterpolationDelay(a.delays.get(i));
					d.gatesplus$setInterpolationDuration(a.rowDuration);
					d.gatesplus$setTransformation(target);
				}
				a.transformsApplied = true;
			}
			if (now >= a.endTick) {
				it.remove();
				finish(a);
			}
		}
	}

	/** Stops an animation immediately and puts the gate in its final state. */
	public void cancel(Gate gate) {
		Animation a = running.remove(gate.id());
		if (a != null) finish(a);
	}

	public void cancelAll() {
		for (Animation a : new ArrayList<>(running.values())) cancel(a.gate);
	}

	private void finish(Animation a) {
		if (!a.opening) finisher.finishClose(a.level, a.gate);
		for (Display.BlockDisplay d : a.displays) {
			ownDisplays.remove(d.getUUID());
			d.discard();
		}
	}

	private static Transformation full() {
		return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1, 1, 1), new Quaternionf());
	}

	/** A block squashed to zero thickness against its far side (top, or the "right" side). */
	private static Transformation collapsed(GateDirection dir, Gate gate) {
		if (dir == GateDirection.VERTICAL) {
			return new Transformation(new Vector3f(0, 1, 0), new Quaternionf(), new Vector3f(1, 0, 1), new Quaternionf());
		}
		boolean alongX = GateGeometry.alongX(gate);
		return alongX
				? new Transformation(new Vector3f(1, 0, 0), new Quaternionf(), new Vector3f(0, 1, 1), new Quaternionf())
				: new Transformation(new Vector3f(0, 0, 1), new Quaternionf(), new Vector3f(1, 1, 0), new Quaternionf());
	}
}
