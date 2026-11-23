package be.jorisclaes.gatesplus.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {
	@Test
	void bundledDefaultParsesWithoutWarnings() throws Exception {
		List<String> warnings = new ArrayList<>();
		GatesPlusConfig cfg;
		try (var in = new InputStreamReader(ConfigLoader.class.getResourceAsStream(ConfigLoader.DEFAULT_RESOURCE),
				StandardCharsets.UTF_8)) {
			cfg = ConfigLoader.parse(in, warnings);
		}
		assertEquals(List.of(), warnings);
		assertEquals(GatesPlusConfig.StorageType.SQLITE, cfg.storage().type());
		assertEquals(60, cfg.materials().chestThreshold());
		assertEquals("minecraft:dirt", cfg.defaults().block());
		assertTrue(cfg.allowedBlocks().contains("minecraft:sand"));
		assertTrue(cfg.session().markerSetsCorner());
		assertFalse(cfg.session().customMarkerBlocks()); // vanilla clients can join by default
		assertEquals("en", cfg.language());
	}

	@Test
	void missingKeysUseDefaultsAndBadValuesWarn() {
		List<String> warnings = new ArrayList<>();
		GatesPlusConfig cfg = ConfigLoader.parse(new StringReader("""
				storage:
				  type: mysql
				limits:
				  max-width: -3
				animation:
				  enabled: nope
				defaults:
				  direction: diagonal
				"""), warnings);
		assertEquals(GatesPlusConfig.StorageType.MYSQL, cfg.storage().type());
		assertEquals(1, cfg.limits().maxWidth());
		assertTrue(cfg.animation().enabled());
		assertEquals("vertical", cfg.defaults().direction());
		assertEquals(5, cfg.limits().maxGatesPerPlayer());
		assertEquals("en", cfg.language()); // missing language = English
		assertTrue(cfg.session().markerSetsCorner());
		assertEquals(3, warnings.size(), warnings.toString());
	}

	@Test
	void markerSetsCornerCanBeTurnedOff() {
		GatesPlusConfig cfg = ConfigLoader.parse(new StringReader("""
				session:
				  marker-sets-corner: false
				"""), new ArrayList<>());
		assertFalse(cfg.session().markerSetsCorner());
	}

	@Test
	void customMarkerBlocksCanBeTurnedOn() {
		GatesPlusConfig cfg = ConfigLoader.parse(new StringReader("""
				session:
				  custom-marker-blocks: true
				"""), new ArrayList<>());
		assertTrue(cfg.session().customMarkerBlocks());
	}

	@Test
	void writesDefaultFileOnFirstLoad(@TempDir Path dir) throws Exception {
		ConfigLoader.load(dir, new ArrayList<>());
		assertTrue(Files.readString(dir.resolve("config.yml")).contains("GatesPlus configuratie"));
	}
}
