package be.jorisclaes.gatesplus.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import be.jorisclaes.gatesplus.protection.Protection;

/** Fire does not burn away fireproof gate blocks. */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
	@Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
	private void gatesplus$fireproofGates(Level level, BlockPos pos, int chance, RandomSource random, int age,
			CallbackInfo ci) {
		if (Protection.preventBurn(level, pos)) ci.cancel();
	}
}
