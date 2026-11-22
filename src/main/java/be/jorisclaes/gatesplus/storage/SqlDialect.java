package be.jorisclaes.gatesplus.storage;

/** The few SQL differences between SQLite and MySQL. */
public enum SqlDialect {
	SQLITE("INTEGER PRIMARY KEY AUTOINCREMENT", "TEXT", ""),
	MYSQL("INT NOT NULL AUTO_INCREMENT PRIMARY KEY", "VARCHAR(191)", " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

	final String autoIncrementPk;
	/** Indexed string column type (MySQL needs a bounded length). */
	final String keyString;
	final String tableSuffix;

	SqlDialect(String autoIncrementPk, String keyString, String tableSuffix) {
		this.autoIncrementPk = autoIncrementPk;
		this.keyString = keyString;
		this.tableSuffix = tableSuffix;
	}
}
