package be.jorisclaes.gatesplus.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

import be.jorisclaes.gatesplus.GatesPlus;
import be.jorisclaes.gatesplus.gate.Gate;
import be.jorisclaes.gatesplus.gate.GateService;
import be.jorisclaes.gatesplus.group.Group;
import be.jorisclaes.gatesplus.util.Messages;
import be.jorisclaes.gatesplus.util.Perms;

/** /gp group ... */
final class GroupCommands {
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

	private GroupCommands() {}

	private static GateService gates() {
		return GatesPlus.get().gates();
	}

	private static final SuggestionProvider<CommandSourceStack> MY_GROUPS = (c, b) -> {
		List<String> names = new ArrayList<>();
		ServerPlayer p = c.getSource().getPlayer();
		if (p != null) for (Group g : gates().groups().groupsOf(p.getUUID())) names.add(g.name());
		return SharedSuggestionProvider.suggest(names, b);
	};

	private static final SuggestionProvider<CommandSourceStack> OWNED_GROUPS = (c, b) -> {
		List<String> names = new ArrayList<>();
		ServerPlayer p = c.getSource().getPlayer();
		if (p != null) {
			boolean admin = Perms.ADMIN_EDIT.check(p);
			for (Group g : gates().groups().all()) if (admin || g.isOwner(p.getUUID())) names.add(g.name());
		}
		return SharedSuggestionProvider.suggest(names, b);
	};

	/** Online players that are not in the group yet (so never yourself, no @-selectors). */
	private static final SuggestionProvider<CommandSourceStack> ADDABLE_PLAYERS = (c, b) -> {
		Group g = gates().groups().byName(StringArgumentType.getString(c, "group"));
		List<String> names = new ArrayList<>();
		for (ServerPlayer online : c.getSource().getServer().getPlayerList().getPlayers()) {
			if (g != null && g.contains(online.getUUID())) continue;
			if (c.getSource().getPlayer() == online) continue;
			names.add(online.getGameProfile().name());
		}
		return SharedSuggestionProvider.suggest(names, b);
	};

	private static final SuggestionProvider<CommandSourceStack> MEMBERS = (c, b) -> {
		Group g = gates().groups().byName(StringArgumentType.getString(c, "group"));
		return SharedSuggestionProvider.suggest(g == null ? List.of() : g.members().values(), b);
	};

	static LiteralArgumentBuilder<CommandSourceStack> build() {
		return literal("group").requires(Perms.GROUP.require())
				.then(literal("create").then(argument("group", StringArgumentType.word()).executes(GroupCommands::create)))
				.then(literal("delete").then(argument("group", StringArgumentType.word()).suggests(OWNED_GROUPS)
						.executes(GroupCommands::delete)))
				.then(literal("add").then(argument("group", StringArgumentType.word()).suggests(OWNED_GROUPS)
						.then(argument("player", StringArgumentType.word()).suggests(ADDABLE_PLAYERS).executes(GroupCommands::add))))
				.then(literal("remove").then(argument("group", StringArgumentType.word()).suggests(OWNED_GROUPS)
						.then(argument("player", StringArgumentType.word()).suggests(MEMBERS).executes(GroupCommands::removeMember))))
				.then(literal("list").executes(GroupCommands::list))
				.then(literal("members").then(argument("group", StringArgumentType.word()).suggests(MY_GROUPS)
						.executes(GroupCommands::members)))
				.then(literal("gates").then(argument("group", StringArgumentType.word()).suggests(MY_GROUPS)
						.executes(GroupCommands::gatesOf)))
				.then(literal("info").then(argument("group", StringArgumentType.word()).suggests(MY_GROUPS)
						.executes(GroupCommands::info)));
	}

	/** Group the player may view (member or admin); sends an error otherwise. */
	private static Group viewable(ServerPlayer p, String name) {
		Group g = gates().groups().byName(name);
		if (g == null || (!g.contains(p.getUUID()) && !Perms.ADMIN_EDIT.check(p))) {
			Messages.send(p, "group.not_found", name);
			return null;
		}
		return g;
	}

	/** Group the player may manage (owner or admin). */
	private static Group manageable(ServerPlayer p, String name) {
		Group g = gates().groups().byName(name);
		if (g == null) {
			Messages.send(p, "group.not_found", name);
			return null;
		}
		if (!g.isOwner(p.getUUID()) && !Perms.ADMIN_EDIT.check(p)) {
			Messages.send(p, "group.not_owner", g.name());
			return null;
		}
		return g;
	}

	private static int create(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = GpCommand.player(c);
		String name = GpCommand.str(c, "group");
		if (!NAME.matcher(name).matches() || name.equalsIgnoreCase("none") || name.matches("\\d+")) {
			Messages.send(p, "group.bad_name", name);
			return 0;
		}
		if (gates().groups().byName(name) != null) {
			Messages.send(p, "group.exists", name);
			return 0;
		}
		int max = GatesPlus.get().config().limits().maxGroupsPerPlayer();
		if (!Perms.BYPASS_LIMITS.check(p) && gates().groups().ownedCount(p.getUUID()) >= max) {
			Messages.send(p, "group.max", max);
			return 0;
		}
		Group g = new Group(0, name, p.getUUID(), p.getGameProfile().name(), System.currentTimeMillis());
		gates().groups().add(g); // reserve the name right away
		GatesPlus.get().storage().call(s -> s.insertGroup(g)).whenComplete((id, err) -> p.level().getServer().execute(() -> {
			if (err != null) {
				gates().groups().remove(g);
				Messages.send(p, "storage.error");
				return;
			}
			gates().groups().remove(g);
			g.setId(id);
			gates().groups().add(g);
			Messages.send(p, "group.created", g.name());
		}));
		return 1;
	}

	private static int delete(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = GpCommand.player(c);
		Group g = manageable(p, GpCommand.str(c, "group"));
		if (g == null) return 0;
		if (g.id() == 0) {
			Messages.send(p, "storage.busy");
			return 0;
		}
		gates().detachGroup(g);
		gates().groups().remove(g);
		int id = g.id();
		GatesPlus.get().storage().run(s -> s.deleteGroup(id));
		Messages.send(p, "group.deleted", g.name());
		return 1;
	}

	private static int add(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = GpCommand.player(c);
		Group g = manageable(p, GpCommand.str(c, "group"));
		if (g == null) return 0;
		String targetName = GpCommand.str(c, "player");
		ServerPlayer target = c.getSource().getServer().getPlayerList().getPlayerByName(targetName);
		if (target == null) {
			Messages.send(p, "group.player_not_online", targetName);
			return 0;
		}
		if (g.contains(target.getUUID())) {
			Messages.send(p, "group.already_member", target.getGameProfile().name(), g.name());
			return 0;
		}
		if (g.members().size() + 1 >= GatesPlus.get().config().limits().maxMembersPerGroup() && !Perms.BYPASS_LIMITS.check(p)) {
			Messages.send(p, "group.full", GatesPlus.get().config().limits().maxMembersPerGroup());
			return 0;
		}
		if (g.id() == 0) {
			Messages.send(p, "storage.busy");
			return 0;
		}
		String name = target.getGameProfile().name();
		g.putMember(target.getUUID(), name);
		int id = g.id();
		UUID uuid = target.getUUID();
		GatesPlus.get().storage().run(s -> s.addMember(id, uuid, name));
		Messages.send(p, "group.added", name, g.name());
		if (target != p) Messages.send(target, "group.you_were_added", g.name(), p.getGameProfile().name());
		return 1;
	}

	private static int removeMember(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = GpCommand.player(c);
		Group g = manageable(p, GpCommand.str(c, "group"));
		if (g == null) return 0;
		String name = GpCommand.str(c, "player");
		Map.Entry<UUID, String> member = GpCommand.memberByName(g, name);
		if (member == null || g.isOwner(member.getKey())) {
			Messages.send(p, "group.not_member", name, g.name());
			return 0;
		}
		g.removeMember(member.getKey());
		int id = g.id();
		UUID uuid = member.getKey();
		GatesPlus.get().storage().run(s -> s.removeMember(id, uuid));
		Messages.send(p, "group.removed", member.getValue(), g.name());
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = GpCommand.player(c);
		List<Group> groups = gates().groups().groupsOf(p.getUUID());
		if (groups.isEmpty()) {
			Messages.send(p, "group.list_empty");
			return 0;
		}
		Messages.send(p, "group.list_header", groups.size());
		for (Group g : groups) {
			Messages.send(p, "group.list_line", g.name(), g.ownerName(), g.members().size() + 1,
					gates().manager().inGroup(g.id()).size());
		}
		return groups.size();
	}

	private static int members(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = GpCommand.player(c);
		Group g = viewable(p, GpCommand.str(c, "group"));
		if (g == null) return 0;
		sendMembers(p, g);
		return 1;
	}

	private static void sendMembers(ServerPlayer p, Group g) {
		var players = p.level().getServer().getPlayerList();
		Messages.send(p, "group.members_header", g.name(), g.members().size() + 1);
		Messages.send(p, "group.member_owner", g.ownerName(),
				Messages.raw(p, players.getPlayer(g.ownerUuid()) != null ? "group.online" : "group.offline"));
		g.members().entrySet().stream().sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
				.forEach(e -> Messages.send(p, "group.member_line", e.getValue(),
						Messages.raw(p, players.getPlayer(e.getKey()) != null ? "group.online" : "group.offline")));
	}

	private static int gatesOf(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = GpCommand.player(c);
		Group g = viewable(p, GpCommand.str(c, "group"));
		if (g == null) return 0;
		sendGates(p, g);
		return 1;
	}

	private static void sendGates(ServerPlayer p, Group g) {
		List<Gate> list = gates().manager().inGroup(g.id());
		list.sort(Comparator.comparingInt(Gate::id));
		Messages.send(p, "group.gates_header", g.name(), list.size());
		if (list.isEmpty()) Messages.send(p, "group.gates_empty");
		for (Gate gate : list) GpCommand.sendGateLine(p, gate);
	}

	private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = GpCommand.player(c);
		Group g = viewable(p, GpCommand.str(c, "group"));
		if (g == null) return 0;
		Messages.send(p, "group.info_header", g.name(), g.ownerName());
		sendMembers(p, g);
		sendGates(p, g);
		return 1;
	}
}
