package be.jorisclaes.gatesplus.session;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

class CreateSessionTest {
	private static CreateSession session() {
		return new CreateSession(CreateSession.Type.CREATE, UUID.randomUUID(), 0);
	}

	@Test
	void undoRemovesTriggerThenCorner2ThenCorner1() {
		CreateSession s = session();
		assertEquals(CreateSession.SetResult.CORNER1, s.clickCorner(new BlockPos(0, 5, 0)));
		assertEquals(CreateSession.SetResult.CORNER2, s.clickCorner(new BlockPos(3, 2, 0)));
		assertEquals(16, s.volume());
		assertTrue(s.enterTriggerMode());
		assertEquals(CreateSession.SetResult.TRIGGER, s.clickTrigger(new BlockPos(5, 2, 1), "minecraft:stone"));
		assertEquals(CreateSession.SetResult.TRIGGER_REPLACED, s.clickTrigger(new BlockPos(5, 3, 1), "minecraft:stone"));

		assertEquals(CreateSession.Point.TRIGGER, s.undo());
		assertNull(s.trigger());
		assertEquals(CreateSession.Mode.TRIGGER, s.mode());
		assertEquals(CreateSession.Point.CORNER2, s.undo());
		assertEquals(CreateSession.Mode.CORNERS, s.mode());
		assertEquals(CreateSession.Point.CORNER1, s.undo());
		assertNull(s.undo());
		assertNull(s.dimension());
	}

	@Test
	void thirdCornerClickReplacesCorner2() {
		CreateSession s = session();
		s.clickCorner(new BlockPos(0, 0, 0));
		s.clickCorner(new BlockPos(1, 1, 0));
		assertEquals(CreateSession.SetResult.CORNER2_REPLACED, s.clickCorner(new BlockPos(2, 2, 0)));
		assertEquals(new BlockPos(2, 2, 0), s.corner2());
		assertEquals(CreateSession.Point.CORNER2, s.undo());
		assertEquals(new BlockPos(0, 0, 0), s.corner1());
	}

	@Test
	void clearCornerRemovesThatCornerOnly() {
		CreateSession s = session();
		s.setCorner(CreateSession.Point.CORNER1, new BlockPos(0, 3, 0));
		s.setCorner(CreateSession.Point.CORNER2, new BlockPos(2, 0, 0));
		assertTrue(s.clearCorner(CreateSession.Point.CORNER1));
		assertNull(s.corner1());
		assertEquals(new BlockPos(2, 0, 0), s.corner2());
		assertFalse(s.clearCorner(CreateSession.Point.CORNER1));
		// the undo stack no longer holds corner 1
		assertEquals(CreateSession.Point.CORNER2, s.undo());
		assertNull(s.undo());
	}

	@Test
	void undoingCorner2AlsoDropsTrigger() {
		CreateSession s = session();
		s.clickCorner(new BlockPos(0, 0, 0));
		s.clickCorner(new BlockPos(1, 1, 0));
		s.enterTriggerMode();
		s.clickTrigger(new BlockPos(3, 0, 0), "minecraft:stone");
		s.undo(); // trigger
		s.clickTrigger(new BlockPos(3, 0, 0), "minecraft:stone");
		assertEquals(CreateSession.Point.TRIGGER, s.undo());
		assertEquals(CreateSession.Point.CORNER2, s.undo());
		assertFalse(s.enterTriggerMode());
	}

	@Test
	void editPrefillUndoOrder() {
		CreateSession s = new CreateSession(CreateSession.Type.EDIT, UUID.randomUUID(), 7);
		s.prefill("minecraft:overworld", new BlockPos(0, 3, 0), new BlockPos(2, 0, 0), new BlockPos(4, 0, 0), "minecraft:stone");
		assertEquals(CreateSession.Point.TRIGGER, s.undo());
		assertEquals(CreateSession.Point.CORNER2, s.undo());
		assertEquals(CreateSession.Point.CORNER1, s.undo());
	}

	@Test
	void markersSetTheirOwnCornerInAnyOrder() {
		CreateSession s = session();
		assertEquals(CreateSession.SetResult.CORNER2, s.setCorner(CreateSession.Point.CORNER2, new BlockPos(3, 0, 0)));
		assertNull(s.corner1());
		assertFalse(s.hasBox());
		assertEquals(CreateSession.SetResult.CORNER1, s.setCorner(CreateSession.Point.CORNER1, new BlockPos(0, 3, 0)));
		assertTrue(s.hasBox());
		assertEquals(CreateSession.SetResult.CORNER2_REPLACED, s.setCorner(CreateSession.Point.CORNER2, new BlockPos(4, 0, 0)));
		s.enterTriggerMode();
		s.clickTrigger(new BlockPos(6, 0, 0), "minecraft:stone");
		assertEquals(CreateSession.Point.TRIGGER, s.undo());
		// corner 1 was set last, so it is undone first; the box is then incomplete
		assertEquals(CreateSession.Point.CORNER1, s.undo());
		assertEquals(CreateSession.Mode.CORNERS, s.mode());
		assertEquals(new BlockPos(4, 0, 0), s.corner2());
	}
}
