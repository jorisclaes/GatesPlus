package be.jorisclaes.gatesplus.gate;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

class GateGeometryTest {
	private static Gate gate(GateDirection dir) {
		Gate g = new Gate(1, UUID.randomUUID(), "o", "g", 0);
		g.setBox(0, 0, 0, 2, 3, 0); // 3 wide (x), 4 high (y), 1 deep
		g.setDirection(dir);
		return g;
	}

	@Test
	void verticalLineStopsAtBlockAndHoldsEverythingBelow() {
		Gate g = gate(GateDirection.VERTICAL);
		BlockPos obstacle = new BlockPos(1, 2, 0);
		Set<BlockPos> held = GateGeometry.heldBack(g, obstacle::equals);
		// column x=1 closes from the top: y=3 is placed, y=2 (the block) and below stay empty
		assertEquals(Set.of(new BlockPos(1, 2, 0), new BlockPos(1, 1, 0), new BlockPos(1, 0, 0)), held);
	}

	@Test
	void horizontalLineClosesFromTheRight() {
		Gate g = gate(GateDirection.HORIZONTAL);
		BlockPos obstacle = new BlockPos(1, 0, 0);
		Set<BlockPos> held = GateGeometry.heldBack(g, obstacle::equals);
		// row y=0 closes from x=2 towards x=0: x=2 placed, x=1 (the block) and x=0 held back
		assertEquals(Set.of(new BlockPos(1, 0, 0), new BlockPos(0, 0, 0)), held);
	}

	@Test
	void noObstructionHoldsNothing() {
		assertTrue(GateGeometry.heldBack(gate(GateDirection.VERTICAL), p -> false).isEmpty());
	}
}
