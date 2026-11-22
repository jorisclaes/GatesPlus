package be.jorisclaes.gatesplus.gate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * In-memory index of all gates. Everything the server tick or the protection mixins need is
 * answered from here without touching the database.
 */
public final class GateManager {
	private final Map<Integer, Gate> byId = new HashMap<>();
	private final Map<String, Gate> byScopeName = new HashMap<>();
	/** dimension -> chunk -> gates whose box or trigger touches that chunk */
	private final Map<String, Long2ObjectMap<List<Gate>>> chunkIndex = new HashMap<>();
	/** dimension -> packed trigger pos -> gate */
	private final Map<String, Long2ObjectMap<Gate>> triggerIndex = new HashMap<>();
	/** Gates whose trigger chunk is loaded. Only these are ticked. */
	private final Set<Gate> active = new LinkedHashSet<>();
	private final Map<Integer, Block> blockCache = new HashMap<>();

	public static String dimensionId(Level level) {
		return level.dimension().identifier().toString();
	}

	private static String nameKey(String scopeKey, String name) {
		return scopeKey + "|" + name.toLowerCase(Locale.ROOT);
	}

	// ------------------------------------------------------------------ mutation

	public void add(Gate gate) {
		byId.put(gate.id(), gate);
		byScopeName.put(nameKey(gate.scopeKey(), gate.name()), gate);
		index(gate);
	}

	public void remove(Gate gate) {
		byId.remove(gate.id());
		byScopeName.remove(nameKey(gate.scopeKey(), gate.name()));
		unindex(gate);
		active.remove(gate);
		blockCache.remove(gate.id());
	}

	/**
	 * Applies {@code updated} onto the stored gate and refreshes every index.
	 * Use this for all changes to geometry, name, group or block.
	 */
	public void update(Gate gate, Gate updated) {
		byScopeName.remove(nameKey(gate.scopeKey(), gate.name()));
		unindex(gate);
		gate.copyFrom(updated);
		blockCache.remove(gate.id());
		byScopeName.put(nameKey(gate.scopeKey(), gate.name()), gate);
		index(gate);
	}

	public void clear() {
		byId.clear();
		byScopeName.clear();
		chunkIndex.clear();
		triggerIndex.clear();
		active.clear();
		blockCache.clear();
	}

	private void index(Gate g) {
		Long2ObjectMap<List<Gate>> chunks = chunkIndex.computeIfAbsent(g.dimension(), d -> new Long2ObjectOpenHashMap<>());
		for (long chunk : chunksOf(g)) chunks.computeIfAbsent(chunk, c -> new ArrayList<>(1)).add(g);
		triggerIndex.computeIfAbsent(g.dimension(), d -> new Long2ObjectOpenHashMap<>())
				.put(BlockPos.asLong(g.triggerX(), g.triggerY(), g.triggerZ()), g);
	}

	private void unindex(Gate g) {
		Long2ObjectMap<List<Gate>> chunks = chunkIndex.get(g.dimension());
		if (chunks != null) {
			for (long chunk : chunksOf(g)) {
				List<Gate> list = chunks.get(chunk);
				if (list != null) {
					list.remove(g);
					if (list.isEmpty()) chunks.remove(chunk);
				}
			}
		}
		Long2ObjectMap<Gate> triggers = triggerIndex.get(g.dimension());
		if (triggers != null) triggers.remove(BlockPos.asLong(g.triggerX(), g.triggerY(), g.triggerZ()), g);
	}

	private static List<Long> chunksOf(Gate g) {
		List<Long> out = new ArrayList<>();
		for (int cx = g.minX() >> 4; cx <= g.maxX() >> 4; cx++) {
			for (int cz = g.minZ() >> 4; cz <= g.maxZ() >> 4; cz++) out.add(ChunkPos.pack(cx, cz));
		}
		long trigger = ChunkPos.pack(g.triggerX() >> 4, g.triggerZ() >> 4);
		if (!out.contains(trigger)) out.add(trigger);
		return out;
	}

	// ------------------------------------------------------------------ active set (chunk load/unload)

	public void onChunkLoad(ServerLevel level, ChunkPos pos) {
		for (Gate g : gatesInChunk(level, pos.pack())) {
			if ((g.triggerX() >> 4) == pos.x() && (g.triggerZ() >> 4) == pos.z()) active.add(g);
		}
	}

	public void onChunkUnload(ServerLevel level, ChunkPos pos) {
		for (Gate g : gatesInChunk(level, pos.pack())) {
			if ((g.triggerX() >> 4) == pos.x() && (g.triggerZ() >> 4) == pos.z()) active.remove(g);
		}
	}

	/** Re-evaluates whether a gate's trigger chunk is loaded (after create/edit). */
	public void refreshActive(ServerLevel level, Gate g) {
		if (level != null && level.hasChunk(g.triggerX() >> 4, g.triggerZ() >> 4)) active.add(g);
		else active.remove(g);
	}

	public Collection<Gate> activeGates() {
		return Collections.unmodifiableSet(active);
	}

	public int activeCount() {
		return active.size();
	}

	// ------------------------------------------------------------------ lookups

	public Gate byId(int id) {
		return byId.get(id);
	}

	public Gate byName(String scopeKey, String name) {
		return byScopeName.get(nameKey(scopeKey, name));
	}

	public Collection<Gate> all() {
		return Collections.unmodifiableCollection(byId.values());
	}

	public int count() {
		return byId.size();
	}

	public List<Gate> ownedBy(UUID owner) {
		List<Gate> out = new ArrayList<>();
		for (Gate g : byId.values()) if (g.ownerUuid().equals(owner)) out.add(g);
		return out;
	}

	public List<Gate> inGroup(int groupId) {
		List<Gate> out = new ArrayList<>();
		for (Gate g : byId.values()) if (g.groupId() != null && g.groupId() == groupId) out.add(g);
		return out;
	}

	private List<Gate> gatesInChunk(Level level, long chunk) {
		Long2ObjectMap<List<Gate>> chunks = chunkIndex.get(dimensionId(level));
		if (chunks == null) return List.of();
		List<Gate> list = chunks.get(chunk);
		return list == null ? List.of() : List.copyOf(list);
	}

	/** Gate whose box contains {@code pos} (open or closed), or null. Hot path: one hash lookup. */
	public Gate gateAt(Level level, BlockPos pos) {
		Long2ObjectMap<List<Gate>> chunks = chunkIndex.get(dimensionId(level));
		if (chunks == null) return null;
		List<Gate> list = chunks.get(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
		if (list == null) return null;
		for (Gate g : list) if (g.contains(pos.getX(), pos.getY(), pos.getZ())) return g;
		return null;
	}

	public Gate gateWithTrigger(Level level, BlockPos pos) {
		Long2ObjectMap<Gate> triggers = triggerIndex.get(dimensionId(level));
		return triggers == null ? null : triggers.get(pos.asLong());
	}

	/**
	 * The gate owning the actual gate block at {@code pos}: the position is inside a closed gate and
	 * still holds that gate's block. Blocks a player placed inside an open gate are not gate blocks.
	 */
	public Gate gateBlockAt(Level level, BlockPos pos, BlockState state) {
		Gate g = gateAt(level, pos);
		if (g == null || g.isOpen()) return null;
		return state.is(blockOf(g)) ? g : null;
	}

	public Block blockOf(Gate g) {
		return blockCache.computeIfAbsent(g.id(), id -> resolveBlock(g.block()));
	}

	public static Block resolveBlock(String id) {
		Identifier key = Identifier.tryParse(id);
		if (key == null) return Blocks.AIR;
		return BuiltInRegistries.BLOCK.getOptional(key).orElse(Blocks.AIR);
	}

	public int chunkBucketCount() {
		int n = 0;
		for (var m : chunkIndex.values()) n += m.size();
		return n;
	}

	public int largestChunkBucket() {
		int max = 0;
		for (var m : chunkIndex.values()) for (List<Gate> l : m.values()) max = Math.max(max, l.size());
		return max;
	}
}
