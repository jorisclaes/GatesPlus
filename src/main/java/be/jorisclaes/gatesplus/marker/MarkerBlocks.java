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

/**
 * The marker block: lets players mark gate corners in mid-air. Clicking it with the wand selects
 * the marker itself; markers inside a gate disappear when the gate is built. Only obtainable with
 * /gp get markerblock (no recipe, no creative tab) and breaking it drops nothing.
 */
public final class MarkerBlocks {
	private static final ResourceKey<Block> BLOCK_KEY = ResourceKey.create(Registries.BLOCK, GatesPlus.id("marker"));
	private static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, GatesPlus.id("marker"));

	public static final Block MARKER = Registry.register(BuiltInRegistries.BLOCK, BLOCK_KEY,
			new Block(BlockBehaviour.Properties.of()
					.setId(BLOCK_KEY)
					.mapColor(MapColor.COLOR_LIGHT_GREEN)
					.lightLevel(state -> 10)
					.instabreak()
					.sound(SoundType.GLASS)
					.noLootTable()));

	public static final Item MARKER_ITEM = Registry.register(BuiltInRegistries.ITEM, ITEM_KEY,
			new BlockItem(MARKER, new Item.Properties().setId(ITEM_KEY).useBlockDescriptionPrefix()));

	private MarkerBlocks() {}

	/** Forces class loading (and thus registration) during mod init. */
	public static void register() {}

	public static boolean isMarker(BlockState state) {
		return state.is(MARKER);
	}
}
