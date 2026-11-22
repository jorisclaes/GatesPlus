package be.jorisclaes.gatesplus.gate;

public enum GateStatus {
	/** Fully built, follows its trigger. */
	OK,
	/** Not all blocks invested yet; stays open until repaired. */
	INCOMPLETE,
	/** A gate block was destroyed; stays open until repaired. */
	BROKEN,
	/** The trigger block was destroyed; stays open until a new trigger is set via /gp edit. */
	NO_TRIGGER;

	public boolean isFunctional() {
		return this == OK;
	}
}
