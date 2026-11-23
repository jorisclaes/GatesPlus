package be.jorisclaes.gatesplus.config;

import java.util.List;

/**
 * Immutable snapshot of config.yml. A reload produces a new instance.
 */
public record GatesPlusConfig(
		String language,
		Storage storage,
		Limits limits,
		List<String> allowedBlocks,
		List<String> nonFullBlockWhitelist,
		Session session,
		Defaults defaults,
		Animation animation,
		Redstone redstone,
		Protection protection,
		Materials materials) {

	public enum StorageType { SQLITE, MYSQL }

	public record Storage(StorageType type, String sqliteFile, Mysql mysql) {}

	public record Mysql(String host, int port, String database, String user, String password, int poolSize) {}

	public record Limits(int maxGatesPerPlayer, int maxGateBlocks, int maxWidth, int maxHeight, int maxDepth,
			int maxTriggerDistance, int maxGroupsPerPlayer, int maxMembersPerGroup, int maxConcurrentAnimations) {}

	public record Session(int timeoutMinutes, boolean markerSetsCorner) {}

	public record Defaults(String block, boolean indestructible, boolean fireproof, boolean animationEnabled,
			String direction, int animationTicks) {}

	public record Animation(boolean enabled, boolean allowPlayerToggle) {}

	public record Redstone(int checkIntervalTicks, boolean allowInverted) {}

	public record Protection(boolean allowOpBreak) {}

	public record Materials(boolean requireBlocks, boolean creativeFree, boolean returnOnRemove, int chestThreshold) {}
}
