package be.jorisclaes.gatesplus.gate;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

import be.jorisclaes.gatesplus.GatesPlus;
import be.jorisclaes.gatesplus.group.Group;
import be.jorisclaes.gatesplus.network.PanelPayload;
import be.jorisclaes.gatesplus.session.CreateSession;
import be.jorisclaes.gatesplus.util.Messages;

/** Builds the rows of the top-right info panel (and the same text for /gp info). */
public final class InfoPanel {
	private InfoPanel() {}

	private static String row(ServerPlayer p, String labelKey, String value) {
		return Messages.raw(p, "panel.label." + labelKey) + "\t" + value;
	}

	private static String yesNo(ServerPlayer p, boolean v) {
		return Messages.raw(p, v ? "panel.yes" : "panel.no");
	}

	private static String blockLabel(String id) {
		Block block = GateManager.resolveBlock(id);
		return block.getName().getString() + " §7(" + id + ")";
	}

	public static PanelPayload forSession(ServerPlayer p, CreateSession s) {
		GateService service = GatesPlus.get().gates();
		Gate gate = s.type() == CreateSession.Type.EDIT ? service.manager().byId(s.gateId()) : null;
		List<String> rows = new ArrayList<>();

		rows.add(row(p, "gate", gate == null ? Messages.raw(p, "panel.new_gate") : "§f" + gate.name() + " §7(#" + gate.id() + ")"));
		rows.add(row(p, "block", blockLabel(BlockFilter.normalize(s.block))));
		rows.add(row(p, "trigger", s.trigger() == null ? Messages.raw(p, "panel.not_set")
				: "§b" + s.trigger().getX() + " " + s.trigger().getY() + " " + s.trigger().getZ()));
		String c1 = s.corner1() == null ? Messages.raw(p, "panel.not_set")
				: "§6" + s.corner1().getX() + " " + s.corner1().getY() + " " + s.corner1().getZ();
		String c2 = s.corner2() == null ? Messages.raw(p, "panel.not_set")
				: "§c" + s.corner2().getX() + " " + s.corner2().getY() + " " + s.corner2().getZ();
		rows.add(row(p, "corners", c1 + " §7/ " + c2));
		if (s.hasBox()) {
			rows.add(row(p, "size", "§f" + s.sizeX() + "×" + s.sizeY() + "×" + s.sizeZ() + " = " + s.volume()));
		}

		int total = s.volume();
		Block block = GateManager.resolveBlock(BlockFilter.normalize(s.block));
		int have = Materials.count(p, block.asItem());
		if (gate != null) {
			rows.add(row(p, "blocks", countColor(gate.placedCount(), gate.totalCount()) + gate.placedCount() + " / "
					+ gate.totalCount() + " §7" + Messages.raw(p, "panel.in_inventory", have)));
			rows.add(row(p, "status", statusLabel(p, gate.status())));
		} else if (total > 0) {
			rows.add(row(p, "blocks", countColor(0, total) + "0 / " + total + " §7" + Messages.raw(p, "panel.in_inventory", have)));
		}
		rows.add(row(p, "protection", Messages.raw(p, "panel.indestructible") + " " + check(s.indestructible) + "  "
				+ Messages.raw(p, "panel.fireproof") + " " + check(s.fireproof)));
		rows.add(row(p, "animation", animationLabel(p, s.animationEnabled, s.direction)));
		rows.add(row(p, "redstone", Messages.raw(p, s.redstoneInverted ? "panel.redstone_inverted" : "panel.redstone_normal")));
		rows.add(row(p, "group", groupLabel(p, s.groupId, gate != null ? gate.ownerName() : p.getGameProfile().name())));

		String title = Messages.raw(p, s.type() == CreateSession.Type.EDIT ? "panel.title_edit" : "panel.title_create");
		if (s.mode() == CreateSession.Mode.TRIGGER) title += " §b" + Messages.raw(p, "panel.trigger_mode");
		return new PanelPayload(PanelPayload.INFO, title, rows);
	}

	/** Rows for /gp info. */
	public static List<String> forGate(ServerPlayer p, Gate gate) {
		List<String> rows = new ArrayList<>();
		rows.add(row(p, "gate", "§f" + gate.name() + " §7(#" + gate.id() + ")"));
		rows.add(row(p, "block", blockLabel(gate.block())));
		rows.add(row(p, "trigger", "§b" + gate.triggerX() + " " + gate.triggerY() + " " + gate.triggerZ()));
		rows.add(row(p, "corners", "§f" + gate.minX() + " " + gate.minY() + " " + gate.minZ() + " §7/ §f"
				+ gate.maxX() + " " + gate.maxY() + " " + gate.maxZ() + " §7(" + gate.dimension() + ")"));
		rows.add(row(p, "blocks", countColor(gate.placedCount(), gate.totalCount()) + gate.placedCount() + " / "
				+ gate.totalCount() + " §7" + Messages.raw(p, "panel.paid", gate.paidCount())));
		rows.add(row(p, "status", statusLabel(p, gate.status()) + " §7(" + Messages.raw(p, gate.isOpen() ? "panel.open" : "panel.closed") + ")"));
		rows.add(row(p, "protection", Messages.raw(p, "panel.indestructible") + " " + check(gate.indestructible()) + "  "
				+ Messages.raw(p, "panel.fireproof") + " " + check(gate.fireproof())));
		rows.add(row(p, "animation", animationLabel(p, gate.animationEnabled(), gate.direction())));
		rows.add(row(p, "redstone", Messages.raw(p, gate.redstoneInverted() ? "panel.redstone_inverted" : "panel.redstone_normal")));
		rows.add(row(p, "group", groupLabel(p, gate.groupId(), gate.ownerName())));
		return rows;
	}

	/** One-line summary for the action bar (players without the client mod). */
	public static String shortSummary(ServerPlayer p, CreateSession s) {
		String size = s.hasBox() ? s.sizeX() + "×" + s.sizeY() + "×" + s.sizeZ() : "-";
		String trigger = s.trigger() == null ? "-" : s.trigger().getX() + " " + s.trigger().getY() + " " + s.trigger().getZ();
		return size + " | " + BlockFilter.normalize(s.block) + " | trigger " + trigger;
	}

	private static String check(boolean v) {
		return v ? "§a✔" : "§c✘";
	}

	private static String countColor(int placed, int total) {
		return placed >= total ? "§a" : "§c";
	}

	public static String statusLabel(ServerPlayer p, GateStatus status) {
		return switch (status) {
			case OK -> "§a" + Messages.raw(p, "panel.status.ok");
			case INCOMPLETE -> "§e" + Messages.raw(p, "panel.status.incomplete");
			case BROKEN -> "§c" + Messages.raw(p, "panel.status.broken");
			case NO_TRIGGER -> "§c" + Messages.raw(p, "panel.status.no_trigger");
		};
	}

	private static String animationLabel(ServerPlayer p, boolean enabled, GateDirection dir) {
		if (!GatesPlus.get().config().animation().enabled()) return "§7" + Messages.raw(p, "panel.animation_global_off");
		String d = Messages.raw(p, "panel.direction." + dir.id());
		return enabled ? "§a" + Messages.raw(p, "panel.on") + "§7, " + d : "§7" + Messages.raw(p, "panel.off");
	}

	private static String groupLabel(ServerPlayer p, Integer groupId, String ownerName) {
		Group g = GatesPlus.get().gates().groups().byId(groupId);
		if (g == null) return "§f" + Messages.raw(p, "panel.personal", ownerName);
		return "§d" + g.name() + " §7(" + Messages.raw(p, "panel.owner", g.ownerName()) + ")";
	}
}
