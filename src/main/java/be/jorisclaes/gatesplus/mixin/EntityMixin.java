package be.jorisclaes.gatesplus.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import be.jorisclaes.gatesplus.marker.MarkerLabels;

/** Marker labels are not sent to players with the client mod (they see the textured marker). */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
	private void gatesplus$hideMarkerLabel(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
		if (MarkerLabels.hideFrom((Entity) (Object) this, player)) cir.setReturnValue(false);
	}
}
