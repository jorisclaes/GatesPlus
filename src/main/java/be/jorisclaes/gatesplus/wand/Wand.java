package be.jorisclaes.gatesplus.wand;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import be.jorisclaes.gatesplus.util.Messages;

/** The selection tool: a wooden axe tagged with custom data so a normal axe does nothing. */
public final class Wand {
	private static final String TAG = "gatesplus_wand";

	private Wand() {}

	public static ItemStack create(ServerPlayer player) {
		ItemStack stack = new ItemStack(Items.WOODEN_AXE);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(TAG, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(Messages.raw(player, "wand.name")));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	public static boolean isWand(ItemStack stack) {
		if (stack == null || stack.isEmpty() || stack.getItem() != Items.WOODEN_AXE) return false;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(TAG, false);
	}

	/** Items that only exist during a session and may not leave the player's inventory. */
	public static boolean isSessionTool(ItemStack stack) {
		return isWand(stack) || be.jorisclaes.gatesplus.marker.MarkerBlocks.isMarkerItem(stack);
	}

	public static boolean has(ServerPlayer player) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) if (isWand(inv.getItem(i))) return true;
		return isWand(player.containerMenu.getCarried());
	}

	/** Gives the wand (selected hotbar slot if empty, otherwise anywhere) unless the player has one. */
	public static void give(ServerPlayer player) {
		if (has(player)) return;
		ItemStack wand = create(player);
		Inventory inv = player.getInventory();
		if (inv.getSelectedItem().isEmpty()) {
			inv.setItem(inv.getSelectedSlot(), wand);
		} else if (!inv.add(wand)) {
			Messages.send(player, "wand.inventory_full");
		}
	}

	/** Removes every wand from the inventory and the cursor. */
	public static void removeAll(ServerPlayer player) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (isWand(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
		}
		if (isWand(player.containerMenu.getCarried())) player.containerMenu.setCarried(ItemStack.EMPTY);
		player.containerMenu.broadcastChanges();
	}
}
