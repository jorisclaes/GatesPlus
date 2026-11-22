package be.jorisclaes.gatesplus.storage;

import java.util.UUID;

/** A vanilla chest spawned to hand back gate blocks; removed automatically once empty. */
public record ReturnChestRecord(String dimension, int x, int y, int z, UUID ownerUuid, long createdAt) {}
