package be.jorisclaes.gatesplus.gate;

import java.util.UUID;

/**
 * A gate: an axis-aligned box of identical blocks that opens/closes on a redstone trigger.
 * Pure data (no Minecraft types) so it can be stored and unit-tested easily.
 */
public final class Gate {
	private int id;
	private final UUID ownerUuid;
	private String ownerName;
	private Integer groupId;
	private String name;
	private String dimension;
	private int minX, minY, minZ, maxX, maxY, maxZ;
	private int triggerX, triggerY, triggerZ;
	private String triggerBlock;
	private String block;
	private boolean indestructible;
	private boolean fireproof;
	private boolean animationEnabled;
	private GateDirection direction;
	private int animationTicks;
	private boolean redstoneInverted;
	private boolean open;
	private GateStatus status;
	private int placedCount;
	private int paidCount;
	private final long createdAt;

	public Gate(int id, UUID ownerUuid, String ownerName, String name, long createdAt) {
		this.id = id;
		this.ownerUuid = ownerUuid;
		this.ownerName = ownerName;
		this.name = name;
		this.createdAt = createdAt;
		this.direction = GateDirection.VERTICAL;
		this.status = GateStatus.OK;
	}

	/** Copies every mutable property from {@code other} (used to apply an edit session). */
	public void copyFrom(Gate other) {
		ownerName = other.ownerName;
		groupId = other.groupId;
		name = other.name;
		dimension = other.dimension;
		setBox(other.minX, other.minY, other.minZ, other.maxX, other.maxY, other.maxZ);
		setTrigger(other.triggerX, other.triggerY, other.triggerZ, other.triggerBlock);
		block = other.block;
		indestructible = other.indestructible;
		fireproof = other.fireproof;
		animationEnabled = other.animationEnabled;
		direction = other.direction;
		animationTicks = other.animationTicks;
		redstoneInverted = other.redstoneInverted;
		open = other.open;
		status = other.status;
		placedCount = other.placedCount;
		paidCount = other.paidCount;
	}

	// ---- identity / ownership ----
	public int id() { return id; }
	public void setId(int id) { this.id = id; }
	public UUID ownerUuid() { return ownerUuid; }
	public String ownerName() { return ownerName; }
	public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
	public Integer groupId() { return groupId; }
	public void setGroupId(Integer groupId) { this.groupId = groupId; }
	public String name() { return name; }
	public void setName(String name) { this.name = name; }
	public long createdAt() { return createdAt; }

	/** Names are unique per scope: a player's personal gates, or a group. */
	public String scopeKey() {
		return scopeKey(ownerUuid, groupId);
	}

	public static String scopeKey(UUID owner, Integer groupId) {
		return groupId != null ? "g:" + groupId : "p:" + owner;
	}

	// ---- geometry ----
	public String dimension() { return dimension; }
	public void setDimension(String dimension) { this.dimension = dimension; }

	public void setBox(int x1, int y1, int z1, int x2, int y2, int z2) {
		minX = Math.min(x1, x2); maxX = Math.max(x1, x2);
		minY = Math.min(y1, y2); maxY = Math.max(y1, y2);
		minZ = Math.min(z1, z2); maxZ = Math.max(z1, z2);
	}

	public int minX() { return minX; }
	public int minY() { return minY; }
	public int minZ() { return minZ; }
	public int maxX() { return maxX; }
	public int maxY() { return maxY; }
	public int maxZ() { return maxZ; }
	public int sizeX() { return maxX - minX + 1; }
	public int sizeY() { return maxY - minY + 1; }
	public int sizeZ() { return maxZ - minZ + 1; }
	public int totalCount() { return sizeX() * sizeY() * sizeZ(); }

	public boolean contains(int x, int y, int z) {
		return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
	}

	public void setTrigger(int x, int y, int z, String triggerBlock) {
		triggerX = x; triggerY = y; triggerZ = z;
		this.triggerBlock = triggerBlock;
	}

	public int triggerX() { return triggerX; }
	public int triggerY() { return triggerY; }
	public int triggerZ() { return triggerZ; }
	public String triggerBlock() { return triggerBlock; }

	public boolean isTrigger(int x, int y, int z) {
		return x == triggerX && y == triggerY && z == triggerZ;
	}

	// ---- properties ----
	public String block() { return block; }
	public void setBlock(String block) { this.block = block; }
	public boolean indestructible() { return indestructible; }
	public void setIndestructible(boolean v) { this.indestructible = v; }
	public boolean fireproof() { return fireproof; }
	public void setFireproof(boolean v) { this.fireproof = v; }
	public boolean animationEnabled() { return animationEnabled; }
	public void setAnimationEnabled(boolean v) { this.animationEnabled = v; }
	public GateDirection direction() { return direction; }
	public void setDirection(GateDirection d) { this.direction = d; }
	public int animationTicks() { return animationTicks; }
	public void setAnimationTicks(int t) { this.animationTicks = t; }
	public boolean redstoneInverted() { return redstoneInverted; }
	public void setRedstoneInverted(boolean v) { this.redstoneInverted = v; }

	// ---- state ----
	public boolean isOpen() { return open; }
	public void setOpen(boolean open) { this.open = open; }
	public GateStatus status() { return status; }
	public void setStatus(GateStatus status) { this.status = status; }
	public int placedCount() { return placedCount; }
	public void setPlacedCount(int c) { this.placedCount = c; }
	/** Blocks actually paid for by survival players; only these are refunded. */
	public int paidCount() { return paidCount; }
	public void setPaidCount(int c) { this.paidCount = c; }

	/** Adds invested blocks, of which {@code paid} were paid for. */
	public void invest(int count, int paid) {
		placedCount = Math.min(totalCount(), placedCount + count);
		paidCount = Math.min(placedCount, paidCount + paid);
	}

	/** Removes invested blocks; the unpaid part decreases first. */
	public void lose(int count) {
		int unpaid = placedCount - paidCount;
		placedCount = Math.max(0, placedCount - count);
		int fromPaid = Math.max(0, count - unpaid);
		paidCount = Math.max(0, Math.min(placedCount, paidCount - fromPaid));
	}

	public int missingCount() { return totalCount() - placedCount; }

	public Gate copy() {
		Gate g = new Gate(id, ownerUuid, ownerName, name, createdAt);
		g.copyFrom(this);
		return g;
	}

	@Override
	public String toString() {
		return "Gate#" + id + "(" + name + ")";
	}
}
