package be.jorisclaes.gatesplus.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import be.jorisclaes.gatesplus.config.GatesPlusConfig.*;

/**
 * Loads config.yml. The bundled default file (with comments) is copied on first start;
 * missing keys fall back to the defaults below, so older config files keep working.
 */
public final class ConfigLoader {
	public static final String DEFAULT_RESOURCE = "/gatesplus/config.yml";

	private ConfigLoader() {}

	public static GatesPlusConfig load(Path configDir, List<String> warnings) throws IOException {
		Files.createDirectories(configDir);
		Path file = configDir.resolve("config.yml");
		if (Files.notExists(file)) {
			try (InputStream in = ConfigLoader.class.getResourceAsStream(DEFAULT_RESOURCE)) {
				if (in == null) throw new IOException("Default config missing from jar: " + DEFAULT_RESOURCE);
				Files.copy(in, file);
			}
		}
		addMissingOptions(file, warnings);
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return parse(reader, warnings);
		}
	}

	/**
	 * A config.yml from an older version lacks newer options: add them (with their comments) so
	 * server owners can see and change them. A backup of the old file is kept as config.yml.bak.
	 */
	static void addMissingOptions(Path file, List<String> warnings) throws IOException {
		String user = Files.readString(file, StandardCharsets.UTF_8);
		String defaults;
		try (InputStream in = ConfigLoader.class.getResourceAsStream(DEFAULT_RESOURCE)) {
			if (in == null) return;
			defaults = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		Object tree;
		try {
			tree = new Yaml(new SafeConstructor(new LoaderOptions())).load(user);
		} catch (RuntimeException e) {
			return; // broken YAML: parse() reports it, don't touch the file
		}
		if (!(tree instanceof Map<?, ?> map)) return;
		ConfigUpdater.Result r = ConfigUpdater.update(user, defaults, map);
		if (r.added().isEmpty()) return;
		Files.writeString(file.resolveSibling("config.yml.bak"), user, StandardCharsets.UTF_8);
		Files.writeString(file, r.text(), StandardCharsets.UTF_8);
		warnings.add("config.yml aangevuld met nieuwe opties (standaardwaarde): " + String.join(", ", r.added())
				+ " - oude versie bewaard als config.yml.bak");
	}

	public static GatesPlusConfig parse(Reader reader, List<String> warnings) {
		Object root = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
		Node n = new Node(root instanceof Map<?, ?> m ? m : Map.of(), "", warnings);

		Node st = n.child("storage");
		Node my = st.child("mysql");
		Storage storage = new Storage(
				st.enumValue("type", StorageType.class, StorageType.SQLITE),
				st.child("sqlite").str("file", "gatesplus.db"),
				new Mysql(my.str("host", "localhost"), my.integer("port", 3306, 1, 65535),
						my.str("database", "gatesplus"), my.str("user", "root"), my.str("password", ""),
						my.integer("pool-size", 4, 1, 64)));

		Node l = n.child("limits");
		Limits limits = new Limits(
				l.integer("max-gates-per-player", 5, 0, Integer.MAX_VALUE),
				l.integer("max-gate-blocks", 256, 1, 100_000),
				l.integer("max-width", 16, 1, 1024),
				l.integer("max-height", 16, 1, 1024),
				l.integer("max-depth", 4, 1, 1024),
				l.integer("max-trigger-distance", 5, 1, 256),
				l.integer("max-groups-per-player", 5, 0, Integer.MAX_VALUE),
				l.integer("max-members-per-group", 20, 1, Integer.MAX_VALUE),
				l.integer("max-concurrent-animations", 50, 0, 10_000));

		Node d = n.child("defaults");
		String direction = d.str("direction", "vertical").toLowerCase(Locale.ROOT);
		if (!direction.equals("vertical") && !direction.equals("horizontal")) {
			warnings.add("defaults.direction moet vertical of horizontal zijn, kreeg '" + direction + "'");
			direction = "vertical";
		}
		Defaults defaults = new Defaults(d.str("block", "minecraft:dirt"), d.bool("indestructible", true),
				d.bool("fireproof", true), d.bool("animation-enabled", true), direction,
				d.integer("animation-ticks", 20, 1, 200));

		Node a = n.child("animation");
		Node r = n.child("redstone");
		Node m = n.child("materials");

		String language = n.str("language", "en").toLowerCase(Locale.ROOT);
		if (!List.of("nl", "en", "auto").contains(language)) {
			warnings.add("language moet nl, en of auto zijn, kreeg '" + language + "'");
			language = "en";
		}

		return new GatesPlusConfig(
				language,
				storage,
				limits,
				n.strList("allowed-blocks", List.of("minecraft:dirt")),
				n.strList("non-full-block-whitelist", List.of("minecraft:iron_bars", "minecraft:glass_pane")),
				new Session(n.child("session").integer("timeout-minutes", 10, 1, 1440),
						n.child("session").bool("marker-sets-corner", true),
						n.child("session").bool("custom-marker-blocks", false),
						n.child("session").enumValue("selection-tool", SelectionTool.class, SelectionTool.BOTH)),
				defaults,
				new Animation(a.bool("enabled", true), a.bool("allow-player-toggle", true)),
				new Redstone(r.integer("check-interval-ticks", 4, 1, 100), r.bool("allow-inverted", true),
						r.enumValue("entity-in-gate", GatesPlusConfig.EntityInGate.class, GatesPlusConfig.EntityInGate.SUFFOCATE)),
				new Protection(n.child("protection").bool("allow-op-break", false)),
				new Materials(m.bool("require-blocks", true), m.bool("creative-free", true),
						m.bool("return-on-remove", true), m.integer("chest-threshold", 60, 0, Integer.MAX_VALUE)),
				new Repair(n.child("repair").bool("command-enabled", true),
						n.child("repair").integer("max-distance", 0, 0, 100_000)));
	}

	/** Small typed view over a YAML map that records warnings instead of throwing. */
	private record Node(Map<?, ?> map, String path, List<String> warnings) {
		Node child(String key) {
			Object v = map.get(key);
			return new Node(v instanceof Map<?, ?> m ? m : Map.of(), path + key + ".", warnings);
		}

		String str(String key, String def) {
			Object v = map.get(key);
			return v == null ? def : String.valueOf(v);
		}

		boolean bool(String key, boolean def) {
			Object v = map.get(key);
			if (v == null) return def;
			if (v instanceof Boolean b) return b;
			warnings.add(path + key + " moet true of false zijn, kreeg '" + v + "'");
			return def;
		}

		int integer(String key, int def, int min, int max) {
			Object v = map.get(key);
			if (v == null) return def;
			if (v instanceof Number num) {
				int i = num.intValue();
				if (i < min || i > max) {
					warnings.add(path + key + " moet tussen " + min + " en " + max + " liggen, kreeg " + i);
					return Math.clamp(i, min, max);
				}
				return i;
			}
			warnings.add(path + key + " moet een getal zijn, kreeg '" + v + "'");
			return def;
		}

		<E extends Enum<E>> E enumValue(String key, Class<E> type, E def) {
			Object v = map.get(key);
			if (v == null) return def;
			try {
				return Enum.valueOf(type, String.valueOf(v).toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				warnings.add(path + key + " heeft ongeldige waarde '" + v + "'");
				return def;
			}
		}

		List<String> strList(String key, List<String> def) {
			Object v = map.get(key);
			if (v == null) return def;
			if (v instanceof List<?> list) {
				List<String> out = new ArrayList<>(list.size());
				for (Object o : list) if (o != null) out.add(String.valueOf(o).trim());
				return List.copyOf(out);
			}
			warnings.add(path + key + " moet een lijst zijn");
			return def;
		}
	}
}
