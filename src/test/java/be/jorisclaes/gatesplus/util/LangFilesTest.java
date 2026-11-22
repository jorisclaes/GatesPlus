package be.jorisclaes.gatesplus.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class LangFilesTest {
	private static Map<String, String> load(String lang) throws IOException {
		Map<String, String> out = new TreeMap<>();
		try (var in = LangFilesTest.class.getResourceAsStream("/gatesplus/lang/" + lang + ".yml")) {
			assertNotNull(in, lang);
			flatten("", new Yaml(new SafeConstructor(new LoaderOptions())).load(new InputStreamReader(in, StandardCharsets.UTF_8)), out);
		}
		return out;
	}

	private static void flatten(String prefix, Object node, Map<String, String> out) {
		if (node instanceof Map<?, ?> m) {
			for (var e : m.entrySet()) {
				assertInstanceOf(String.class, e.getKey(), "non-string key under " + prefix + " (quote yes/no/on/off)");
				flatten(prefix + e.getKey() + ".", e.getValue(), out);
			}
		} else {
			assertInstanceOf(String.class, node, "non-string value at " + prefix);
			out.put(prefix.substring(0, prefix.length() - 1), (String) node);
		}
	}

	@Test
	void languagesHaveSameKeys() throws IOException {
		assertEquals(load("nl").keySet(), load("en").keySet());
	}

	@Test
	void everyKeyUsedInCodeExists() throws IOException {
		Map<String, String> nl = load("nl");
		Set<String> used = new TreeSet<>();
		Pattern call = Pattern.compile("(?:Messages\\.(?:send|actionBar|raw|get)\\(\\w+, |fail\\(player, |notifyGate\\(\\w+, )\"([a-z_.]+)\"");
		try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
			for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
				Matcher m = call.matcher(Files.readString(f));
				while (m.find()) if (!m.group(1).endsWith(".")) used.add(m.group(1));
			}
		}
		assertTrue(used.size() > 50, "pattern found too few keys: " + used.size());
		List<String> missing = new ArrayList<>();
		for (String k : used) if (!nl.containsKey(k)) missing.add(k);
		assertEquals(List.of(), missing);
	}
}
