package be.jorisclaes.gatesplus.mixin;

import com.mojang.math.Transformation;

import net.minecraft.world.entity.Display;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.class)
public interface DisplayAccessor {
	@Invoker("setTransformation")
	void gatesplus$setTransformation(Transformation transformation);

	@Invoker("setTransformationInterpolationDuration")
	void gatesplus$setInterpolationDuration(int ticks);

	@Invoker("setTransformationInterpolationDelay")
	void gatesplus$setInterpolationDelay(int ticks);

	@Invoker("setViewRange")
	void gatesplus$setViewRange(float range);

	@Invoker("setBrightnessOverride")
	void gatesplus$setBrightnessOverride(net.minecraft.util.Brightness brightness);
}
