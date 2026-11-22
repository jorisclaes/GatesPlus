package be.jorisclaes.gatesplus.gate;

import java.util.Locale;

public enum GateDirection {
	/** Gate slides from bottom to top. */
	VERTICAL,
	/** Gate slides from left to right. */
	HORIZONTAL;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static GateDirection parse(String s) {
		return valueOf(s.toUpperCase(Locale.ROOT));
	}
}
