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

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import be.jorisclaes.gatesplus.animation.GateAnimator;
import be.jorisclaes.gatesplus.command.GpCommand;
import be.jorisclaes.gatesplus.config.ConfigLoader;
import be.jorisclaes.gatesplus.gate.BlockFilter;
import be.jorisclaes.gatesplus.gate.GateService;
import be.jorisclaes.gatesplus.marker.MarkerBlocks;
import be.jorisclaes.gatesplus.network.Network;
import be.jorisclaes.gatesplus.protection.Protection;
import be.jorisclaes.gatesplus.redstone.GateTicker;
import be.jorisclaes.gatesplus.util.DevStats;
import be.jorisclaes.gatesplus.util.Messages;
import be.jorisclaes.gatesplus.wand.Wand;
import be.jorisclaes.gatesplus.wand.WandEvents;
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
	private final GateService gates = new GateService();
	private final GateTicker ticker = new GateTicker(gates);
	private final DevStats devStats = new DevStats();

	@Override
	public void onInitialize() {
		instance = this;
		configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
		reloadConfig();
		Messages.load(configDir);
		MarkerBlocks.register();
		Network.register();
		Protection.register();
		WandEvents.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, ctx, env) -> GpCommand.register(dispatcher));

		ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> gates.shutdown());
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> onServerStopped());

		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> gates.manager().onChunkLoad(level, chunk.getPos()));
		ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
			gates.manager().onChunkUnload(level, chunk.getPos());
		});
		// leftover animation displays (e.g. after a crash) are removed when their chunk loads
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(GateAnimator.DISPLAY_TAG) && !gates.animator().isOwnDisplay(entity)) {
				entity.discard();
			}
			if (be.jorisclaes.gatesplus.marker.MarkerLabels.isStale(entity)) entity.discard();
		});

		ServerTickEvents.END_SERVER_TICK.register(this::onTick);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
			// sessions don't survive a restart or relog, so neither do the wand and marker blocks
			if (gates.sessions().get(handler.player) == null) {
				Wand.removeAll(handler.player);
				MarkerBlocks.removeItems(handler.player);
			}
			be.jorisclaes.gatesplus.network.Network.send(handler.player, gates.sessions().markersPayload());
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> {
			gates.sessions().onDisconnect(handler.player);
			devStats.remove(handler.player.getUUID());
			be.jorisclaes.gatesplus.util.Panels.forget(handler.player.getUUID());
		});
	}

	private void onTick(MinecraftServer server) {
		if (storage == null) return;
		gates.animator().tick(server.getTickCount());
		Protection.tick(server);
		ticker.tick(server);
		if (server.getTickCount() % 20 == 0) {
			gates.returnChests().tick(server);
			gates.sessions().tickTimeouts(server);
			devStats.tick(server);
		}
	}

	public GateService gates() {
		return gates;
	}

	public GateTicker ticker() {
		return ticker;
	}

	public DevStats devStats() {
		return devStats;
	}

	/** /gp reload: config, language files and the block filter (storage changes need a restart). */
	public List<String> reload() {
		List<String> warnings = reloadConfig();
		Messages.load(configDir);
		BlockFilter.rebuild(config);
		return warnings;
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
		BlockFilter.rebuild(config);
		try {
			gates.loadAll();
		} catch (StorageException e) {
			throw new IllegalStateException("GatesPlus: " + e.getMessage(), e);
		}
	}

	private void onServerStopped() {
		if (storage != null) {
			storage.shutdown();
			storage = null;
		}
		server = null;
	}
}
