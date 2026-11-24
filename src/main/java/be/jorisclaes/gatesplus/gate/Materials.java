package be.jorisclaes.gatesplus.gate;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import be.jorisclaes.gatesplus.GatesPlus;
import be.jorisclaes.gatesplus.config.GatesPlusConfig;
import be.jorisclaes.gatesplus.wand.Wand;

/** Paying for gate blocks from the inventory and handing them back. */
public final class Materials {
	private Materials() {}

	/** Result of investing blocks: how many were placed and how many of those were paid. */
	public record Investment(int placed, int paid) {}

	/**
	 * Takes up to {@code wanted} blocks. Creative players (with creative-free) and servers without
	 * require-blocks get them for free but unpaid, so nothing is refunded later.
	 */
	public static Investment invest(ServerPlayer player, Block block, int wanted) {
		GatesPlusConfig.Materials cfg = GatesPlus.get().config().materials();
		if (wanted <= 0) return new Investment(0, 0);
		if (!cfg.requireBlocks() || (player.isCreative() && cfg.creativeFree())) {
			return new Investment(wanted, 0);
		}
		int taken = take(player, block.asItem(), wanted);
		return new Investment(taken, taken);
	}

	public static int count(ServerPlayer player, Item item) {
		Inventory inv = player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.getItem() == item && s.getComponentsPatch().isEmpty()) n += s.getCount();
		}
		return n;
	}

	public static int take(ServerPlayer player, Item item, int max) {
		Inventory inv = player.getInventory();
		int taken = 0;
		for (int i = 0; i < inv.getContainerSize() && taken < max; i++) {
			ItemStack s = inv.getItem(i);
			if (s.getItem() != item || Wand.isWand(s) || !s.getComponentsPatch().isEmpty()) continue;
			int n = Math.min(s.getCount(), max - taken);
			s.shrink(n);
			taken += n;
		}
		if (taken > 0) inv.setChanged();
		return taken;
	}

	/**
	 * Gives back {@code count} blocks: into the inventory (rest dropped), or into return chests
	 * when more than chest-threshold items come back.
	 */
	public static void refund(ServerLevel level, ServerPlayer player, Block block, int count) {
		GatesPlusConfig.Materials cfg = GatesPlus.get().config().materials();
		if (count <= 0 || !cfg.returnOnRemove()) return;
		Item item = block.asItem();
		List<ItemStack> stacks = new ArrayList<>();
		int max = new ItemStack(item).getMaxStackSize();
		for (int left = count; left > 0; left -= max) stacks.add(new ItemStack(item, Math.min(max, left)));

		if (count > cfg.chestThreshold()) {
			GatesPlus.get().gates().returnChests().give(level, player, stacks);
			return;
		}
		for (ItemStack s : stacks) {
			if (!player.getInventory().add(s) || !s.isEmpty()) ReturnChestTracker.drop(level, player, s);
		}
	}
}
