package be.jorisclaes.gatesplus.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adds options that are missing from an existing config.yml (written by an older version), copied
 * from the bundled default together with their comments. Values, comments and order already in the
 * file are left untouched. Works on the text, so it only relies on the simple layout of our own
 * config: two-space indentation, one key per line.
 */
public final class ConfigUpdater {
	private ConfigUpdater() {}

	/** One "key:" line with the comments right above it and everything nested below it. */
	private record Entry(List<String> path, int indent, int start, int keyLine, int end) {}

	public record Result(String text, List<String> added) {}

	/**
	 * @param user        the current file
	 * @param defaults    the bundled default file
	 * @param userTree    the parsed current file (to see which keys exist)
	 */
	public static Result update(String user, String defaults, Map<?, ?> userTree) {
		List<String> userLines = new ArrayList<>(List.of(user.split("\n", -1)));
		List<String> defLines = List.of(defaults.split("\n", -1));
		List<String> added = new ArrayList<>();
		List<List<String>> skipUnder = new ArrayList<>();

		for (Entry e : scan(defLines)) {
			boolean underAdded = skipUnder.stream().anyMatch(p -> e.path().size() > p.size() && e.path().subList(0, p.size()).equals(p));
			if (underAdded || has(userTree, e.path())) continue;

			List<String> block = new ArrayList<>(defLines.subList(e.start(), e.end()));
			while (!block.isEmpty() && block.getLast().isBlank()) block.removeLast();
			List<String> parent = e.path().subList(0, e.path().size() - 1);
			int insertAt;
			if (parent.isEmpty()) {
				insertAt = lastNonBlank(userLines) + 1;
				block.addFirst("");
			} else {
				Entry p = find(scan(userLines), parent);
				if (p == null) continue; // parent exists in the tree but not as a plain section: leave it
				insertAt = p.end();
				while (insertAt > p.keyLine() + 1 && userLines.get(insertAt - 1).isBlank()) insertAt--;
			}
			userLines.addAll(insertAt, block);
			added.add(String.join(".", e.path()));
			skipUnder.add(e.path());
		}
		return new Result(String.join("\n", userLines), added);
	}

	private static int lastNonBlank(List<String> lines) {
		for (int i = lines.size() - 1; i >= 0; i--) if (!lines.get(i).isBlank()) return i;
		return -1;
	}

	private static boolean has(Map<?, ?> tree, List<String> path) {
		Object cur = tree;
		for (String k : path) {
			if (!(cur instanceof Map<?, ?> m) || !m.containsKey(k)) return false;
			cur = m.get(k);
		}
		return true;
	}

	private static Entry find(List<Entry> entries, List<String> path) {
		for (Entry e : entries) if (e.path().equals(path)) return e;
		return null;
	}

	private static List<Entry> scan(List<String> lines) {
		List<Entry> out = new ArrayList<>();
		List<String> stack = new ArrayList<>();
		List<Integer> indents = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			String t = line.strip();
			if (t.isEmpty() || t.startsWith("#") || t.startsWith("- ") || t.equals("-")) continue;
			int colon = t.indexOf(':');
			if (colon <= 0) continue;
			String key = t.substring(0, colon).replace("\"", "").replace("'", "");
			int indent = line.length() - line.stripLeading().length();
			while (!indents.isEmpty() && indents.getLast() >= indent) {
				indents.removeLast();
				stack.removeLast();
			}
			stack.add(key);
			indents.add(indent);

			int start = i;
			while (start > 0 && lines.get(start - 1).strip().startsWith("#")
					&& indentOf(lines.get(start - 1)) == indent) start--;
			int end = i + 1;
			while (end < lines.size()) {
				String l = lines.get(end);
				if (l.isBlank()) {
					end++;
					continue;
				}
				int li = indentOf(l);
				boolean nestedList = li == indent && l.strip().startsWith("-");
				if (li <= indent && !nestedList) break;
				end++;
			}
			// don't swallow the comments of the next key
			while (end > i + 1 && (lines.get(end - 1).isBlank() || lines.get(end - 1).strip().startsWith("#"))) end--;
			out.add(new Entry(List.copyOf(stack), indent, start, i, end));
		}
		return out;
	}

	private static int indentOf(String line) {
		return line.length() - line.stripLeading().length();
	}
}
