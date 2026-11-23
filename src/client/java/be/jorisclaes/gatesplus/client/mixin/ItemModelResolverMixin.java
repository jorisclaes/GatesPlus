package be.jorisclaes.gatesplus.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import be.jorisclaes.gatesplus.GatesPlus;
import be.jorisclaes.gatesplus.marker.MarkerBlocks;
import be.jorisclaes.gatesplus.session.CreateSession;

/**
 * Vanilla marker items (tagged yellow / red concrete) are drawn with the model of the own marker
 * blocks, in the inventory, the hotbar and the hand. Client-side only; the item stays concrete.
 */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
	@ModifyExpressionValue(method = "appendItemLayers", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
	private Object gatesplus$markerModel(Object model, ItemStackRenderState state, ItemStack stack,
			ItemDisplayContext context, Level level, ItemOwner owner, int seed) {
		CreateSession.Point corner = MarkerBlocks.vanillaMarkerCorner(stack);
		if (corner == null) return model;
		Identifier id = GatesPlus.id(corner == CreateSession.Point.CORNER1 ? "marker_top_left" : "marker_bottom_right");
		return id;
	}
}
