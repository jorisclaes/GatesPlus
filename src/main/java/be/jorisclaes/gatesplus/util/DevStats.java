package be.jorisclaes.gatesplus.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import be.jorisclaes.gatesplus.GatesPlus;
import be.jorisclaes.gatesplus.gate.Gate;
import be.jorisclaes.gatesplus.gate.GateService;
import be.jorisclaes.gatesplus.gate.GateStatus;
import be.jorisclaes.gatesplus.network.Network;
import be.jorisclaes.gatesplus.network.PanelPayload;
import be.jorisclaes.gatesplus.redstone.GateTicker;
import be.jorisclaes.gatesplus.storage.AsyncStorage;

/** /gp devstats: live numbers for admins, as a HUD panel (client mod) or in chat. */
public final class DevStats {
	private final Set<UUID> viewers = new HashSet<>();

	/** @return true when the panel is now on */
	public boolean toggle(ServerPlayer player) {
		if (viewers.remove(player.getUUID())) {
			Network.send(player, PanelPayload.hide(PanelPayload.DEVSTATS));
			return false;
		}
		viewers.add(player.getUUID());
		return true;
	}

	public void remove(UUID player) {
		viewers.remove(player);
	}

	public List<String> rows(ServerPlayer p) {
		GateService s = GatesPlus.get().gates();
		GateTicker t = GatesPlus.get().ticker();
		AsyncStorage st = GatesPlus.get().storage();
		int loaded = 0, open = 0, broken = 0;
		for (Gate g : s.manager().all()) {
			if (g.isOpen()) open++;
			if (g.status() != GateStatus.OK) broken++;
			var level = s.level(g);
			if (level != null && level.hasChunk(g.minX() >> 4, g.minZ() >> 4)) loaded++;
		}
		List<String> rows = new ArrayList<>();
		rows.add(Messages.raw(p, "dev.gates") + "\t§f" + s.manager().count() + " §7| " + Messages.raw(p, "dev.loaded") + " §f"
				+ loaded + " §7| " + Messages.raw(p, "dev.active") + " §f" + s.manager().activeCount());
		rows.add(Messages.raw(p, "dev.state") + "\t§f" + open + " " + Messages.raw(p, "panel.open") + " §7| §c" + broken + " "
				+ Messages.raw(p, "dev.not_ok"));
		rows.add(Messages.raw(p, "dev.ticker") + "\t§f" + String.format("%.3f", t.avgMs()) + " ms §7avg | §f"
				+ String.format("%.3f", t.maxMs()) + " ms §7max");
		rows.add(Messages.raw(p, "dev.checks") + "\t§f" + t.checksPerSecond() + "/s");
		rows.add(Messages.raw(p, "dev.animations") + "\t§f" + s.animator().runningCount() + " §7| displays §f"
				+ s.animator().displayCount());
		rows.add(Messages.raw(p, "dev.sessions") + "\t§f" + s.sessions().count() + " §7| " + Messages.raw(p, "dev.groups")
				+ " §f" + s.groups().all().size() + " §7| " + Messages.raw(p, "dev.chests") + " §f" + s.returnChests().count());
		rows.add(Messages.raw(p, "dev.storage") + "\t§f" + st.describe());
		rows.add(Messages.raw(p, "dev.queue") + "\t§f" + st.pendingCount() + " §7| §f"
				+ String.format("%.2f", st.averageMillis()) + " ms §7avg");
		if (st.lastError() != null) rows.add(Messages.raw(p, "dev.last_error") + "\t§c" + st.lastError());
		rows.add(Messages.raw(p, "dev.index") + "\t§f" + s.manager().chunkBucketCount() + " §7buckets | max §f"
				+ s.manager().largestChunkBucket());
		return rows;
	}

	/** Chat dump (also used without the client mod). */
	public void print(ServerPlayer p) {
		p.sendSystemMessage(Component.literal(Messages.raw(p, "dev.title")));
		for (String row : rows(p)) p.sendSystemMessage(Component.literal("§7" + row.replace("\t", ": ")));
	}

	/** Once per second. */
	public void tick(MinecraftServer server) {
		for (UUID uuid : new ArrayList<>(viewers)) {
			ServerPlayer p = server.getPlayerList().getPlayer(uuid);
			if (p == null) {
				viewers.remove(uuid);
				continue;
			}
			if (!Network.send(p, new PanelPayload(PanelPayload.DEVSTATS, Messages.raw(p, "dev.title"), rows(p)))) {
				viewers.remove(uuid); // no client mod: chat dump only
			}
		}
	}
}
