package be.jorisclaes.gatesplus.group;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import be.jorisclaes.gatesplus.gate.Gate;

/** In-memory groups plus the permission rules for gates. */
public final class GroupManager {
	private final Map<Integer, Group> byId = new HashMap<>();
	private final Map<String, Group> byName = new HashMap<>();

	public void add(Group g) {
		byId.put(g.id(), g);
		byName.put(g.name().toLowerCase(Locale.ROOT), g);
	}

	public void remove(Group g) {
		byId.remove(g.id());
		byName.remove(g.name().toLowerCase(Locale.ROOT));
	}

	public void clear() {
		byId.clear();
		byName.clear();
	}

	public Group byId(Integer id) {
		return id == null ? null : byId.get(id);
	}

	public Group byName(String name) {
		return byName.get(name.toLowerCase(Locale.ROOT));
	}

	public Collection<Group> all() {
		return byId.values();
	}

	/** Groups the player owns or is a member of. */
	public List<Group> groupsOf(UUID player) {
		List<Group> out = new ArrayList<>();
		for (Group g : byId.values()) if (g.contains(player)) out.add(g);
		out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
		return out;
	}

	public long ownedCount(UUID player) {
		return byId.values().stream().filter(g -> g.isOwner(player)).count();
	}

	/** Owner, or member of the gate's group. Admin overrides are checked by the caller. */
	public boolean canEdit(Gate gate, UUID player) {
		if (gate.ownerUuid().equals(player)) return true;
		Group g = byId(gate.groupId());
		return g != null && g.contains(player);
	}

	/** Only the gate owner may remove it (admins checked by the caller). */
	public boolean canRemove(Gate gate, UUID player) {
		return gate.ownerUuid().equals(player);
	}

	/** Gates a player can see: own gates and gates of their groups. */
	public boolean canSee(Gate gate, UUID player) {
		return canEdit(gate, player);
	}

	public String scopeLabel(Gate gate) {
		Group g = byId(gate.groupId());
		return g != null ? g.name() : null;
	}
}
