package be.jorisclaes.gatesplus.wand;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Decides whether an inventory click would move the wand out of the player's own inventory. */
public final class WandGuard {
	private static final int OUTSIDE = -999;

	private WandGuard() {}

	public static boolean blocksClick(AbstractContainerMenu menu, int slotIndex, int button, ContainerInput input,
			ServerPlayer player) {
		Inventory inv = player.getInventory();
		ItemStack carried = menu.getCarried();
		Slot slot = slotIndex >= 0 && slotIndex < menu.slots.size() ? menu.getSlot(slotIndex) : null;
		boolean slotIsPlayer = slot != null && slot.container == inv;
		boolean ownMenu = menu == player.inventoryMenu;

		if (input == ContainerInput.THROW) {
			return slot != null && Wand.isWand(slot.getItem());
		}
		if (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_CRAFT) {
			if (!Wand.isWand(carried)) return false;
			if (slotIndex == OUTSIDE || input == ContainerInput.QUICK_CRAFT) return true; // drop / drag
			return slot != null && !slotIsPlayer;
		}
		if (input == ContainerInput.QUICK_MOVE) {
			return slot != null && Wand.isWand(slot.getItem()) && !ownMenu;
		}
		if (input == ContainerInput.SWAP) {
			ItemStack swapped = button >= 0 && button < inv.getContainerSize() ? inv.getItem(button) : ItemStack.EMPTY;
			if (button == 40) swapped = player.getOffhandItem();
			return slot != null && !slotIsPlayer && (Wand.isWand(swapped) || Wand.isWand(slot.getItem()));
		}
		return false;
	}
}
