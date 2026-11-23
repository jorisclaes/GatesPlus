package be.jorisclaes.gatesplus.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class ConfigUpdaterTest {
	private static Map<?, ?> yaml(String s) {
		return (Map<?, ?>) new Yaml(new SafeConstructor(new LoaderOptions())).load(s);
	}

	@Test
	void addsMissingNestedAndTopLevelKeysKeepingUserValues() {
		String defaults = """
				# header
				session:
				  timeout-minutes: 10
				  # set the corner when placing
				  marker-sets-corner: true

				redstone:
				  allow-inverted: true
				  # what to do
				  entity-in-gate: suffocate

				# new section
				extra:
				  list:
				    - a
				""";
		String user = """
				# my config
				session:
				  timeout-minutes: 30

				redstone:
				  allow-inverted: false
				""";
		ConfigUpdater.Result r = ConfigUpdater.update(user, defaults, yaml(user));
		assertEquals(List.of("session.marker-sets-corner", "redstone.entity-in-gate", "extra"), r.added());
		Map<?, ?> out = yaml(r.text());
		assertEquals(30, ((Map<?, ?>) out.get("session")).get("timeout-minutes"));
		assertEquals(true, ((Map<?, ?>) out.get("session")).get("marker-sets-corner"));
		assertEquals(false, ((Map<?, ?>) out.get("redstone")).get("allow-inverted"));
		assertEquals("suffocate", ((Map<?, ?>) out.get("redstone")).get("entity-in-gate"));
		assertEquals(List.of("a"), ((Map<?, ?>) out.get("extra")).get("list"));
		assertTrue(r.text().contains("  # what to do\n  entity-in-gate: suffocate"));
		assertTrue(r.text().startsWith("# my config"));
	}

	@Test
	void completeFileIsUnchanged() throws Exception {
		String defaults;
		try (var in = ConfigLoader.class.getResourceAsStream(ConfigLoader.DEFAULT_RESOURCE)) {
			defaults = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		ConfigUpdater.Result r = ConfigUpdater.update(defaults, defaults, yaml(defaults));
		assertEquals(List.of(), r.added());
		assertEquals(defaults, r.text());
	}

	@Test
	void oldFileOnDiskGetsNewOptionsAndBackup(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("config.yml"), """
				language: en
				redstone:
				  check-interval-ticks: 8
				""");
		List<String> warnings = new ArrayList<>();
		GatesPlusConfig cfg = ConfigLoader.load(dir, warnings);
		assertEquals("en", cfg.language());
		assertEquals(8, cfg.redstone().checkIntervalTicks());
		assertEquals(GatesPlusConfig.EntityInGate.SUFFOCATE, cfg.redstone().entityInGate());
		String text = Files.readString(dir.resolve("config.yml"));
		assertTrue(text.contains("entity-in-gate: suffocate"), text);
		assertTrue(text.contains("marker-sets-corner: true"), text);
		assertTrue(Files.exists(dir.resolve("config.yml.bak")));
		// the merged file parses cleanly with the full default set
		List<String> w2 = new ArrayList<>();
		ConfigLoader.parse(new StringReader(text), w2);
		assertEquals(List.of(), w2);
		assertEquals(1, warnings.size(), warnings.toString());
	}
}
