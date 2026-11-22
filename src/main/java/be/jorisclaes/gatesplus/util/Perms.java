package be.jorisclaes.gatesplus.util;

import java.util.function.Predicate;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.Entity;

import me.lucko.fabric.api.permissions.v0.Permissions;

/**
 * Permission nodes (LuckPerms compatible via fabric-permissions-api). Without a permission
 * plugin every node falls back to its default: everyone, or operator level 2.
 */
public enum Perms {
	USE("gatesplus.use", 0),
	REMOVE("gatesplus.remove", 0),
	GROUP("gatesplus.group", 0),
	GET_MARKER("gatesplus.get.marker", 0),
	SET_INVERTED("gatesplus.set.inverted", 0),
	SET_ANIMATION("gatesplus.set.animation", 0),
	SET_INDESTRUCTIBLE("gatesplus.set.indestructible", 0),
	SET_FIREPROOF("gatesplus.set.fireproof", 0),
	BYPASS_LIMITS("gatesplus.bypass.limits", 2),
	ADMIN_EDIT("gatesplus.admin.edit", 2),
	ADMIN_REMOVE("gatesplus.admin.remove", 2),
	ADMIN_BREAK("gatesplus.admin.break", 2),
	ADMIN_RELOAD("gatesplus.admin.reload", 2),
	ADMIN_DEVSTATS("gatesplus.admin.devstats", 2);

	public final String node;
	public final int defaultLevel;

	Perms(String node, int defaultLevel) {
		this.node = node;
		this.defaultLevel = defaultLevel;
	}

	public boolean check(CommandSourceStack source) {
		return Permissions.check(source, node, defaultLevel);
	}

	public boolean check(Entity entity) {
		return Permissions.check(entity, node, defaultLevel);
	}

	public Predicate<CommandSourceStack> require() {
		return Permissions.require(node, defaultLevel);
	}
}
