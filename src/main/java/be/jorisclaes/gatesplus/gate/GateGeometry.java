package be.jorisclaes.gatesplus.gate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Position helpers for a gate box. */
public final class GateGeometry {
	private GateGeometry() {}

	/** All positions, bottom row first (the order blocks are built / repaired in). */
	public static List<BlockPos> positions(Gate g) {
		List<BlockPos> out = new ArrayList<>(g.totalCount());
		for (int y = g.minY(); y <= g.maxY(); y++)
			for (int x = g.minX(); x <= g.maxX(); x++)
				for (int z = g.minZ(); z <= g.maxZ(); z++) out.add(new BlockPos(x, y, z));
		return out;
	}

	/** True when the gate's "length" runs along X (used for horizontal sliding). */
	public static boolean alongX(Gate g) {
		return g.sizeX() >= g.sizeZ();
	}

	/** Number of animation rows for the gate's direction. */
	public static int rowCount(Gate g) {
		return switch (g.direction()) {
			case VERTICAL -> g.sizeY();
			case HORIZONTAL -> alongX(g) ? g.sizeX() : g.sizeZ();
		};
	}

	/** Row index of a position: 0 = bottom row (vertical) or left-most column (horizontal). */
	public static int row(Gate g, BlockPos p) {
		return switch (g.direction()) {
			case VERTICAL -> p.getY() - g.minY();
			case HORIZONTAL -> alongX(g) ? p.getX() - g.minX() : p.getZ() - g.minZ();
		};
	}

	/** Position of row {@code row} on the line through {@code linePos} (the other coordinates). */
	public static BlockPos onRow(Gate g, BlockPos linePos, int row) {
		return switch (g.direction()) {
			case VERTICAL -> new BlockPos(linePos.getX(), g.minY() + row, linePos.getZ());
			case HORIZONTAL -> alongX(g) ? new BlockPos(g.minX() + row, linePos.getY(), linePos.getZ())
					: new BlockPos(linePos.getX(), linePos.getY(), g.minZ() + row);
		};
	}

	/**
	 * Positions a closing gate leaves empty because of a block in its opening. Every line along the
	 * closing direction fills in closing order (vertical: top down) and stops at the first
	 * obstruction: that position and everything after it on the line is held back.
	 */
	public static Set<BlockPos> heldBack(Gate g, Predicate<BlockPos> obstruction) {
		Set<BlockPos> held = new HashSet<>();
		int rows = rowCount(g);
		for (BlockPos p : positions(g)) {
			if (row(g, p) != 0) continue; // one start position per line
			boolean blocked = false;
			for (int r = rows - 1; r >= 0; r--) {
				BlockPos q = onRow(g, p, r);
				if (!blocked && obstruction.test(q)) blocked = true;
				if (blocked) held.add(q);
			}
		}
		return held;
	}

	public static boolean allChunksLoaded(Level level, Gate g) {
		for (int cx = g.minX() >> 4; cx <= g.maxX() >> 4; cx++)
			for (int cz = g.minZ() >> 4; cz <= g.maxZ() >> 4; cz++)
				if (!level.hasChunk(cx, cz)) return false;
		return true;
	}

	/** Chebyshev distance from a point to the gate box (0 = inside). */
	public static int distanceToBox(Gate g, int x, int y, int z) {
		int dx = Math.max(0, Math.max(g.minX() - x, x - g.maxX()));
		int dy = Math.max(0, Math.max(g.minY() - y, y - g.maxY()));
		int dz = Math.max(0, Math.max(g.minZ() - z, z - g.maxZ()));
		return Math.max(dx, Math.max(dy, dz));
	}
}
