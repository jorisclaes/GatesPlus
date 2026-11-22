package be.jorisclaes.gatesplus.group;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** A player group that can share gates. Group names are globally unique (case-insensitive). */
public final class Group {
	private int id;
	private String name;
	private final UUID ownerUuid;
	private String ownerName;
	private final long createdAt;
	/** Members excluding the owner, uuid -> last known name. */
	private final Map<UUID, String> members = new LinkedHashMap<>();

	public Group(int id, String name, UUID ownerUuid, String ownerName, long createdAt) {
		this.id = id;
		this.name = name;
		this.ownerUuid = ownerUuid;
		this.ownerName = ownerName;
		this.createdAt = createdAt;
	}

	public int id() { return id; }
	public void setId(int id) { this.id = id; }
	public String name() { return name; }
	public UUID ownerUuid() { return ownerUuid; }
	public String ownerName() { return ownerName; }
	public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
	public long createdAt() { return createdAt; }
	public Map<UUID, String> members() { return Collections.unmodifiableMap(members); }

	public void putMember(UUID uuid, String name) { members.put(uuid, name); }
	public boolean removeMember(UUID uuid) { return members.remove(uuid) != null; }

	public boolean isOwner(UUID uuid) { return ownerUuid.equals(uuid); }

	/** Owner or member. */
	public boolean contains(UUID uuid) { return isOwner(uuid) || members.containsKey(uuid); }
}
