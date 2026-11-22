package be.jorisclaes.gatesplus.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import be.jorisclaes.gatesplus.GatesPlus;

/**
 * Translated chat messages from lang/nl.yml and lang/en.yml. The files are copied to
 * config/gatesplus/lang/ so admins can change the wording; missing keys fall back to the bundled text.
 * Placeholders are {0}, {1}, ... and '&' colour codes are supported.
 */
public final class Messages {
	private static final String[] LANGS = {"nl", "en"};
	private static final Map<String, Map<String, String>> TABLES = new HashMap<>();

	private Messages() {}

	public static void load(Path configDir) {
		TABLES.clear();
		Path langDir = configDir.resolve("lang");
		for (String lang : LANGS) {
			Map<String, String> table = new HashMap<>(readBundled(lang));
			Path file = langDir.resolve(lang + ".yml");
			try {
				Files.createDirectories(langDir);
				if (Files.notExists(file)) {
					try (InputStream in = Messages.class.getResourceAsStream("/gatesplus/lang/" + lang + ".yml")) {
						if (in != null) Files.copy(in, file);
					}
				} else {
					try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
						flatten("", new Yaml(new SafeConstructor(new LoaderOptions())).load(r), table);
					}
				}
			} catch (IOException | RuntimeException e) {
				GatesPlus.LOGGER.warn("Could not read {}: {}", file, e.getMessage());
			}
			TABLES.put(lang, table);
		}
	}

	private static Map<String, String> readBundled(String lang) {
		Map<String, String> out = new HashMap<>();
		try (InputStream in = Messages.class.getResourceAsStream("/gatesplus/lang/" + lang + ".yml")) {
			if (in != null) {
				flatten("", new Yaml(new SafeConstructor(new LoaderOptions()))
						.load(new InputStreamReader(in, StandardCharsets.UTF_8)), out);
			}
		} catch (IOException e) {
			GatesPlus.LOGGER.warn("Missing bundled language file {}", lang);
		}
		return out;
	}

	private static void flatten(String prefix, Object node, Map<String, String> out) {
		if (node instanceof Map<?, ?> map) {
			for (var e : map.entrySet()) flatten(prefix + e.getKey() + ".", e.getValue(), out);
		} else if (node != null) {
			out.put(prefix.substring(0, prefix.length() - 1), String.valueOf(node));
		}
	}

	/** Server language, or the player's client language when language is "auto". */
	public static String languageOf(ServerPlayer player) {
		String cfg = GatesPlus.get().config().language();
		if (!cfg.equals("auto")) return cfg;
		if (player == null) return "en";
		String client = player.clientInformation().language();
		return client != null && client.startsWith("nl") ? "nl" : "en";
	}

	public static String raw(String lang, String key, Object... args) {
		Map<String, String> table = TABLES.getOrDefault(lang, Map.of());
		String text = table.get(key);
		if (text == null) text = TABLES.getOrDefault("en", Map.of()).getOrDefault(key, key);
		for (int i = 0; i < args.length; i++) text = text.replace("{" + i + "}", String.valueOf(args[i]));
		return text.replace('&', '§');
	}

	public static String raw(ServerPlayer player, String key, Object... args) {
		return raw(languageOf(player), key, args);
	}

	public static Component get(ServerPlayer player, String key, Object... args) {
		return Component.literal(raw(player, key, args));
	}

	public static Component get(CommandSourceStack source, String key, Object... args) {
		return get(source.getPlayer(), key, args);
	}

	public static void send(ServerPlayer player, String key, Object... args) {
		player.sendSystemMessage(get(player, key, args));
	}

	public static void actionBar(ServerPlayer player, String key, Object... args) {
		player.sendSystemMessage(get(player, key, args), true);
	}
}
