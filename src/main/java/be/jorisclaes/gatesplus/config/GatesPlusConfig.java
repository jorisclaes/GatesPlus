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

	/**
	 * @param customMarkerBlocks own marker blocks (every player needs the client mod); false = vanilla
	 *                           blocks with a floating label, so vanilla clients can join. Needs a restart.
	 */
	public record Session(int timeoutMinutes, boolean markerSetsCorner, boolean customMarkerBlocks) {}

	public record Defaults(String block, boolean indestructible, boolean fireproof, boolean animationEnabled,
			String direction, int animationTicks) {}

	public record Animation(boolean enabled, boolean allowPlayerToggle) {}

	/** What a closing gate does with players and mobs in its opening. */
	public enum EntityInGate {
		/** Close anyway; the blocks are placed in them (vanilla suffocation). */
		SUFFOCATE,
		/** Close anyway; they are pushed out of the gate to the nearest side. */
		PUSH,
		/** Don't close until the opening is free. */
		WAIT
	}

	public record Redstone(int checkIntervalTicks, boolean allowInverted, EntityInGate entityInGate) {}

	public record Protection(boolean allowOpBreak) {}

	public record Materials(boolean requireBlocks, boolean creativeFree, boolean returnOnRemove, int chestThreshold) {}
}
