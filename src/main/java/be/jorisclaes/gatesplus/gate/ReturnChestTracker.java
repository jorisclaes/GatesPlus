package be.jorisclaes.gatesplus.gate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import be.jorisclaes.gatesplus.GatesPlus;
import be.jorisclaes.gatesplus.storage.ReturnChestRecord;
import be.jorisclaes.gatesplus.util.Messages;

/**
 * Hands back many gate blocks in ordinary vanilla chests. A chest is removed automatically once it
 * is empty; breaking it simply follows the normal game rules (contents and chest drop).
 */
public final class ReturnChestTracker {
	private static final int SEARCH_RADIUS = 3;
	private final List<ReturnChestRecord> chests = new ArrayList<>();

	public void load(List<ReturnChestRecord> records) {
		chests.clear();
		chests.addAll(records);
	}

	public int count() {
		return chests.size();
	}

	/** Puts {@code stacks} into new chests near the player; anything that doesn't fit is dropped. */
	public void spawn(ServerLevel level, ServerPlayer player, List<ItemStack> stacks) {
		List<BlockPos> placed = new ArrayList<>();
		int index = 0;
		while (index < stacks.size()) {
			BlockPos pos = findFreeSpot(level, player.blockPosition(), placed);
			if (pos == null) break;
			level.setBlock(pos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
			BlockEntity be = level.getBlockEntity(pos);
			if (!(be instanceof Container container)) break;
			for (int slot = 0; slot < container.getContainerSize() && index < stacks.size(); slot++) {
				container.setItem(slot, stacks.get(index++));
			}
			placed.add(pos);
			ReturnChestRecord rec = new ReturnChestRecord(GateManager.dimensionId(level), pos.getX(), pos.getY(),
					pos.getZ(), player.getUUID(), System.currentTimeMillis());
			chests.add(rec);
			GatesPlus.get().storage().run(s -> s.insertReturnChest(rec));
			Messages.send(player, "chest.placed", pos.getX(), pos.getY(), pos.getZ());
		}
		for (; index < stacks.size(); index++) drop(level, player, stacks.get(index));
	}

	public static void drop(ServerLevel level, ServerPlayer player, ItemStack stack) {
		ItemEntity item = new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), stack);
		level.addFreshEntity(item);
	}

	private static BlockPos findFreeSpot(ServerLevel level, BlockPos center, List<BlockPos> taken) {
		for (int r = 1; r <= SEARCH_RADIUS; r++) {
			for (int dy = 0; dy <= 1; dy++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
						BlockPos p = center.offset(dx, dy, dz);
						if (taken.contains(p) || !level.getBlockState(p).isAir()) continue;
						if (GatesPlus.get().gates().manager().gateAt(level, p) != null) continue;
						if (isNextToChest(level, p)) continue;
						return p;
					}
				}
			}
		}
		return null;
	}

	/** Avoid touching other chests so vanilla never merges ours into a double chest. */
	private static boolean isNextToChest(Level level, BlockPos p) {
		for (BlockPos n : new BlockPos[] {p.north(), p.south(), p.east(), p.west()}) {
			if (level.getBlockState(n).is(Blocks.CHEST) || level.getBlockState(n).is(Blocks.TRAPPED_CHEST)) return true;
		}
		return false;
	}

	/** Every second: forget broken chests and remove empty ones. */
	public void tick(MinecraftServer server) {
		Iterator<ReturnChestRecord> it = chests.iterator();
		while (it.hasNext()) {
			ReturnChestRecord rec = it.next();
			Identifier dim = Identifier.tryParse(rec.dimension());
			if (dim == null) continue;
			ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
			if (level == null) continue;
			BlockPos pos = new BlockPos(rec.x(), rec.y(), rec.z());
			if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
			boolean remove;
			if (!level.getBlockState(pos).is(Blocks.CHEST)) {
				remove = true; // broken or replaced: normal game logic already handled it
			} else if (level.getBlockEntity(pos) instanceof Container c && c.isEmpty()) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				remove = true;
			} else {
				remove = false;
			}
			if (remove) {
				it.remove();
				GatesPlus.get().storage().run(s -> s.deleteReturnChest(rec.dimension(), rec.x(), rec.y(), rec.z()));
			}
		}
	}
}
