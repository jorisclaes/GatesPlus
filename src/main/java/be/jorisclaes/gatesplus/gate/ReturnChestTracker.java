package be.jorisclaes.gatesplus.gate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
	/** Blocks that found no room in a chest, waiting for /gp claim: owner -> (item id -> amount). */
	private final Map<UUID, Map<String, Integer>> pending = new HashMap<>();

	public void load(List<ReturnChestRecord> records, Map<UUID, Map<String, Integer>> pendingReturns) {
		chests.clear();
		chests.addAll(records);
		pending.clear();
		pendingReturns.forEach((owner, items) -> pending.put(owner, new LinkedHashMap<>(items)));
	}

	/** Total number of blocks waiting for this player. */
	public int pendingCount(UUID owner) {
		Map<String, Integer> items = pending.get(owner);
		int n = 0;
		if (items != null) for (int amount : items.values()) n += amount;
		return n;
	}

	/**
	 * Hands back blocks in chests; whatever finds no room is kept (memory + database) until the
	 * player runs /gp claim at a spot with more room.
	 */
	public void give(ServerLevel level, ServerPlayer player, List<ItemStack> stacks) {
		List<ItemStack> left = spawn(level, player, stacks);
		if (left.isEmpty()) return;
		int n = 0;
		for (ItemStack s : left) {
			addPending(player.getUUID(), BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount());
			n += s.getCount();
		}
		Messages.send(player, "chest.pending", n);
	}

	/**
	 * /gp claim: places chests with the waiting blocks, as many as fit here; the rest keeps waiting.
	 * @return number of blocks handed out
	 */
	public int claim(ServerLevel level, ServerPlayer player) {
		UUID owner = player.getUUID();
		Map<String, Integer> items = pending.get(owner);
		if (items == null || items.isEmpty()) {
			Messages.send(player, "claim.none");
			return 0;
		}
		List<ItemStack> stacks = new ArrayList<>();
		int total = 0;
		for (var e : List.copyOf(items.entrySet())) {
			Identifier id = Identifier.tryParse(e.getKey());
			Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
			if (item == null || item == Items.AIR) {
				// unknown item (mod removed?): nothing to give, forget it
				setPending(owner, e.getKey(), 0);
				continue;
			}
			int max = new ItemStack(item).getMaxStackSize();
			for (int left = e.getValue(); left > 0; left -= max) stacks.add(new ItemStack(item, Math.min(max, left)));
			total += e.getValue();
			setPending(owner, e.getKey(), 0);
		}
		List<ItemStack> left = spawn(level, player, stacks);
		int rest = 0;
		for (ItemStack s : left) {
			addPending(owner, BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount());
			rest += s.getCount();
		}
		if (rest == 0) Messages.send(player, "claim.done", total);
		else if (rest == total) Messages.send(player, "claim.no_room", rest);
		else Messages.send(player, "claim.partial", total - rest, rest);
		return total - rest;
	}

	private void addPending(UUID owner, String item, int amount) {
		Map<String, Integer> items = pending.get(owner);
		int now = (items == null ? 0 : items.getOrDefault(item, 0)) + amount;
		setPending(owner, item, now);
	}

	private void setPending(UUID owner, String item, int amount) {
		if (amount > 0) {
			pending.computeIfAbsent(owner, k -> new LinkedHashMap<>()).put(item, amount);
		} else {
			Map<String, Integer> items = pending.get(owner);
			if (items != null) {
				items.remove(item);
				if (items.isEmpty()) pending.remove(owner);
			}
		}
		GatesPlus.get().storage().run(s -> s.setPendingReturn(owner, item, amount));
	}

	public int count() {
		return chests.size();
	}

	/**
	 * Puts {@code stacks} into new chests near the player.
	 * @return the stacks that found no room (no free spot left)
	 */
	private List<ItemStack> spawn(ServerLevel level, ServerPlayer player, List<ItemStack> stacks) {
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
		return new ArrayList<>(stacks.subList(index, stacks.size()));
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
