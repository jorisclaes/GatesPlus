package be.jorisclaes.gatesplus.session;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

import net.minecraft.core.BlockPos;

import be.jorisclaes.gatesplus.gate.GateDirection;

/**
 * A create or edit session: the selection made with the wand plus the chosen properties.
 * Right-click undoes the last point: trigger, then corner 2, then corner 1.
 */
public final class CreateSession {
	public enum Type { CREATE, EDIT }

	public enum Mode { CORNERS, TRIGGER }

	public enum Point { CORNER1, CORNER2, TRIGGER }

	public enum SetResult { CORNER1, CORNER2, CORNER2_REPLACED, TRIGGER, TRIGGER_REPLACED }

	private final Type type;
	private final UUID player;
	/** Gate being edited, or 0 for create. */
	private final int gateId;
	private String dimension;
	private BlockPos corner1;
	private BlockPos corner2;
	private BlockPos trigger;
	private String triggerBlock;
	private Mode mode = Mode.CORNERS;
	private final Deque<Point> history = new ArrayDeque<>();
	private long lastActivityMillis = System.currentTimeMillis();

	// properties (copied into the gate on confirm)
	public String block;
	public boolean indestructible;
	public boolean fireproof;
	public boolean animationEnabled;
	public GateDirection direction;
	public int animationTicks;
	public boolean redstoneInverted;
	public Integer groupId;

	public CreateSession(Type type, UUID player, int gateId) {
		this.type = type;
		this.player = player;
		this.gateId = gateId;
	}

	public Type type() { return type; }
	public UUID player() { return player; }
	public int gateId() { return gateId; }
	public String dimension() { return dimension; }
	public BlockPos corner1() { return corner1; }
	public BlockPos corner2() { return corner2; }
	public BlockPos trigger() { return trigger; }
	public String triggerBlock() { return triggerBlock; }
	public Mode mode() { return mode; }
	public boolean hasBox() { return corner1 != null && corner2 != null; }
	public long lastActivityMillis() { return lastActivityMillis; }

	private long lastClickMillis;
	private int lastRightClickTick = Integer.MIN_VALUE;

	/**
	 * A vanilla client right-clicking a block with the axe sends two packets (use on block, then use
	 * item) in the same tick; only the first one may undo a point.
	 */
	public boolean acceptRightClick(int serverTick) {
		if (serverTick == lastRightClickTick) return false;
		lastRightClickTick = serverTick;
		return true;
	}

	/**
	 * A marker block placed during this session, removed again when the session ends.
	 * @param replaced the gate block the marker temporarily replaced (edit session), or null (air)
	 * @param label    floating "1"/"2" above a vanilla marker, or null
	 * @param fromWand placed by a wand click (no marker item was used, so none is given back)
	 */
	public record PlacedMarker(Point corner, net.minecraft.server.level.ServerLevel level, BlockPos pos,
			net.minecraft.world.level.block.state.BlockState replaced, java.util.UUID label, boolean fromWand) {}

	/** At most one placed marker per corner: placing it again moves it. */
	private final java.util.Map<Point, PlacedMarker> placedMarkers = new java.util.EnumMap<>(Point.class);

	/**
	 * Remembers a newly placed marker for {@code corner}.
	 * @return the previously placed marker of that corner (to be removed from the world), or null
	 */
	public PlacedMarker putPlacedMarker(Point corner, net.minecraft.server.level.ServerLevel level, BlockPos pos,
			net.minecraft.world.level.block.state.BlockState replaced, java.util.UUID label, boolean fromWand) {
		return placedMarkers.put(corner, new PlacedMarker(corner, level, pos, replaced, label, fromWand));
	}

	/** The placed marker of this corner, or null. */
	public PlacedMarker placedMarker(Point corner) {
		return placedMarkers.get(corner);
	}

	/** Forgets the placed marker of this corner, returning it, or null. */
	public PlacedMarker removePlacedMarker(Point corner) {
		return placedMarkers.remove(corner);
	}

	/** Forgets the placed marker at this position (it was broken), returning it, or null. */
	public PlacedMarker removePlacedMarkerAt(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
		var it = placedMarkers.values().iterator();
		while (it.hasNext()) {
			PlacedMarker m = it.next();
			if (m.level() == level && m.pos().equals(pos)) {
				it.remove();
				return m;
			}
		}
		return null;
	}

	/** The placed marker at this position, or null. */
	public PlacedMarker placedMarkerAt(net.minecraft.world.level.Level level, BlockPos pos) {
		for (PlacedMarker m : placedMarkers.values()) {
			if (m.level() == level && m.pos().equals(pos)) return m;
		}
		return null;
	}

	public java.util.Collection<PlacedMarker> placedMarkers() {
		return placedMarkers.values();
	}

	/**
	 * Creative clients repeat attack packets while the button is held; ignore clicks that follow
	 * each other within 200 ms so one click sets one point.
	 */
	public boolean debounceClick() {
		long now = System.currentTimeMillis();
		boolean accept = now - lastClickMillis >= 200;
		if (accept) lastClickMillis = now;
		return accept;
	}
	public void touch() { lastActivityMillis = System.currentTimeMillis(); }

	/** Pre-fills an edit session with the gate's current selection (undo order: trigger, corner2, corner1). */
	public void prefill(String dimension, BlockPos c1, BlockPos c2, BlockPos trigger, String triggerBlock) {
		this.dimension = dimension;
		corner1 = c1;
		corner2 = c2;
		history.clear();
		history.push(Point.CORNER1);
		history.push(Point.CORNER2);
		if (trigger != null) {
			this.trigger = trigger;
			this.triggerBlock = triggerBlock;
			history.push(Point.TRIGGER);
		}
	}

	/** Switches the wand to trigger mode; only possible once both corners are set. */
	public boolean enterTriggerMode() {
		if (!hasBox()) return false;
		mode = Mode.TRIGGER;
		return true;
	}

	public void setDimension(String dimension) {
		this.dimension = dimension;
	}

	/** Left-click in corner mode. */
	public SetResult clickCorner(BlockPos pos) {
		touch();
		if (corner1 == null) {
			corner1 = pos;
			history.push(Point.CORNER1);
			return SetResult.CORNER1;
		}
		if (corner2 == null) {
			corner2 = pos;
			history.push(Point.CORNER2);
			return SetResult.CORNER2;
		}
		corner2 = pos;
		return SetResult.CORNER2_REPLACED;
	}

	/**
	 * Sets a specific corner (clicking a corner marker block): the top-left marker always sets
	 * corner 1, the bottom-right marker corner 2, in whatever order they are clicked.
	 */
	public SetResult setCorner(Point which, BlockPos pos) {
		touch();
		if (which == Point.CORNER1) {
			boolean replaced = corner1 != null;
			corner1 = pos;
			if (!replaced) history.push(Point.CORNER1);
			return SetResult.CORNER1;
		}
		boolean replaced = corner2 != null;
		corner2 = pos;
		if (!replaced) history.push(Point.CORNER2);
		return replaced ? SetResult.CORNER2_REPLACED : SetResult.CORNER2;
	}

	/** Left-click in trigger mode (position already validated by the caller). */
	public SetResult clickTrigger(BlockPos pos, String blockId) {
		touch();
		boolean replaced = trigger != null;
		trigger = pos;
		triggerBlock = blockId;
		if (!replaced) history.push(Point.TRIGGER);
		return replaced ? SetResult.TRIGGER_REPLACED : SetResult.TRIGGER;
	}

	/** Right-click: removes the last set point. Returns null when nothing is left. */
	public Point undo() {
		touch();
		Point p = history.poll();
		if (p == null) return null;
		clear(p);
		return p;
	}

	/**
	 * Removes one specific corner (its marker block was broken), wherever it is in the undo order.
	 * @return false when that corner was not set
	 */
	public boolean clearCorner(Point which) {
		touch();
		if (which == Point.TRIGGER || (which == Point.CORNER1 ? corner1 : corner2) == null) return false;
		history.remove(which);
		clear(which);
		return true;
	}

	private void clear(Point p) {
		switch (p) {
			case TRIGGER -> {
				trigger = null;
				triggerBlock = null;
			}
			case CORNER2 -> corner2 = null;
			case CORNER1 -> corner1 = null;
		}
		if (p != Point.TRIGGER) {
			mode = Mode.CORNERS;
			// a trigger without a complete box makes no sense; drop it as well
			if (trigger != null) {
				history.remove(Point.TRIGGER);
				trigger = null;
				triggerBlock = null;
			}
		}
		if (corner1 == null && corner2 == null && trigger == null) dimension = null;
	}

	public int sizeX() { return Math.abs(corner1.getX() - corner2.getX()) + 1; }
	public int sizeY() { return Math.abs(corner1.getY() - corner2.getY()) + 1; }
	public int sizeZ() { return Math.abs(corner1.getZ() - corner2.getZ()) + 1; }
	public int volume() { return hasBox() ? sizeX() * sizeY() * sizeZ() : 0; }
}
