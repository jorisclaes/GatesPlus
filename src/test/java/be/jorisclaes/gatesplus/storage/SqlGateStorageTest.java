package be.jorisclaes.gatesplus.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import be.jorisclaes.gatesplus.gate.Gate;
import be.jorisclaes.gatesplus.gate.GateDirection;
import be.jorisclaes.gatesplus.gate.GateStatus;
import be.jorisclaes.gatesplus.group.Group;

class SqlGateStorageTest {
	private static Gate sampleGate(UUID owner, String name) {
		Gate g = new Gate(0, owner, "Joris", name, 1234L);
		g.setDimension("minecraft:overworld");
		g.setBox(3, 70, 5, 0, 64, 5);
		g.setTrigger(5, 64, 6, "minecraft:stone");
		g.setBlock("minecraft:stone");
		g.setIndestructible(true);
		g.setFireproof(false);
		g.setAnimationEnabled(true);
		g.setDirection(GateDirection.HORIZONTAL);
		g.setAnimationTicks(20);
		g.setStatus(GateStatus.INCOMPLETE);
		g.invest(10, 4);
		return g;
	}

	@Test
	void gateRoundTrip(@TempDir Path dir) throws Exception {
		UUID owner = UUID.randomUUID();
		try (SqlGateStorage s = SqlGateStorage.sqlite(dir.resolve("t.db"))) {
			s.init();
			int id = s.insertGate(sampleGate(owner, "deur1"));
			assertTrue(id > 0);
			Gate loaded = s.loadGates().getFirst();
			assertEquals(id, loaded.id());
			assertEquals(0, loaded.minX());
			assertEquals(70, loaded.maxY());
			assertEquals(28, loaded.totalCount());
			assertEquals(10, loaded.placedCount());
			assertEquals(4, loaded.paidCount());
			assertEquals(GateDirection.HORIZONTAL, loaded.direction());
			assertEquals(GateStatus.INCOMPLETE, loaded.status());
			assertNull(loaded.groupId());

			loaded.setOpen(true);
			loaded.setGroupId(7);
			s.updateGate(loaded);
			Gate again = s.loadGates().getFirst();
			assertTrue(again.isOpen());
			assertEquals(7, again.groupId());

			s.deleteGate(id);
			assertTrue(s.loadGates().isEmpty());
		}
	}

	@Test
	void nameUniquePerScope(@TempDir Path dir) throws Exception {
		UUID a = UUID.randomUUID();
		try (SqlGateStorage s = SqlGateStorage.sqlite(dir.resolve("t.db"))) {
			s.init();
			s.insertGate(sampleGate(a, "deur"));
			assertThrows(StorageException.class, () -> s.insertGate(sampleGate(a, "deur")));
			Gate inGroup = sampleGate(a, "deur");
			inGroup.setGroupId(1);
			s.insertGate(inGroup);
			s.insertGate(sampleGate(UUID.randomUUID(), "deur"));
			assertEquals(3, s.loadGates().size());
		}
	}

	@Test
	void groupsAndMembers(@TempDir Path dir) throws Exception {
		UUID owner = UUID.randomUUID();
		UUID member = UUID.randomUUID();
		try (SqlGateStorage s = SqlGateStorage.sqlite(dir.resolve("t.db"))) {
			s.init();
			int id = s.insertGroup(new Group(0, "kasteel", owner, "Joris", 1L));
			s.addMember(id, member, "Bob");
			Group g = s.loadGroups().getFirst();
			assertEquals("kasteel", g.name());
			assertTrue(g.contains(member));
			s.removeMember(id, member);
			assertFalse(s.loadGroups().getFirst().contains(member));
			s.deleteGroup(id);
			assertTrue(s.loadGroups().isEmpty());
		}
	}

	@Test
	void pendingReturnsAreStoredUpdatedAndDeleted(@TempDir Path dir) throws Exception {
		UUID owner = UUID.randomUUID();
		try (SqlGateStorage s = SqlGateStorage.sqlite(dir.resolve("p.db"))) {
			s.init();
			s.setPendingReturn(owner, "minecraft:stone", 500);
			s.setPendingReturn(owner, "minecraft:dirt", 64);
			s.setPendingReturn(owner, "minecraft:stone", 200);
			s.setPendingReturn(owner, "minecraft:dirt", 0);
			var pending = s.loadPendingReturns();
			assertEquals(java.util.Map.of("minecraft:stone", 200), pending.get(owner));
		}
	}

	@Test
	void migrationIsIdempotentAndRejectsNewerSchema(@TempDir Path dir) throws Exception {
		Path db = dir.resolve("t.db");
		try (SqlGateStorage s = SqlGateStorage.sqlite(db)) {
			s.init();
			s.insertReturnChest(new ReturnChestRecord("minecraft:overworld", 1, 2, 3, UUID.randomUUID(), 5L));
		}
		try (SqlGateStorage s = SqlGateStorage.sqlite(db)) {
			s.init();
			List<ReturnChestRecord> chests = s.loadReturnChests();
			assertEquals(1, chests.size());
		}
		try (var c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + db);
				var st = c.createStatement()) {
			st.executeUpdate("UPDATE gp_schema_version SET version = 999");
		}
		try (SqlGateStorage s = SqlGateStorage.sqlite(db)) {
			StorageException e = assertThrows(StorageException.class, s::init);
			assertTrue(e.getMessage().contains("newer"), e.getMessage());
		}
	}
}
