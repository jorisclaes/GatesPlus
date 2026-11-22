package be.jorisclaes.gatesplus.marker;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import be.jorisclaes.gatesplus.GatesPlus;

/**
 * A corner marker. Placing one is only meaningful during a create/edit session: the session
 * remembers where it was placed so it can be cleaned up when the session ends. Placed without a
 * session (should not happen, the items are removed) it disappears again immediately.
 */
public final class MarkerBlock extends Block {
	public MarkerBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!(level instanceof ServerLevel serverLevel)) return;
		boolean tracked = placer instanceof ServerPlayer player
				&& GatesPlus.get().gates().sessions().onMarkerPlaced(player, serverLevel, pos);
		if (!tracked) serverLevel.removeBlock(pos, false);
	}
}
