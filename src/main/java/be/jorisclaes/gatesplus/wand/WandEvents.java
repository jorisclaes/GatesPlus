package be.jorisclaes.gatesplus.wand;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import be.jorisclaes.gatesplus.GatesPlus;

/**
 * Wand input. On the client we return SUCCESS so the click is still sent to the server
 * (and no block breaking / log stripping starts); the server does the real work.
 */
public final class WandEvents {
	private WandEvents() {}

	public static void register() {
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (hand != InteractionHand.MAIN_HAND || !Wand.isWand(player.getItemInHand(hand))) return InteractionResult.PASS;
			if (player instanceof ServerPlayer sp) GatesPlus.get().gates().sessions().onLeftClick(sp, pos, direction);
			return InteractionResult.SUCCESS;
		});

		// edit session: a marker on a block of the gate being edited takes that block's place
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			var stack = player.getItemInHand(hand);
			if (!be.jorisclaes.gatesplus.marker.MarkerBlocks.isMarkerItem(stack)) return InteractionResult.PASS;
			if (!(player instanceof ServerPlayer sp) || !(level instanceof net.minecraft.server.level.ServerLevel sl)) {
				return InteractionResult.PASS;
			}
			return GatesPlus.get().gates().sessions().placeMarkerOnGate(sp, sl, hit.getBlockPos(), stack)
					? InteractionResult.SUCCESS : InteractionResult.PASS;
		});

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !Wand.isWand(player.getItemInHand(hand))) return InteractionResult.PASS;
			if (player instanceof ServerPlayer sp) GatesPlus.get().gates().sessions().onRightClick(sp);
			return InteractionResult.SUCCESS;
		});

		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (hand != InteractionHand.MAIN_HAND || !Wand.isWand(player.getItemInHand(hand))) return InteractionResult.PASS;
			if (player instanceof ServerPlayer sp) GatesPlus.get().gates().sessions().onRightClick(sp);
			return InteractionResult.SUCCESS;
		});

		// item frames, armor stands, allays, ...: the wand can't be handed to an entity
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				Wand.isSessionTool(player.getItemInHand(hand)) ? InteractionResult.FAIL : InteractionResult.PASS);

		// remove the wand before death drops are made
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer sp) {
				GatesPlus.get().gates().sessions().end(sp, false);
				Wand.removeAll(sp);
			}
			return true;
		});
	}
}
