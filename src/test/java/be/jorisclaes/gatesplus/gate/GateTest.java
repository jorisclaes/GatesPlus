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
}
