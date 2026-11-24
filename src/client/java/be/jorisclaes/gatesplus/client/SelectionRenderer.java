package be.jorisclaes.gatesplus.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import be.jorisclaes.gatesplus.network.SelectionSyncPayload;

/**
 * Draws the wand selection with vanilla gizmos:
 * corner 1 gold, corner 2 red (both see-through so they stay visible), the box edges green,
 * the trigger block bright cyan with a pulsing white outline and a line to the gate, and blocking positions red.
 */
final class SelectionRenderer {
	private static final int GOLD = 0xFFFFC21A;
	private static final int RED = 0xFFFF3B30;
	private static final int GREEN = 0xFF4CFF5A;
	private static final int BLUE = 0xFF3FA9FF;
	private static final int TRIGGER = 0xFF00E5FF;
	private static final int WHITE = 0xFFFFFFFF;

	private SelectionRenderer() {}

	static void render(LevelRenderContext ctx) {
		SelectionSyncPayload s = ClientState.selection;
		if (!s.active()) return;
		try (var ignored = ctx.levelRenderer().collectPerFrameRenderThreadGizmos()) {
			draw(s);
		}
	}

	private static int alpha(int argb, int alpha) {
		return (alpha << 24) | (argb & 0x00FFFFFF);
	}

	private static void draw(SelectionSyncPayload s) {
		BlockPos c1 = s.corner1().orElse(null);
		BlockPos c2 = s.corner2().orElse(null);
		AABB box = null;
		if (c1 != null && c2 != null) {
			box = AABB.encapsulatingFullBlocks(c1, c2);
			Gizmos.cuboid(box.inflate(0.005), GizmoStyle.strokeAndFill(GREEN, 2.5f, alpha(GREEN, 0x22)));
		}
		if (c1 != null) {
			Gizmos.cuboid(new AABB(c1).inflate(0.02), GizmoStyle.strokeAndFill(GOLD, 4f, alpha(GOLD, 0x70))).setAlwaysOnTop();
		}
		if (c2 != null) {
			Gizmos.cuboid(new AABB(c2).inflate(0.02), GizmoStyle.strokeAndFill(RED, 4f, alpha(RED, 0x70))).setAlwaysOnTop();
		}

		// the area the trigger may be in: only after /gp set trigger (trigger mode)
		if (box != null && s.triggerMode()) {
			Gizmos.cuboid(box.inflate(s.triggerRange()), GizmoStyle.stroke(TRIGGER, 3f)).setAlwaysOnTop();
			drawOutsideRange(box.inflate(s.triggerRange()));
		}
		// real blocks just outside the range (they can't be the trigger): red like blocks in the way
		for (BlockPos p : s.outOfRange()) {
			Gizmos.cuboid(new AABB(p).inflate(0.01), GizmoStyle.strokeAndFill(RED, 2f, alpha(RED, 0x55)));
		}
		s.trigger().ifPresent(t -> {
			double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 200.0);
			int fillAlpha = 0x50 + (int) (pulse * 0x60);
			// thick bright outline + fill, always on top so it shows through walls
			Gizmos.cuboid(new AABB(t).inflate(0.03), GizmoStyle.strokeAndFill(TRIGGER, 7f, alpha(TRIGGER, fillAlpha))).setAlwaysOnTop();
			// a second, white outline that pulses outward, so the trigger stands out from the other markers
			double grow = 0.08 + pulse * 0.22;
			Gizmos.cuboid(new AABB(t).inflate(grow), GizmoStyle.stroke(alpha(WHITE, 0x80 + (int) ((1 - pulse) * 0x7F)), 3f)).setAlwaysOnTop();
			if (c1 != null && c2 != null) {
				AABB b = AABB.encapsulatingFullBlocks(c1, c2);
				Vec3 from = Vec3.atCenterOf(t);
				Vec3 to = new Vec3(clamp(from.x, b.minX, b.maxX), clamp(from.y, b.minY, b.maxY), clamp(from.z, b.minZ, b.maxZ));
				Gizmos.line(from, to, TRIGGER, 3f);
			}
		});

		for (BlockPos p : s.invalid()) {
			Gizmos.cuboid(new AABB(p).inflate(0.01), GizmoStyle.strokeAndFill(RED, 2f, alpha(RED, 0x55)));
		}
	}

	/**
	 * The air one block outside the trigger range turns red: six 1-block slabs around the range
	 * box, slightly inset so they hide inside solid blocks and never z-fight with block faces.
	 */
	private static void drawOutsideRange(AABB r) {
		double in = 0.02;
		AABB o = r.inflate(1);
		AABB[] slabs = {
				new AABB(o.minX, o.minY, o.minZ, o.maxX, r.minY, o.maxZ), new AABB(o.minX, r.maxY, o.minZ, o.maxX, o.maxY, o.maxZ),
				new AABB(o.minX, r.minY, o.minZ, r.minX, r.maxY, o.maxZ), new AABB(r.maxX, r.minY, o.minZ, o.maxX, r.maxY, o.maxZ),
				new AABB(r.minX, r.minY, o.minZ, r.maxX, r.maxY, r.minZ), new AABB(r.minX, r.minY, r.maxZ, r.maxX, r.maxY, o.maxZ) };
		for (AABB slab : slabs) {
			Gizmos.cuboid(slab.deflate(in), GizmoStyle.fill(alpha(RED, 0x30)));
		}
	}

	private static double clamp(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}
}
