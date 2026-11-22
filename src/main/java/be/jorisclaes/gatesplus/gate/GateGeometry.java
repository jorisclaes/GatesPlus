package be.jorisclaes.gatesplus.gate;

import java.util.ArrayList;
import java.util.List;

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
