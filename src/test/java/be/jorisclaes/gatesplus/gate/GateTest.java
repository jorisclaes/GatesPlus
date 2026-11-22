package be.jorisclaes.gatesplus.gate;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class GateTest {
	@Test
	void unpaidBlocksAreLostFirst() {
		Gate g = new Gate(1, UUID.randomUUID(), "a", "g", 0);
		g.setBox(0, 0, 0, 9, 9, 0); // 100 blocks
		g.invest(50, 30); // 20 unpaid
		g.lose(15);
		assertEquals(35, g.placedCount());
		assertEquals(30, g.paidCount());
		g.lose(10);
		assertEquals(25, g.placedCount());
		assertEquals(25, g.paidCount());
		assertEquals(75, g.missingCount());
	}

	@Test
	void investCapsAtTotal() {
		Gate g = new Gate(1, UUID.randomUUID(), "a", "g", 0);
		g.setBox(0, 0, 0, 1, 1, 0);
		g.invest(10, 10);
		assertEquals(4, g.placedCount());
		assertEquals(4, g.paidCount());
	}

	@Test
	void animationRowsDoNotOverlap() {
		Gate g = new Gate(1, UUID.randomUUID(), "a", "g", 0);
		g.setBox(0, 0, 0, 2, 3, 0); // 3 wide, 4 high
		g.setAnimationTicks(20);
		g.setDirection(GateDirection.VERTICAL);
		assertEquals(5, be.jorisclaes.gatesplus.animation.GateAnimator.rowTicks(g)); // 4 rows x 5 ticks
		g.setDirection(GateDirection.HORIZONTAL);
		assertEquals(7, be.jorisclaes.gatesplus.animation.GateAnimator.rowTicks(g)); // 3 columns
		g.setDirection(GateDirection.VERTICAL);
		// closing: 1.5x slower (5 -> 8 ticks per row)
		assertEquals(8, be.jorisclaes.gatesplus.animation.GateAnimator.closingRowDuration(g));
		g.setBox(0, 0, 0, 0, 15, 0); // 16 rows: never faster than 2 ticks per row
		g.setDirection(GateDirection.VERTICAL);
		assertEquals(2, be.jorisclaes.gatesplus.animation.GateAnimator.rowTicks(g));
	}
}
