package be.jorisclaes.gatesplus.animation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.math.Transformation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.state.BlockState;

import be.jorisclaes.gatesplus.gate.Gate;
import be.jorisclaes.gatesplus.gate.GateDirection;
import be.jorisclaes.gatesplus.gate.GateGeometry;
import be.jorisclaes.gatesplus.mixin.BlockDisplayAccessor;
import be.jorisclaes.gatesplus.mixin.DisplayAccessor;

/**
 * Smooth gate animation with vanilla block-display entities, strictly row by row.
 *
 * The server starts each row on its own tick (the client interpolates the movement of that row),
 * and a row only starts when the previous one is done:
 * - opening: real blocks become air, then row after row shrinks away
 *   (vertical: bottom row first, retracting upwards; horizontal: left column first, retracting right);
 * - closing: the reverse order (vertical: top row first, growing down), a bit slower and with the
 *   rows overlapping; each row becomes real blocks as soon as it is done.
 */
public final class GateAnimator {
	public static final String DISPLAY_TAG = "gatesplus_anim";
	/**
	 * Extra ticks a finished row keeps its display before it is swapped for the real block (closing)
	 * or removed (opening). The client starts interpolating when the update arrives, a little after
	 * the server sent it, so swapping exactly on time cuts off the end of the movement.
	 */
	private static final int SETTLE_TICKS = 2;

	/** Places the real blocks of one finished closing row. */
	public interface RowPlacer {
		void placeRow(ServerLevel level, Gate gate, List<BlockPos> positions);
	}

	private static final class Row {
		final List<Display.BlockDisplay> displays = new ArrayList<>();
		final List<BlockPos> positions = new ArrayList<>();
	}

	private static final class Animation {
		final Gate gate;
		final ServerLevel level;
		final boolean opening;
		final BlockState state;
		/** Rows in animation order. */
		final List<Row> rows = new ArrayList<>();
		final long firstRowTick;
		/** Ticks one row takes to move. */
		final int rowDuration;
		/** Ticks between the starts of two rows (smaller than the duration = rows overlap). */
		final int rowInterval;
		/** Closing only: rows get their displays just before their turn (see {@link #tick}). */
		int nextToSpawn;
		int nextToStart;
		int nextToFinish;

		Animation(Gate gate, ServerLevel level, boolean opening, BlockState state, long firstRowTick, int rowDuration,
				int rowInterval) {
			this.gate = gate;
			this.level = level;
			this.opening = opening;
			this.state = state;
			this.firstRowTick = firstRowTick;
			this.rowDuration = rowDuration;
			this.rowInterval = rowInterval;
		}

		long rowStart(int i) {
			return firstRowTick + (long) i * rowInterval;
		}
	}

	private final Map<Integer, Animation> running = new HashMap<>();
	/** Displays spawned by running animations; any other tagged display is a leftover and removed. */
	private final Set<UUID> ownDisplays = new HashSet<>();
	private final RowPlacer placer;

	public GateAnimator(RowPlacer placer) {
		this.placer = placer;
	}

	public boolean isOwnDisplay(Entity entity) {
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
		for (Animation a : running.values()) for (Row r : a.rows) n += r.displays.size();
		return n;
	}

	/** Ticks each row takes: the gate's animation time spread over its rows (min. 2). */
	public static int rowTicks(Gate gate) {
		int rows = GateGeometry.rowCount(gate);
		return Math.max(2, Math.round((float) Math.max(2, gate.animationTicks()) / rows));
	}

	/** Closing rows take 1.5x as long as opening rows. */
	public static int closingRowDuration(Gate gate) {
		return Math.max(3, Math.round(rowTicks(gate) * 1.5f));
	}

	/** The next closing row starts when the previous one is about 60% done. */
	public static int closingRowInterval(Gate gate) {
		return Math.max(1, Math.round(closingRowDuration(gate) * 0.6f));
	}

	/**
	 * Starts an animation. The caller has already removed the real blocks when opening.
	 * @param state the gate block state to show
	 */
	public void start(ServerLevel level, Gate gate, BlockState state, boolean opening, long now) {
		cancel(gate);
		int rowCount = GateGeometry.rowCount(gate);
		// first row starts one tick after spawning, so clients have the start state first
		// Opening: each row in turn (liked as it is). Closing: a bit slower and the rows overlap, so
		// the edge of the gate moves down calmly instead of jumping from row to row.
		int duration = opening ? rowTicks(gate) : closingRowDuration(gate);
		int interval = opening ? duration : closingRowInterval(gate);
		Animation anim = new Animation(gate, level, opening, state, now + 1, duration, interval);
		for (int i = 0; i < rowCount; i++) anim.rows.add(new Row());
		for (BlockPos pos : GateGeometry.positions(gate)) {
			int row = GateGeometry.row(gate, pos);
			// opening: first row first; closing: last row first (vertical: top row first, going down)
			anim.rows.get(opening ? row : rowCount - 1 - row).positions.add(pos);
		}

		if (opening) {
			// the real blocks are already gone: every row needs its (full-size) display right away
			for (Row r : anim.rows) spawnRow(anim, r, full());
			anim.nextToSpawn = rowCount;
		} else {
			// A collapsed display is still visible as a flat plane, so closing rows only appear when
			// it is their turn: the first one now, the others one tick before they start.
			spawnRow(anim, anim.rows.get(0), collapsed(gate));
			anim.nextToSpawn = 1;
		}
		running.put(gate.id(), anim);
	}

	private void spawnRow(Animation a, Row row, Transformation startState) {
		for (BlockPos pos : row.positions) {
			Display.BlockDisplay display = EntityTypes.BLOCK_DISPLAY.create(a.level, EntitySpawnReason.TRIGGERED);
			if (display == null) continue;
			display.setPos(pos.getX(), pos.getY(), pos.getZ());
			display.addTag(DISPLAY_TAG);
			((BlockDisplayAccessor) display).gatesplus$setBlockState(a.state);
			((DisplayAccessor) display).gatesplus$setViewRange(1.5f);
			((DisplayAccessor) display).gatesplus$setTransformation(startState);
			ownDisplays.add(display.getUUID());
			a.level.addFreshEntity(display);
			row.displays.add(display);
		}
	}

	public void tick(long now) {
		Iterator<Animation> it = running.values().iterator();
		while (it.hasNext()) {
			Animation a = it.next();
			while (a.nextToSpawn < a.rows.size() && now >= a.rowStart(a.nextToSpawn) - 1) {
				spawnRow(a, a.rows.get(a.nextToSpawn), collapsed(a.gate));
				a.nextToSpawn++;
			}
			while (a.nextToStart < a.nextToSpawn && now >= a.rowStart(a.nextToStart)) {
				startRow(a, a.rows.get(a.nextToStart));
				a.nextToStart++;
			}
			while (a.nextToFinish < a.nextToStart && now >= a.rowStart(a.nextToFinish) + a.rowDuration + SETTLE_TICKS) {
				finishRow(a, a.rows.get(a.nextToFinish));
				a.nextToFinish++;
			}
			if (a.nextToFinish >= a.rows.size()) it.remove();
		}
	}

	private static void startRow(Animation a, Row row) {
		Transformation target = a.opening ? collapsed(a.gate) : full();
		for (Display.BlockDisplay display : row.displays) {
			DisplayAccessor d = (DisplayAccessor) display;
			d.gatesplus$setInterpolationDelay(0);
			d.gatesplus$setInterpolationDuration(a.rowDuration);
			d.gatesplus$setTransformation(target);
		}
	}

	private void finishRow(Animation a, Row row) {
		if (!a.opening) placer.placeRow(a.level, a.gate, row.positions);
		for (Display.BlockDisplay d : row.displays) {
			ownDisplays.remove(d.getUUID());
			d.discard();
		}
		row.displays.clear();
	}

	/** Stops an animation immediately and puts the gate in its final state. */
	public void cancel(Gate gate) {
		Animation a = running.remove(gate.id());
		if (a == null) return;
		for (int i = a.nextToFinish; i < a.rows.size(); i++) finishRow(a, a.rows.get(i));
	}

	public void cancelAll() {
		for (Animation a : new ArrayList<>(running.values())) cancel(a.gate);
	}

	private static Transformation full() {
		return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1, 1, 1), new Quaternionf());
	}

	/** A block squashed to zero thickness against its far side (top, or the "right" side). */
	private static Transformation collapsed(Gate gate) {
		if (gate.direction() == GateDirection.VERTICAL) {
			return new Transformation(new Vector3f(0, 1, 0), new Quaternionf(), new Vector3f(1, 0, 1), new Quaternionf());
		}
		return GateGeometry.alongX(gate)
				? new Transformation(new Vector3f(1, 0, 0), new Quaternionf(), new Vector3f(0, 1, 1), new Quaternionf())
				: new Transformation(new Vector3f(0, 0, 1), new Quaternionf(), new Vector3f(1, 1, 0), new Quaternionf());
	}
}
