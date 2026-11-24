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
		Materials materials,
		Repair repair) {

	public enum StorageType { SQLITE, MYSQL }

	public record Storage(StorageType type, String sqliteFile, Mysql mysql) {}

	public record Mysql(String host, int port, String database, String user, String password, int poolSize) {}

	public record Limits(int maxGatesPerPlayer, int maxGateBlocks, int maxWidth, int maxHeight, int maxDepth,
			int maxTriggerDistance, int maxGroupsPerPlayer, int maxMembersPerGroup, int maxConcurrentAnimations) {}

	/**
	 * @param customMarkerBlocks own marker blocks (every player needs the client mod); false = vanilla
	 *                           blocks with a floating label, so vanilla clients can join. Needs a restart.
	 */
	public record Session(int timeoutMinutes, boolean markerSetsCorner, boolean customMarkerBlocks,
			SelectionTool selectionTool) {
		/** Placing a marker sets its corner; always so when markers are the only selection tool. */
		public boolean markersSetCorner() {
			return markerSetsCorner || selectionTool == SelectionTool.MARKERS;
		}
	}

	/** How players select a gate: the axe, the marker blocks, or both. */
	public enum SelectionTool {
		AXE, MARKERS, BOTH;

		public boolean axe() { return this != MARKERS; }
		public boolean markers() { return this != AXE; }
	}

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

	/**
	 * @param commandEnabled false = /gp repair is off; holes must be filled by hand with the right block
	 * @param maxDistance    max distance (blocks) between the player and the gate for /gp repair; 0 = unlimited
	 */
	public record Repair(boolean commandEnabled, int maxDistance) {}
}
