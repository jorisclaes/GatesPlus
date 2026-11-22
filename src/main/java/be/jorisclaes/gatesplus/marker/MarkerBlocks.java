package be.jorisclaes.gatesplus.marker;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import be.jorisclaes.gatesplus.GatesPlus;
import be.jorisclaes.gatesplus.session.CreateSession;

/**
 * The two corner marker blocks: one for the top-left corner (corner 1, gold) and one for the
 * bottom-right corner (corner 2, red). Clicking one with the wand sets that corner on the marker
 * itself; markers inside a gate disappear when the gate is built. Only obtainable with
 * /gp get markerblock (no recipe, no creative tab) and breaking them drops nothing.
 */
public final class MarkerBlocks {
	public static final Block TOP_LEFT = block("marker_top_left", MapColor.GOLD);
	public static final Block BOTTOM_RIGHT = block("marker_bottom_right", MapColor.COLOR_RED);
	public static final Item TOP_LEFT_ITEM = item("marker_top_left", TOP_LEFT);
	public static final Item BOTTOM_RIGHT_ITEM = item("marker_bottom_right", BOTTOM_RIGHT);

	private MarkerBlocks() {}

	private static Block block(String name, MapColor color) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, GatesPlus.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, new MarkerBlock(BlockBehaviour.Properties.of()
				.setId(key)
				.mapColor(color)
				.lightLevel(state -> 10)
				.instabreak()
				.sound(SoundType.GLASS)
				.noLootTable()));
	}

	private static Item item(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, GatesPlus.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	/** Forces class loading (and thus registration) during mod init. */
	public static void register() {}

	public static boolean isMarkerItem(net.minecraft.world.item.ItemStack stack) {
		return stack != null && (stack.getItem() == TOP_LEFT_ITEM || stack.getItem() == BOTTOM_RIGHT_ITEM);
	}

	/** Removes every marker item from the inventory and the cursor (end of a session). */
	public static void removeItems(net.minecraft.server.level.ServerPlayer player) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (isMarkerItem(inv.getItem(i))) inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
		}
		if (isMarkerItem(player.containerMenu.getCarried())) {
			player.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
		}
		player.containerMenu.broadcastChanges();
	}

	public static boolean isMarker(BlockState state) {
		return state.is(TOP_LEFT) || state.is(BOTTOM_RIGHT);
	}

	/** Which corner a marker block stands for, or null when the state is not a marker. */
	public static CreateSession.Point cornerOf(BlockState state) {
		if (state.is(TOP_LEFT)) return CreateSession.Point.CORNER1;
		if (state.is(BOTTOM_RIGHT)) return CreateSession.Point.CORNER2;
		return null;
	}
}
