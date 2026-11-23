package be.jorisclaes.gatesplus.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import be.jorisclaes.gatesplus.network.Network;
import be.jorisclaes.gatesplus.network.PanelPayload;

/**
 * The info panel and the dev panel. With the client mod they are HUD panels; without it they are
 * shown in the scoreboard sidebar of that one player (packets only, the server scoreboard is not
 * touched). The sidebar shows one panel: the info panel during a session, else the dev panel.
 */
public final class Panels {
	private static final String OBJECTIVE = "gatesplus_panel";
	private static final int MAX_LINES = 15;
	private static final Scoreboard DUMMY_BOARD = new Scoreboard();

	/** Per player without client mod: the panels currently shown and the lines last sent. */
	private static final Map<UUID, Sidebar> SIDEBARS = new HashMap<>();

	private static final class Sidebar {
		PanelPayload info;
		PanelPayload dev;
		String title;
		List<String> lines = List.of();
	}

	private Panels() {}

	/** Shows (or, with no rows, hides) a panel. */
	public static void show(ServerPlayer player, PanelPayload panel) {
		if (Network.send(player, panel)) return;
		Sidebar sb = SIDEBARS.get(player.getUUID());
		if (sb == null) {
			if (panel.rows().isEmpty()) return;
			sb = new Sidebar();
			SIDEBARS.put(player.getUUID(), sb);
		}
		PanelPayload value = panel.rows().isEmpty() ? null : panel;
		if (panel.panel() == PanelPayload.DEVSTATS) sb.dev = value;
		else sb.info = value;
		render(player, sb);
	}

	public static void hide(ServerPlayer player, int panel) {
		show(player, PanelPayload.hide(panel));
	}

	/** Player left: forget the state (the client drops the objective itself). */
	public static void forget(UUID player) {
		SIDEBARS.remove(player);
	}

	private static void render(ServerPlayer player, Sidebar sb) {
		if (sb.info == null && sb.dev == null) {
			removeSidebar(player);
			SIDEBARS.remove(player.getUUID());
			return;
		}
		// both panels don't fit in the 15 sidebar lines: during a session the info panel takes the
		// sidebar, otherwise the dev panel (its numbers are also printed in chat by /gp devstats)
		PanelPayload shown = sb.info != null ? sb.info : sb.dev;
		String title = shown.title();
		List<String> lines = new ArrayList<>();
		for (String row : shown.rows()) lines.add(format(row));
		if (lines.size() > MAX_LINES) lines = new ArrayList<>(lines.subList(0, MAX_LINES));

		Objective objective = objective(title);
		if (sb.title == null) {
			player.connection.send(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_ADD));
			player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
		} else if (!sb.title.equals(title)) {
			player.connection.send(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_CHANGE));
		}
		sb.title = title;

		// one score per line; the score only fixes the order (highest on top) and is not shown
		for (int i = 0; i < lines.size(); i++) {
			if (i < sb.lines.size() && sb.lines.get(i).equals(lines.get(i))) continue;
			player.connection.send(new ClientboundSetScorePacket(owner(i), OBJECTIVE, MAX_LINES - i,
					Optional.of(Component.literal(lines.get(i))), Optional.of(BlankFormat.INSTANCE)));
		}
		for (int i = lines.size(); i < sb.lines.size(); i++) {
			player.connection.send(new ClientboundResetScorePacket(owner(i), OBJECTIVE));
		}
		sb.lines = lines;
	}

	/** Removes our objective and gives the sidebar back to the server's own objective, if any. */
	private static void removeSidebar(ServerPlayer player) {
		player.connection.send(new ClientboundSetObjectivePacket(objective(""), ClientboundSetObjectivePacket.METHOD_REMOVE));
		Objective own = player.level().getServer().getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
		if (own != null) player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, own));
	}

	private static Objective objective(String title) {
		return new Objective(DUMMY_BOARD, OBJECTIVE, ObjectiveCriteria.DUMMY, Component.literal(title),
				ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
	}

	/** Invisible, unique score holder per line (the display name is what is shown). */
	private static String owner(int line) {
		return "#gp" + line;
	}

	/** "label\tvalue" -> "§7label: §fvalue" (values may contain their own colour codes). */
	private static String format(String row) {
		int tab = row.indexOf('\t');
		if (tab < 0) return "§7" + row;
		return "§7" + row.substring(0, tab) + ": §f" + row.substring(tab + 1);
	}
}
