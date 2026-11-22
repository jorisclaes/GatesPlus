package be.jorisclaes.gatesplus.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import be.jorisclaes.gatesplus.GatesPlus;
import be.jorisclaes.gatesplus.gate.BlockFilter;
import be.jorisclaes.gatesplus.gate.Gate;
import be.jorisclaes.gatesplus.gate.GateDirection;
import be.jorisclaes.gatesplus.gate.GateService;
import be.jorisclaes.gatesplus.gate.InfoPanel;
import be.jorisclaes.gatesplus.gate.ReturnChestTracker;
import be.jorisclaes.gatesplus.marker.MarkerBlocks;
import be.jorisclaes.gatesplus.group.Group;
import be.jorisclaes.gatesplus.network.Network;
import be.jorisclaes.gatesplus.session.CreateSession;
import be.jorisclaes.gatesplus.util.Messages;
import be.jorisclaes.gatesplus.util.Perms;

/** /gp (alias /gatesplus). */
public final class GpCommand {
	private GpCommand() {}

	private static GateService gates() {
		return GatesPlus.get().gates();
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralCommandNode<CommandSourceStack> root = dispatcher.register(literal("gp")
				.requires(Perms.USE.require())
				.executes(GpCommand::help)
				.then(literal("help").executes(GpCommand::help))
				.then(literal("create").executes(GpCommand::create))
				.then(literal("cancel").executes(GpCommand::cancel))
				.then(literal("confirm")
						.executes(c -> confirm(c, null))
						.then(argument("name", StringArgumentType.word()).executes(c -> confirm(c, str(c, "name")))))
				.then(literal("edit").then(argument("gate", StringArgumentType.word()).suggests(EDITABLE_GATES)
						.executes(GpCommand::edit)))
				.then(literal("set")
						.then(literal("block").then(argument("block", StringArgumentType.greedyString())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(BlockFilter.allowedIds(), b))
								.executes(GpCommand::setBlock)))
						.then(literal("animation")
								.then(literal("enabled").executes(c -> setAnimation(c, true)))
								.then(literal("disabled").executes(c -> setAnimation(c, false))))
						.then(literal("direction")
								.then(literal("vertical").executes(c -> setDirection(c, GateDirection.VERTICAL)))
								.then(literal("horizontal").executes(c -> setDirection(c, GateDirection.HORIZONTAL))))
						.then(literal("trigger").executes(GpCommand::setTrigger))
						.then(literal("redstone")
								.then(literal("normal").executes(c -> setRedstone(c, false)))
								.then(literal("inverted").executes(c -> setRedstone(c, true))))
						.then(literal("indestructible")
								.then(literal("on").executes(c -> setIndestructible(c, true)))
								.then(literal("off").executes(c -> setIndestructible(c, false))))
						.then(literal("fireproof")
								.then(literal("on").executes(c -> setFireproof(c, true)))
								.then(literal("off").executes(c -> setFireproof(c, false))))
						.then(literal("group").then(argument("group", StringArgumentType.word()).suggests(MY_GROUPS_OR_NONE)
								.executes(GpCommand::setGroup))))
				.then(literal("get").requires(Perms.GET_MARKER.require())
						.then(literal("markerblock").executes(GpCommand::getMarkers)))
				.then(literal("list")
						.executes(GpCommand::list)
						.then(literal("blocks").executes(GpCommand::listBlocks))
						.then(literal("all").requires(Perms.ADMIN_EDIT.require()).executes(GpCommand::listAll)))
				.then(literal("info").then(argument("gate", StringArgumentType.word()).suggests(VISIBLE_GATES)
						.executes(GpCommand::info)))
				.then(literal("remove").requires(Perms.REMOVE.require())
						.then(argument("gate", StringArgumentType.word()).suggests(REMOVABLE_GATES).executes(GpCommand::remove)))
				.then(literal("repair")
						.executes(GpCommand::repairSession)
						.then(argument("gate", StringArgumentType.word()).suggests(EDITABLE_GATES)
								.executes(GpCommand::repair)))
				.then(GroupCommands.build())
				.then(literal("reload").requires(Perms.ADMIN_RELOAD.require()).executes(GpCommand::reload))
				.then(literal("devstats").requires(Perms.ADMIN_DEVSTATS.require()).executes(GpCommand::devstats)));

		dispatcher.register(literal("gatesplus").requires(Perms.USE.require()).redirect(root));
	}

	static String str(CommandContext<CommandSourceStack> c, String name) {
		return StringArgumentType.getString(c, name);
	}

	static ServerPlayer player(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		return c.getSource().getPlayerOrException();
	}

	// ------------------------------------------------------------------ suggestions

	private static List<String> gateNames(CommandSourceStack src, boolean onlyRemovable) {
		ServerPlayer p = src.getPlayer();
		if (p == null) return List.of();
		UUID uuid = p.getUUID();
		List<String> out = new ArrayList<>();
		for (Gate g : gates().manager().all()) {
			boolean ok = onlyRemovable ? gates().groups().canRemove(g, uuid) : gates().groups().canSee(g, uuid);
			if (ok) out.add(g.name());
		}
		return out;
	}

	private static final SuggestionProvider<CommandSourceStack> VISIBLE_GATES =
			(c, b) -> SharedSuggestionProvider.suggest(gateNames(c.getSource(), false), b);
	private static final SuggestionProvider<CommandSourceStack> EDITABLE_GATES = VISIBLE_GATES;
	private static final SuggestionProvider<CommandSourceStack> REMOVABLE_GATES =
			(c, b) -> SharedSuggestionProvider.suggest(gateNames(c.getSource(), true), b);
	private static final SuggestionProvider<CommandSourceStack> MY_GROUPS_OR_NONE = (c, b) -> {
		List<String> names = new ArrayList<>();
		names.add("none");
		ServerPlayer p = c.getSource().getPlayer();
		if (p != null) for (Group g : gates().groups().groupsOf(p.getUUID())) names.add(g.name());
		return SharedSuggestionProvider.suggest(names, b);
	};

	// ------------------------------------------------------------------ handlers

	private static int help(CommandContext<CommandSourceStack> c) {
		ServerPlayer p = c.getSource().getPlayer();
		for (String line : Messages.raw(p, "help").split("\n")) {
			c.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		return 1;
	}

	private static int create(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		int max = GatesPlus.get().config().limits().maxGatesPerPlayer();
		if (!Perms.BYPASS_LIMITS.check(p) && gates().manager().ownedBy(p.getUUID()).size() >= max) {
			Messages.send(p, "create.max_gates", max);
			return 0;
		}
		gates().sessions().startCreate(p);
		Messages.send(p, "create.started");
		if (!Network.hasClientMod(p)) Messages.send(p, "create.no_client_mod");
		return 1;
	}

	private static int cancel(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		if (gates().sessions().get(p) == null) {
			Messages.send(p, "session.none");
			return 0;
		}
		gates().sessions().end(p, true);
		return 1;
	}

	private static CreateSession session(ServerPlayer p) {
		CreateSession s = gates().sessions().get(p);
		if (s == null) Messages.send(p, "session.none");
		return s;
	}

	private static int confirm(CommandContext<CommandSourceStack> c, String name) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		CreateSession s = session(p);
		if (s == null) return 0;
		if (gates().confirm(p, s, name)) {
			gates().sessions().end(p, false);
			return 1;
		}
		gates().sessions().sync(p);
		return 0;
	}

	private static int edit(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		boolean admin = Perms.ADMIN_EDIT.check(p);
		Gate g = gates().resolve(p, str(c, "gate"), admin);
		if (g == null) return 0;
		if (!admin && !gates().groups().canEdit(g, p.getUUID())) {
			Messages.send(p, "no_permission");
			return 0;
		}
		if (gates().sessions().isEditing(g)) {
			Messages.send(p, "edit.in_use", g.name());
			return 0;
		}
		gates().sessions().startEdit(p, g);
		Messages.send(p, "edit.started", g.name(), g.id());
		return 1;
	}

	private static int setBlock(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		CreateSession s = session(p);
		if (s == null) return 0;
		String id = BlockFilter.normalize(str(c, "block"));
		if (BlockFilter.allowedBlock(id) == null) {
			Messages.send(p, "set.block.not_allowed", id);
			return 0;
		}
		s.block = id;
		Messages.send(p, "set.block.done", id);
		gates().sessions().sync(p);
		return 1;
	}

	private static int setAnimation(CommandContext<CommandSourceStack> c, boolean enabled) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		CreateSession s = session(p);
		if (s == null) return 0;
		var cfg = GatesPlus.get().config().animation();
		if (!cfg.enabled()) {
			Messages.send(p, "set.animation.global_off");
			return 0;
		}
		if (!cfg.allowPlayerToggle() || !Perms.SET_ANIMATION.check(p)) {
			Messages.send(p, "set.animation.not_allowed");
			return 0;
		}
		s.animationEnabled = enabled;
		Messages.send(p, enabled ? "set.animation.on" : "set.animation.off");
		gates().sessions().sync(p);
		return 1;
	}

	private static int setDirection(CommandContext<CommandSourceStack> c, GateDirection d) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		CreateSession s = session(p);
		if (s == null) return 0;
		s.direction = d;
		Messages.send(p, "set.direction." + d.id());
		gates().sessions().sync(p);
		return 1;
	}

	private static int setTrigger(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		CreateSession s = session(p);
		if (s == null) return 0;
		if (!s.enterTriggerMode()) {
			Messages.send(p, "set.trigger.need_corners");
			return 0;
		}
		be.jorisclaes.gatesplus.wand.Wand.give(p);
		Messages.send(p, "set.trigger.mode", GatesPlus.get().config().limits().maxTriggerDistance());
		gates().sessions().sync(p);
		return 1;
	}

	/** Breakable or not; for an existing gate: /gp edit <gate>, this command, /gp confirm. */
	private static int setIndestructible(CommandContext<CommandSourceStack> c, boolean on) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		CreateSession s = session(p);
		if (s == null) return 0;
		if (!Perms.SET_INDESTRUCTIBLE.check(p)) {
			Messages.send(p, "no_permission");
			return 0;
		}
		s.indestructible = on;
		Messages.send(p, on ? "set.indestructible.on" : "set.indestructible.off");
		gates().sessions().sync(p);
		return 1;
	}

	/** Fireproof or not; for an existing gate: /gp edit <gate>, this command, /gp confirm. */
	private static int setFireproof(CommandContext<CommandSourceStack> c, boolean on) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		CreateSession s = session(p);
		if (s == null) return 0;
		if (!Perms.SET_FIREPROOF.check(p)) {
			Messages.send(p, "no_permission");
			return 0;
		}
		s.fireproof = on;
		Messages.send(p, on ? "set.fireproof.on" : "set.fireproof.off");
		gates().sessions().sync(p);
		return 1;
	}

	private static int setRedstone(CommandContext<CommandSourceStack> c, boolean inverted) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		CreateSession s = session(p);
		if (s == null) return 0;
		if (inverted && (!GatesPlus.get().config().redstone().allowInverted() || !Perms.SET_INVERTED.check(p))) {
			Messages.send(p, "set.redstone.not_allowed");
			return 0;
		}
		s.redstoneInverted = inverted;
		Messages.send(p, inverted ? "set.redstone.inverted" : "set.redstone.normal");
		gates().sessions().sync(p);
		return 1;
	}

	private static int setGroup(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		CreateSession s = session(p);
		if (s == null) return 0;
		String name = str(c, "group");
		if (name.equalsIgnoreCase("none")) {
			s.groupId = null;
			Messages.send(p, "set.group.none");
		} else {
			Group g = gates().groups().byName(name);
			if (g == null) {
				Messages.send(p, "group.not_found", name);
				return 0;
			}
			if (!g.contains(p.getUUID()) && !Perms.ADMIN_EDIT.check(p)) {
				Messages.send(p, "set.group.not_member");
				return 0;
			}
			s.groupId = g.id();
			Messages.send(p, "set.group.done", g.name());
		}
		gates().sessions().sync(p);
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		List<Gate> visible = new ArrayList<>();
		for (Gate g : gates().manager().all()) if (gates().groups().canSee(g, p.getUUID())) visible.add(g);
		visible.sort(Comparator.comparingInt(Gate::id));
		if (visible.isEmpty()) {
			Messages.send(p, "list.empty");
			if (Perms.ADMIN_EDIT.check(p) && gates().manager().count() > 0) {
				Messages.send(p, "list.others_exist", gates().manager().count());
			}
			return 1;
		}
		Messages.send(p, "list.header", visible.size(), gates().manager().ownedBy(p.getUUID()).size(),
				GatesPlus.get().config().limits().maxGatesPerPlayer());
		for (Gate g : visible) sendGateLine(p, g);
		return visible.size();
	}

	/** Admins: every gate on the server, with its owner. */
	private static int listAll(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		List<Gate> all = new ArrayList<>(gates().manager().all());
		all.sort(Comparator.comparingInt(Gate::id));
		Messages.send(p, "list.header_all", all.size());
		for (Gate g : all) {
			sendGateLine(p, g);
			Messages.send(p, "list.owner", g.ownerName());
		}
		return all.size();
	}

	static void sendGateLine(ServerPlayer p, Gate g) {
		String group = gates().groups().scopeLabel(g);
		// name green when the gate works, red when it doesn't (broken, incomplete, trigger missing)
		String name = (g.status() == be.jorisclaes.gatesplus.gate.GateStatus.OK ? "§a" : "§c") + g.name();
		Messages.send(p, "list.line", g.id(), name, group == null ? Messages.raw(p, "list.personal") : group,
				g.minX() + " " + g.minY() + " " + g.minZ(), g.block(),
				Messages.raw(p, g.isOpen() ? "panel.open" : "panel.closed"), InfoPanel.statusLabel(p, g.status()));
	}

	/** Gives one top-left (corner 1) and one bottom-right (corner 2) marker block. */
	private static int getMarkers(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		// marker blocks are a selection tool: only while creating or editing a gate
		if (gates().sessions().get(p) == null) {
			Messages.send(p, "get.marker.no_session");
			return 0;
		}
		for (var item : new net.minecraft.world.item.Item[] {MarkerBlocks.TOP_LEFT_ITEM, MarkerBlocks.BOTTOM_RIGHT_ITEM}) {
			ItemStack stack = new ItemStack(item);
			if (!p.getInventory().add(stack) || !stack.isEmpty()) ReturnChestTracker.drop(p.level(), p, stack);
		}
		Messages.send(p, "get.marker.done");
		return 1;
	}

	private static int listBlocks(CommandContext<CommandSourceStack> c) {
		ServerPlayer p = c.getSource().getPlayer();
		List<String> ids = BlockFilter.allowedIds();
		c.getSource().sendSuccess(() -> Messages.get(p, "list.blocks", ids.size(), String.join(", ", ids)), false);
		return ids.size();
	}

	private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		Gate g = gates().resolve(p, str(c, "gate"), Perms.ADMIN_EDIT.check(p));
		if (g == null) return 0;
		Messages.send(p, "info.header", g.name());
		for (String row : InfoPanel.forGate(p, g)) p.sendSystemMessage(Component.literal("§7" + row.replace("\t", ": §f")));
		return 1;
	}

	private static int remove(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		boolean admin = Perms.ADMIN_REMOVE.check(p);
		Gate g = gates().resolve(p, str(c, "gate"), admin);
		if (g == null) return 0;
		if (!admin && !gates().groups().canRemove(g, p.getUUID())) {
			Messages.send(p, "rm.not_owner", g.name());
			return 0;
		}
		gates().remove(p, g);
		Messages.send(p, "rm.done", g.name(), g.id());
		return 1;
	}

	/** /gp repair without a name: repairs the gate of the player's own edit session. */
	private static int repairSession(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		CreateSession s = gates().sessions().get(p);
		if (s == null) {
			Messages.send(p, "repair.need_gate");
			return 0;
		}
		if (s.type() == CreateSession.Type.CREATE) {
			Messages.send(p, "repair.new_gate");
			return 0;
		}
		Gate g = gates().manager().byId(s.gateId());
		if (g == null) {
			Messages.send(p, "edit.gone");
			return 0;
		}
		return doRepair(p, g);
	}

	private static int repair(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		Gate g = gates().resolve(p, str(c, "gate"), Perms.ADMIN_EDIT.check(p));
		return g == null ? 0 : doRepair(p, g);
	}

	private static int doRepair(ServerPlayer p, Gate g) {
		if (!Perms.ADMIN_EDIT.check(p) && !gates().groups().canEdit(g, p.getUUID())) {
			Messages.send(p, "no_permission");
			return 0;
		}
		// a gate being edited may only be repaired by the player editing it
		CreateSession own = gates().sessions().get(p);
		boolean ownEdit = own != null && own.type() == CreateSession.Type.EDIT && own.gateId() == g.id();
		if (gates().isLocked(g) && !ownEdit) {
			Messages.send(p, "edit.in_use", g.name());
			return 0;
		}
		gates().repair(p, g);
		if (own != null) gates().sessions().sync(p); // refresh "blocks x / y" in the info panel
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> c) {
		List<String> warnings = GatesPlus.get().reload();
		ServerPlayer p = c.getSource().getPlayer();
		c.getSource().sendSuccess(() -> Messages.get(p, "reload.done", BlockFilter.allowedIds().size()), true);
		for (String w : warnings) c.getSource().sendSuccess(() -> Component.literal("§e⚠ " + w), false);
		return 1;
	}

	private static int devstats(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		GatesPlus.get().devStats().print(p);
		if (Network.hasClientMod(p)) {
			boolean on = GatesPlus.get().devStats().toggle(p);
			Messages.send(p, on ? "dev.panel_on" : "dev.panel_off");
		}
		return 1;
	}

	static Map.Entry<UUID, String> memberByName(Group g, String name) {
		if (g.ownerName().equalsIgnoreCase(name)) return Map.entry(g.ownerUuid(), g.ownerName());
		for (var e : g.members().entrySet()) if (e.getValue().equalsIgnoreCase(name)) return e;
		return null;
	}

	static ServerPlayer onlinePlayer(CommandContext<CommandSourceStack> c, String arg) throws CommandSyntaxException {
		return EntityArgument.getPlayer(c, arg);
	}
}
