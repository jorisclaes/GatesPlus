package be.jorisclaes.gatesplus.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Numbered, forward-only schema migrations. Each migration runs in a transaction.
 * A database newer than this mod knows is refused.
 */
public final class SchemaMigrator {
	private static final Logger LOGGER = LoggerFactory.getLogger("gatesplus/storage");

	/** Index i holds migration to version i+1. */
	static final List<Function<SqlDialect, List<String>>> MIGRATIONS = List.of(SchemaMigrator::v1, SchemaMigrator::v2);

	public static int latestVersion() {
		return MIGRATIONS.size();
	}

	private SchemaMigrator() {}

	/**
	 * @param sqliteFile database file to back up before migrating, or null (MySQL)
	 */
	public static void migrate(Connection c, SqlDialect dialect, Path sqliteFile) throws StorageException {
		try {
			try (Statement st = c.createStatement()) {
				st.executeUpdate("CREATE TABLE IF NOT EXISTS gp_schema_version (version INT NOT NULL)" + dialect.tableSuffix);
			}
			int current = currentVersion(c);
			int latest = latestVersion();
			if (current > latest) {
				throw new StorageException("Database schema version " + current
						+ " is newer than this GatesPlus version supports (" + latest + "). Update the mod.");
			}
			if (current == latest) return;

			if (current > 0 && sqliteFile != null && Files.exists(sqliteFile)) {
				Path backup = sqliteFile.resolveSibling(sqliteFile.getFileName() + ".v" + current + ".bak");
				Files.copy(sqliteFile, backup, StandardCopyOption.REPLACE_EXISTING);
				LOGGER.info("Backed up database to {}", backup);
			}

			boolean auto = c.getAutoCommit();
			c.setAutoCommit(false);
			try {
				for (int v = current; v < latest; v++) {
					try (Statement st = c.createStatement()) {
						for (String sql : MIGRATIONS.get(v).apply(dialect)) st.executeUpdate(sql);
						if (v == 0) st.executeUpdate("INSERT INTO gp_schema_version (version) VALUES (1)");
						else st.executeUpdate("UPDATE gp_schema_version SET version = " + (v + 1));
					}
					c.commit();
					LOGGER.info("Migrated GatesPlus database to schema version {}", v + 1);
				}
			} catch (SQLException e) {
				c.rollback();
				throw e;
			} finally {
				c.setAutoCommit(auto);
			}
		} catch (SQLException | IOException e) {
			throw new StorageException("Schema migration failed: " + e.getMessage(), e);
		}
	}

	static int currentVersion(Connection c) throws SQLException {
		try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT version FROM gp_schema_version")) {
			return rs.next() ? rs.getInt(1) : 0;
		}
	}

	/** V2: blocks that didn't fit in return chests, waiting for /gp claim. */
	private static List<String> v2(SqlDialect d) {
		return List.of(
				"CREATE TABLE gp_pending_returns ("
						+ "owner_uuid CHAR(36) NOT NULL, "
						+ "item VARCHAR(128) NOT NULL, "
						+ "amount INT NOT NULL, "
						+ "PRIMARY KEY (owner_uuid, item))" + d.tableSuffix);
	}

	private static List<String> v1(SqlDialect d) {
		String key = d.keyString;
		return List.of(
				"CREATE TABLE gp_groups ("
						+ "id " + d.autoIncrementPk + ", "
						+ "name " + key + " NOT NULL UNIQUE, "
						+ "owner_uuid CHAR(36) NOT NULL, "
						+ "owner_name VARCHAR(32) NOT NULL, "
						+ "created_at BIGINT NOT NULL)" + d.tableSuffix,
				"CREATE TABLE gp_group_members ("
						+ "group_id INT NOT NULL, "
						+ "player_uuid CHAR(36) NOT NULL, "
						+ "player_name VARCHAR(32) NOT NULL, "
						+ "PRIMARY KEY (group_id, player_uuid))" + d.tableSuffix,
				"CREATE TABLE gp_gates ("
						+ "id " + d.autoIncrementPk + ", "
						+ "owner_uuid CHAR(36) NOT NULL, "
						+ "owner_name VARCHAR(32) NOT NULL, "
						+ "group_id INT NULL, "
						+ "scope_key " + key + " NOT NULL, "
						+ "name " + key + " NOT NULL, "
						+ "dimension VARCHAR(128) NOT NULL, "
						+ "min_x INT NOT NULL, min_y INT NOT NULL, min_z INT NOT NULL, "
						+ "max_x INT NOT NULL, max_y INT NOT NULL, max_z INT NOT NULL, "
						+ "trigger_x INT NOT NULL, trigger_y INT NOT NULL, trigger_z INT NOT NULL, "
						+ "trigger_block VARCHAR(128) NOT NULL, "
						+ "block VARCHAR(128) NOT NULL, "
						+ "indestructible BOOLEAN NOT NULL, "
						+ "fireproof BOOLEAN NOT NULL, "
						+ "animation_enabled BOOLEAN NOT NULL, "
						+ "direction VARCHAR(16) NOT NULL, "
						+ "animation_ticks INT NOT NULL, "
						+ "redstone_inverted BOOLEAN NOT NULL, "
						+ "is_open BOOLEAN NOT NULL, "
						+ "status VARCHAR(16) NOT NULL, "
						+ "placed_count INT NOT NULL, "
						+ "paid_count INT NOT NULL, "
						+ "total_count INT NOT NULL, "
						+ "created_at BIGINT NOT NULL, "
						+ "UNIQUE (scope_key, name))" + d.tableSuffix,
				"CREATE TABLE gp_return_chests ("
						+ "dimension VARCHAR(128) NOT NULL, "
						+ "x INT NOT NULL, y INT NOT NULL, z INT NOT NULL, "
						+ "owner_uuid CHAR(36) NOT NULL, "
						+ "created_at BIGINT NOT NULL, "
						+ "PRIMARY KEY (dimension, x, y, z))" + d.tableSuffix);
	}
}
