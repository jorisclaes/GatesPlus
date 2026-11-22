package be.jorisclaes.gatesplus.storage;

import java.util.List;
import java.util.UUID;

import be.jorisclaes.gatesplus.gate.Gate;
import be.jorisclaes.gatesplus.group.Group;

/**
 * Blocking storage API. Never call from the server thread; use {@link AsyncStorage}.
 */
public interface GateStorage extends AutoCloseable {
	/** Opens connections and migrates the schema. */
	void init() throws StorageException;

	String describe();

	List<Gate> loadGates() throws StorageException;

	/** Inserts the gate and returns the generated, globally unique id. */
	int insertGate(Gate gate) throws StorageException;

	void updateGate(Gate gate) throws StorageException;

	void deleteGate(int id) throws StorageException;

	List<Group> loadGroups() throws StorageException;

	int insertGroup(Group group) throws StorageException;

	/** Deletes the group and its members; gates of the group become personal again (handled by caller). */
	void deleteGroup(int id) throws StorageException;

	void addMember(int groupId, UUID player, String name) throws StorageException;

	void removeMember(int groupId, UUID player) throws StorageException;

	List<ReturnChestRecord> loadReturnChests() throws StorageException;

	void insertReturnChest(ReturnChestRecord chest) throws StorageException;

	void deleteReturnChest(String dimension, int x, int y, int z) throws StorageException;

	@Override
	void close();
}
