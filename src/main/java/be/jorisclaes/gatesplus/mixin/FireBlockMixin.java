package be.jorisclaes.gatesplus.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import be.jorisclaes.gatesplus.protection.Protection;

/**
 * Fireproof gates: their blocks never burn away, and no fire can exist against them. A fire next
 * to a fireproof gate block can't be lit, doesn't spread there and goes out by itself, so the gate
 * never shows the burning animation, also when blocks next to it are on fire.
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
	@Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
	private void gatesplus$fireproofGates(Level level, BlockPos pos, int chance, RandomSource random, int age,
			CallbackInfo ci) {
		if (Protection.preventBurn(level, pos)) ci.cancel();
	}

	/** Used by flint and steel, fire charges, neighbour updates and the fire's own tick. */
	@Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
	private void gatesplus$noFireAgainstGate(BlockState state, LevelReader level, BlockPos pos,
			CallbackInfoReturnable<Boolean> cir) {
		if (Protection.nextToFireproofGate(level, pos)) cir.setReturnValue(false);
	}

	/** Spreading: an empty spot next to a fireproof gate never catches fire. */
	@Inject(method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I",
			at = @At("HEAD"), cancellable = true)
	private void gatesplus$noSpreadToGate(LevelReader level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		if (Protection.nextToFireproofGate(level, pos)) cir.setReturnValue(0);
	}

	/** A burnt-out neighbour of the gate turns into air instead of new fire. */
	@Inject(method = "getStateWithAge", at = @At("HEAD"), cancellable = true)
	private void gatesplus$noNewFireAtGate(LevelReader level, BlockPos pos, int age,
			CallbackInfoReturnable<BlockState> cir) {
		if (Protection.nextToFireproofGate(level, pos)) cir.setReturnValue(Blocks.AIR.defaultBlockState());
	}
}
