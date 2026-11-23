package be.jorisclaes.gatesplus.marker;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import be.jorisclaes.gatesplus.GatesPlus;
import be.jorisclaes.gatesplus.session.CreateSession;
import be.jorisclaes.gatesplus.util.Messages;

/**
 * The two corner markers: top-left (corner 1, gold) and bottom-right (corner 2, red). Two modes,
 * chosen with config {@code session.custom-marker-blocks} at startup:
 * <ul>
 * <li>custom: own registered blocks with a texture. Registered content makes the client mod
 * mandatory for every player (Fabric registry sync refuses vanilla clients).</li>
 * <li>vanilla (default): yellow / red concrete items tagged with custom data. A placed vanilla
 * marker is only recognised by its position (the session tracks it) and gets a floating label.</li>
 * </ul>
 * Only obtainable with /gp get markerblock; breaking them drops nothing.
 */
public final class MarkerBlocks {
	private static final String TAG = "gatesplus_marker";

	private static boolean custom;
	private static Block topLeft;
	private static Block bottomRight;
	private static Item topLeftItem;
	private static Item bottomRightItem;

	private MarkerBlocks() {}

	/**
	 * Registers the custom blocks when the config asks for them. A physical client always registers
	 * them: in singleplayer the integrated server may use them, and a modded client joining a server
	 * that has them needs the same registry entries.
	 */
	public static void register() {
		custom = GatesPlus.get().config().session().customMarkerBlocks();
		if (!custom && FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
		topLeft = block("marker_top_left", MapColor.GOLD);
		bottomRight = block("marker_bottom_right", MapColor.COLOR_RED);
		topLeftItem = item("marker_top_left", topLeft);
		bottomRightItem = item("marker_bottom_right", bottomRight);
	}

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

	/** True when the own (textured) marker blocks are used; false = vanilla blocks + label. */
	public static boolean custom() {
		return custom;
	}

	/** The block a marker for this corner is in the world. */
	public static Block blockFor(CreateSession.Point corner) {
		return custom ? customBlockFor(corner) : vanillaBlockFor(corner);
	}

	/** The vanilla marker block (yellow / red concrete). */
	public static Block vanillaBlockFor(CreateSession.Point corner) {
		return corner == CreateSession.Point.CORNER1 ? Blocks.CONCRETE.yellow() : Blocks.CONCRETE.red();
	}

	/** The own textured marker block, or null when it is not registered (dedicated server, vanilla mode). */
	public static Block customBlockFor(CreateSession.Point corner) {
		return corner == CreateSession.Point.CORNER1 ? topLeft : bottomRight;
	}

	/** A fresh marker item for this corner. */
	public static ItemStack create(ServerPlayer player, CreateSession.Point corner) {
		boolean one = corner == CreateSession.Point.CORNER1;
		if (custom) return new ItemStack(one ? topLeftItem : bottomRightItem);
		ItemStack stack = new ItemStack(blockFor(corner).asItem());
		CompoundTag tag = new CompoundTag();
		tag.putInt(TAG, one ? 1 : 2);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.CUSTOM_NAME,
				Component.literal(Messages.raw(player, one ? "get.marker.item1" : "get.marker.item2")));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	/** Which corner a marker item stands for, or null when it is no marker item. */
	public static CreateSession.Point cornerOfItem(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		if (topLeftItem != null && stack.getItem() == topLeftItem) return CreateSession.Point.CORNER1;
		if (bottomRightItem != null && stack.getItem() == bottomRightItem) return CreateSession.Point.CORNER2;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return null;
		return switch (data.copyTag().getIntOr(TAG, 0)) {
			case 1 -> CreateSession.Point.CORNER1;
			case 2 -> CreateSession.Point.CORNER2;
			default -> null;
		};
	}

	public static boolean isMarkerItem(ItemStack stack) {
		return cornerOfItem(stack) != null;
	}

	/** Removes every marker item from the inventory and the cursor (end of a session). */
	public static void removeItems(ServerPlayer player) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (isMarkerItem(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
		}
		if (isMarkerItem(player.containerMenu.getCarried())) {
			player.containerMenu.setCarried(ItemStack.EMPTY);
		}
		player.containerMenu.broadcastChanges();
	}

	/** True for an own (custom) marker block; vanilla markers can only be found by position. */
	public static boolean isCustomMarker(BlockState state) {
		return topLeft != null && (state.is(topLeft) || state.is(bottomRight));
	}

	/** Corner of an own (custom) marker block, or null. */
	public static CreateSession.Point cornerOfCustom(BlockState state) {
		if (topLeft == null) return null;
		if (state.is(topLeft)) return CreateSession.Point.CORNER1;
		if (state.is(bottomRight)) return CreateSession.Point.CORNER2;
		return null;
	}
}
