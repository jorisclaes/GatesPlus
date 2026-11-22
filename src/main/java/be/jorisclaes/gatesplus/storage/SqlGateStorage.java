package be.jorisclaes.gatesplus.storage;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import be.jorisclaes.gatesplus.config.GatesPlusConfig;
import be.jorisclaes.gatesplus.gate.Gate;
import be.jorisclaes.gatesplus.gate.GateDirection;
import be.jorisclaes.gatesplus.gate.GateStatus;
import be.jorisclaes.gatesplus.group.Group;

/** JDBC storage for SQLite and MySQL, pooled with HikariCP. All statements are prepared. */
public final class SqlGateStorage implements GateStorage {
	private static final String GATE_COLUMNS = "owner_uuid, owner_name, group_id, scope_key, name, dimension, "
			+ "min_x, min_y, min_z, max_x, max_y, max_z, trigger_x, trigger_y, trigger_z, trigger_block, block, "
			+ "indestructible, fireproof, animation_enabled, direction, animation_ticks, redstone_inverted, "
			+ "is_open, status, placed_count, paid_count, total_count, created_at";

	private final SqlDialect dialect;
	private final HikariConfig hikariConfig;
	private final Path sqliteFile;
	private final String description;
	private HikariDataSource dataSource;

	private SqlGateStorage(SqlDialect dialect, HikariConfig hikariConfig, Path sqliteFile, String description) {
		this.dialect = dialect;
		this.hikariConfig = hikariConfig;
		this.sqliteFile = sqliteFile;
		this.description = description;
	}

	public static SqlGateStorage sqlite(Path file) {
		HikariConfig hc = new HikariConfig();
		hc.setPoolName("GatesPlus-SQLite");
		hc.setDriverClassName("org.sqlite.JDBC");
		hc.setJdbcUrl("jdbc:sqlite:" + file.toAbsolutePath());
		// SQLite allows a single writer; one connection avoids SQLITE_BUSY.
		hc.setMaximumPoolSize(1);
		hc.addDataSourceProperty("foreign_keys", "true");
		hc.addDataSourceProperty("journal_mode", "WAL");
		return new SqlGateStorage(SqlDialect.SQLITE, hc, file, "SQLite (" + file.getFileName() + ")");
	}

	public static SqlGateStorage mysql(GatesPlusConfig.Mysql cfg) {
		HikariConfig hc = new HikariConfig();
		hc.setPoolName("GatesPlus-MySQL");
		hc.setDriverClassName("com.mysql.cj.jdbc.Driver");
		hc.setJdbcUrl("jdbc:mysql://" + cfg.host() + ":" + cfg.port() + "/" + cfg.database()
				+ "?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true");
		hc.setUsername(cfg.user());
		hc.setPassword(cfg.password());
		hc.setMaximumPoolSize(cfg.poolSize());
		hc.addDataSourceProperty("cachePrepStmts", "true");
		hc.addDataSourceProperty("useServerPrepStmts", "true");
		return new SqlGateStorage(SqlDialect.MYSQL, hc, null,
				"MySQL (" + cfg.host() + ":" + cfg.port() + "/" + cfg.database() + ")");
	}

	@Override
	public void init() throws StorageException {
		try {
			dataSource = new HikariDataSource(hikariConfig);
		} catch (RuntimeException e) {
			throw new StorageException("Kan geen verbinding maken met " + description + ": " + e.getMessage(), e);
		}
		try (Connection c = dataSource.getConnection()) {
			SchemaMigrator.migrate(c, dialect, sqliteFile);
		} catch (SQLException e) {
			throw new StorageException("Database init failed: " + e.getMessage(), e);
		}
	}

	@Override
	public String describe() {
		return description;
	}

	@Override
	public void close() {
		if (dataSource != null) dataSource.close();
	}

	// ------------------------------------------------------------------ gates

	@Override
	public List<Gate> loadGates() throws StorageException {
		List<Gate> out = new ArrayList<>();
		try (Connection c = dataSource.getConnection();
				Statement st = c.createStatement();
				ResultSet rs = st.executeQuery("SELECT id, " + GATE_COLUMNS + " FROM gp_gates")) {
			while (rs.next()) {
				Gate g = new Gate(rs.getInt("id"), UUID.fromString(rs.getString("owner_uuid")),
						rs.getString("owner_name"), rs.getString("name"), rs.getLong("created_at"));
				int groupId = rs.getInt("group_id");
				g.setGroupId(rs.wasNull() ? null : groupId);
				g.setDimension(rs.getString("dimension"));
				g.setBox(rs.getInt("min_x"), rs.getInt("min_y"), rs.getInt("min_z"),
						rs.getInt("max_x"), rs.getInt("max_y"), rs.getInt("max_z"));
				g.setTrigger(rs.getInt("trigger_x"), rs.getInt("trigger_y"), rs.getInt("trigger_z"),
						rs.getString("trigger_block"));
				g.setBlock(rs.getString("block"));
				g.setIndestructible(rs.getBoolean("indestructible"));
				g.setFireproof(rs.getBoolean("fireproof"));
				g.setAnimationEnabled(rs.getBoolean("animation_enabled"));
				g.setDirection(GateDirection.parse(rs.getString("direction")));
				g.setAnimationTicks(rs.getInt("animation_ticks"));
				g.setRedstoneInverted(rs.getBoolean("redstone_inverted"));
				g.setOpen(rs.getBoolean("is_open"));
				g.setStatus(GateStatus.valueOf(rs.getString("status")));
				g.setPlacedCount(rs.getInt("placed_count"));
				g.setPaidCount(rs.getInt("paid_count"));
				out.add(g);
			}
		} catch (SQLException | IllegalArgumentException e) {
			throw new StorageException("Loading gates failed: " + e.getMessage(), e);
		}
		return out;
	}

	@Override
	public int insertGate(Gate g) throws StorageException {
		String placeholders = "?, ".repeat(29);
		String sql = "INSERT INTO gp_gates (" + GATE_COLUMNS + ") VALUES ("
				+ placeholders.substring(0, placeholders.length() - 2) + ")";
		try (Connection c = dataSource.getConnection();
				PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			bindGate(ps, g);
			ps.setLong(29, g.createdAt());
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (!keys.next()) throw new StorageException("No id generated for gate " + g.name());
				return keys.getInt(1);
			}
		} catch (SQLException e) {
			throw new StorageException("Saving gate failed: " + e.getMessage(), e);
		}
	}

	@Override
	public void updateGate(Gate g) throws StorageException {
		String set = GATE_COLUMNS.replace(", created_at", "").replace(", ", " = ?, ") + " = ?";
		try (Connection c = dataSource.getConnection();
				PreparedStatement ps = c.prepareStatement("UPDATE gp_gates SET " + set + " WHERE id = ?")) {
			bindGate(ps, g);
			ps.setInt(29, g.id());
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new StorageException("Updating gate failed: " + e.getMessage(), e);
		}
	}

	/** Binds parameters 1..28 (everything except created_at). */
	private static void bindGate(PreparedStatement ps, Gate g) throws SQLException {
		int i = 1;
		ps.setString(i++, g.ownerUuid().toString());
		ps.setString(i++, g.ownerName());
		if (g.groupId() == null) ps.setNull(i++, java.sql.Types.INTEGER);
		else ps.setInt(i++, g.groupId());
		ps.setString(i++, g.scopeKey());
		ps.setString(i++, g.name());
		ps.setString(i++, g.dimension());
		ps.setInt(i++, g.minX());
		ps.setInt(i++, g.minY());
		ps.setInt(i++, g.minZ());
		ps.setInt(i++, g.maxX());
		ps.setInt(i++, g.maxY());
		ps.setInt(i++, g.maxZ());
		ps.setInt(i++, g.triggerX());
		ps.setInt(i++, g.triggerY());
		ps.setInt(i++, g.triggerZ());
		ps.setString(i++, g.triggerBlock());
		ps.setString(i++, g.block());
		ps.setBoolean(i++, g.indestructible());
		ps.setBoolean(i++, g.fireproof());
		ps.setBoolean(i++, g.animationEnabled());
		ps.setString(i++, g.direction().id());
		ps.setInt(i++, g.animationTicks());
		ps.setBoolean(i++, g.redstoneInverted());
		ps.setBoolean(i++, g.isOpen());
		ps.setString(i++, g.status().name());
		ps.setInt(i++, g.placedCount());
		ps.setInt(i++, g.paidCount());
		ps.setInt(i, g.totalCount());
	}

	@Override
	public void deleteGate(int id) throws StorageException {
		update("DELETE FROM gp_gates WHERE id = ?", id);
	}

	// ------------------------------------------------------------------ groups

	@Override
	public List<Group> loadGroups() throws StorageException {
		Map<Integer, Group> groups = new HashMap<>();
		try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
			try (ResultSet rs = st.executeQuery("SELECT id, name, owner_uuid, owner_name, created_at FROM gp_groups")) {
				while (rs.next()) {
					Group g = new Group(rs.getInt(1), rs.getString(2), UUID.fromString(rs.getString(3)),
							rs.getString(4), rs.getLong(5));
					groups.put(g.id(), g);
				}
			}
			try (ResultSet rs = st.executeQuery("SELECT group_id, player_uuid, player_name FROM gp_group_members")) {
				while (rs.next()) {
					Group g = groups.get(rs.getInt(1));
					if (g != null) g.putMember(UUID.fromString(rs.getString(2)), rs.getString(3));
				}
			}
		} catch (SQLException | IllegalArgumentException e) {
			throw new StorageException("Loading groups failed: " + e.getMessage(), e);
		}
		return new ArrayList<>(groups.values());
	}

	@Override
	public int insertGroup(Group g) throws StorageException {
		try (Connection c = dataSource.getConnection();
				PreparedStatement ps = c.prepareStatement(
						"INSERT INTO gp_groups (name, owner_uuid, owner_name, created_at) VALUES (?, ?, ?, ?)",
						Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, g.name());
			ps.setString(2, g.ownerUuid().toString());
			ps.setString(3, g.ownerName());
			ps.setLong(4, g.createdAt());
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (!keys.next()) throw new StorageException("No id generated for group " + g.name());
				return keys.getInt(1);
			}
		} catch (SQLException e) {
			throw new StorageException("Saving group failed: " + e.getMessage(), e);
		}
	}

	@Override
	public void deleteGroup(int id) throws StorageException {
		try (Connection c = dataSource.getConnection()) {
			boolean auto = c.getAutoCommit();
			c.setAutoCommit(false);
			try (PreparedStatement m = c.prepareStatement("DELETE FROM gp_group_members WHERE group_id = ?");
					PreparedStatement g = c.prepareStatement("DELETE FROM gp_groups WHERE id = ?")) {
				m.setInt(1, id);
				m.executeUpdate();
				g.setInt(1, id);
				g.executeUpdate();
				c.commit();
			} catch (SQLException e) {
				c.rollback();
				throw e;
			} finally {
				c.setAutoCommit(auto);
			}
		} catch (SQLException e) {
			throw new StorageException("Deleting group failed: " + e.getMessage(), e);
		}
	}

	@Override
	public void addMember(int groupId, UUID player, String name) throws StorageException {
		update("INSERT INTO gp_group_members (group_id, player_uuid, player_name) VALUES (?, ?, ?)",
				groupId, player.toString(), name);
	}

	@Override
	public void removeMember(int groupId, UUID player) throws StorageException {
		update("DELETE FROM gp_group_members WHERE group_id = ? AND player_uuid = ?", groupId, player.toString());
	}

	// ------------------------------------------------------------------ return chests

	@Override
	public List<ReturnChestRecord> loadReturnChests() throws StorageException {
		List<ReturnChestRecord> out = new ArrayList<>();
		try (Connection c = dataSource.getConnection();
				Statement st = c.createStatement();
				ResultSet rs = st.executeQuery("SELECT dimension, x, y, z, owner_uuid, created_at FROM gp_return_chests")) {
			while (rs.next()) {
				out.add(new ReturnChestRecord(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4),
						UUID.fromString(rs.getString(5)), rs.getLong(6)));
			}
		} catch (SQLException | IllegalArgumentException e) {
			throw new StorageException("Loading return chests failed: " + e.getMessage(), e);
		}
		return out;
	}

	@Override
	public void insertReturnChest(ReturnChestRecord r) throws StorageException {
		update("INSERT INTO gp_return_chests (dimension, x, y, z, owner_uuid, created_at) VALUES (?, ?, ?, ?, ?, ?)",
				r.dimension(), r.x(), r.y(), r.z(), r.ownerUuid().toString(), r.createdAt());
	}

	@Override
	public void deleteReturnChest(String dimension, int x, int y, int z) throws StorageException {
		update("DELETE FROM gp_return_chests WHERE dimension = ? AND x = ? AND y = ? AND z = ?", dimension, x, y, z);
	}

	private void update(String sql, Object... args) throws StorageException {
		try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
			for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new StorageException("Query failed: " + e.getMessage(), e);
		}
	}
}
