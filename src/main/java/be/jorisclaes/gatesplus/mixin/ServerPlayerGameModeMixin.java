package be.jorisclaes.gatesplus.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import be.jorisclaes.gatesplus.GatesPlus;

/**
 * A vanilla client starts mining when it left-clicks with the wand (or hits a marker); the server
 * cancels that click, so the client's "stop destroying" later names a block the server never
 * started on and vanilla logs "Mismatch in destroy block pos". Harmless: silenced during sessions.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
	@Shadow @Final protected ServerPlayer player;

	@WrapWithCondition(method = "abortDestroyBlock",
			at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"))
	private boolean gatesplus$quietMismatch(Logger logger, String message, Object a, Object b) {
		GatesPlus mod = GatesPlus.get();
		return mod == null || mod.gates() == null || mod.gates().sessions().get(player) == null;
	}
}
