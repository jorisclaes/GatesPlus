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
 * the trigger block pulsing blue with a line to the gate, and blocking positions red.
 */
final class SelectionRenderer {
	private static final int GOLD = 0xFFFFC21A;
	private static final int RED = 0xFFFF3B30;
	private static final int GREEN = 0xFF4CFF5A;
	private static final int BLUE = 0xFF3FA9FF;

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

		if (box != null && s.triggerMode()) {
			Gizmos.cuboid(box.inflate(s.triggerRange()), GizmoStyle.stroke(alpha(BLUE, 0x60), 1f));
		}
		s.trigger().ifPresent(t -> {
			double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 200.0);
			int fillAlpha = 0x30 + (int) (pulse * 0x60);
			Gizmos.cuboid(new AABB(t).inflate(0.03), GizmoStyle.strokeAndFill(BLUE, 4f, alpha(BLUE, fillAlpha))).setAlwaysOnTop();
			if (c1 != null && c2 != null) {
				AABB b = AABB.encapsulatingFullBlocks(c1, c2);
				Vec3 from = Vec3.atCenterOf(t);
				Vec3 to = new Vec3(clamp(from.x, b.minX, b.maxX), clamp(from.y, b.minY, b.maxY), clamp(from.z, b.minZ, b.maxZ));
				Gizmos.line(from, to, BLUE, 2f);
			}
		});

		for (BlockPos p : s.invalid()) {
			Gizmos.cuboid(new AABB(p).inflate(0.01), GizmoStyle.strokeAndFill(RED, 2f, alpha(RED, 0x55)));
		}
	}

	private static double clamp(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}
}
