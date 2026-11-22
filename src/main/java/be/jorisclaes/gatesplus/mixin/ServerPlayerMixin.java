package be.jorisclaes.gatesplus.mixin;

import net.minecraft.server.level.ServerPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import be.jorisclaes.gatesplus.wand.Wand;

/** The wand can't be dropped with Q / Ctrl+Q. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Inject(method = "drop(Z)V", at = @At("HEAD"), cancellable = true)
	private void gatesplus$keepWand(boolean all, CallbackInfo ci) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		if (Wand.isWand(self.getInventory().getSelectedItem())) {
			ci.cancel();
			self.containerMenu.sendAllDataToRemote();
		}
	}
}
