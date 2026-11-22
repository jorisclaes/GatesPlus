package be.jorisclaes.gatesplus.mixin;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import be.jorisclaes.gatesplus.protection.Protection;

/** Indestructible gate blocks are removed from the list of blocks an explosion destroys. */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@Inject(method = "calculateExplodedPositions", at = @At("RETURN"), cancellable = true)
	private void gatesplus$protectGates(CallbackInfoReturnable<List<BlockPos>> cir) {
		cir.setReturnValue(Protection.filterExplosion(level, cir.getReturnValue()));
	}
}
