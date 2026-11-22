package be.jorisclaes.gatesplus.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import be.jorisclaes.gatesplus.wand.WandGuard;

/** The wand can't leave the player's inventory through any inventory click. */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
	@Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
	private void gatesplus$lockWand(int slotIndex, int button, ContainerInput input, Player player, CallbackInfo ci) {
		if (!(player instanceof ServerPlayer sp)) return;
		AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
		if (WandGuard.blocksClick(self, slotIndex, button, input, sp)) {
			ci.cancel();
			self.sendAllDataToRemote();
		}
	}
}
