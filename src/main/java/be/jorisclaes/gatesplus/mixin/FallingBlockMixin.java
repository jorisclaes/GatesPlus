package be.jorisclaes.gatesplus.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import be.jorisclaes.gatesplus.protection.Protection;

/** Sand/gravel gates are locked in place: their blocks never start falling. */
@Mixin(FallingBlock.class)
public abstract class FallingBlockMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void gatesplus$lockGateBlocks(BlockState state, ServerLevel level, BlockPos pos, RandomSource random,
			CallbackInfo ci) {
		if (Protection.lockFalling(level, pos)) ci.cancel();
	}
}
