package be.jorisclaes.gatesplus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.jorisclaes.gatesplus.config.ConfigLoader;
import be.jorisclaes.gatesplus.config.GatesPlusConfig;
import be.jorisclaes.gatesplus.storage.AsyncStorage;
import be.jorisclaes.gatesplus.storage.GateStorage;
import be.jorisclaes.gatesplus.storage.SqlGateStorage;
import be.jorisclaes.gatesplus.storage.StorageException;

public class GatesPlus implements ModInitializer {
	public static final String MOD_ID = "gatesplus";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static GatesPlus instance;

	private Path configDir;
	private volatile GatesPlusConfig config;
	private AsyncStorage storage;
	private MinecraftServer server;

	@Override
	public void onInitialize() {
		instance = this;
		configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
		reloadConfig();

		ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> onServerStopped());
	}

	public static GatesPlus get() {
		return instance;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public GatesPlusConfig config() {
		return config;
	}

	public Path configDir() {
		return configDir;
	}

	public AsyncStorage storage() {
		return storage;
	}

	public MinecraftServer server() {
		return server;
	}

	/** @return warnings found while parsing; the old config is kept if the file can't be read */
	public List<String> reloadConfig() {
		List<String> warnings = new ArrayList<>();
		try {
			config = ConfigLoader.load(configDir, warnings);
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Could not read GatesPlus config, keeping previous values", e);
			warnings.add("config.yml kon niet gelezen worden: " + e.getMessage());
			if (config == null) {
				config = ConfigLoader.parse(new java.io.StringReader(""), new ArrayList<>());
			}
		}
		warnings.forEach(w -> LOGGER.warn("config.yml: {}", w));
		return warnings;
	}

	private void onServerStarting(MinecraftServer server) {
		this.server = server;
		GateStorage backend = switch (config.storage().type()) {
			case SQLITE -> SqlGateStorage.sqlite(configDir.resolve(config.storage().sqliteFile()));
			case MYSQL -> SqlGateStorage.mysql(config.storage().mysql());
		};
		try {
			backend.init();
		} catch (StorageException e) {
			backend.close();
			// Refuse to run with a broken database rather than silently losing gates.
			throw new IllegalStateException("GatesPlus: " + e.getMessage(), e);
		}
		storage = new AsyncStorage(backend);
		LOGGER.info("GatesPlus storage: {}", backend.describe());
	}

	private void onServerStopped() {
		if (storage != null) {
			storage.shutdown();
			storage = null;
		}
		server = null;
	}
}
