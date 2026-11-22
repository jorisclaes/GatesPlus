package be.jorisclaes.gatesplus.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import be.jorisclaes.gatesplus.protection.Protection;

/** Pistons can't push or pull indestructible gate blocks. */
@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockMixin {
	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private static void gatesplus$immovableGates(BlockState state, Level level, BlockPos pos, Direction direction,
			boolean allowDestroy, Direction pistonFacing, CallbackInfoReturnable<Boolean> cir) {
		if (Protection.preventPush(level, pos)) cir.setReturnValue(false);
	}
}
