package be.jorisclaes.gatesplus.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccessor {
	@Invoker("setText")
	void gatesplus$setText(Component text);

	@Invoker("setBackgroundColor")
	void gatesplus$setBackgroundColor(int argb);

	@Invoker("setFlags")
	void gatesplus$setFlags(byte flags);
}
