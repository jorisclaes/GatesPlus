package be.jorisclaes.gatesplus.gate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;

import be.jorisclaes.gatesplus.GatesPlus;
import be.jorisclaes.gatesplus.config.GatesPlusConfig;

/**
 * Decides which configured blocks may be used for gates. Falling blocks are allowed (they are
 * locked in place by a mixin); anything that can't be kept stable is filtered out and logged.
 */
public final class BlockFilter {
	/** block id -> block, in config order */
	private static volatile Map<String, Block> allowed = Map.of();

	private BlockFilter() {}

	public static void rebuild(GatesPlusConfig cfg) {
		Map<String, Block> out = new LinkedHashMap<>();
		List<String> whitelist = cfg.nonFullBlockWhitelist().stream().map(BlockFilter::normalize).toList();
		for (String raw : cfg.allowedBlocks()) {
			String id = normalize(raw);
			Identifier key = Identifier.tryParse(id);
			Block block = key == null ? null : BuiltInRegistries.BLOCK.getOptional(key).orElse(null);
			if (block == null) {
				GatesPlus.LOGGER.warn("allowed-blocks: unknown block '{}' ignored", raw);
				continue;
			}
			String reason = rejectReason(block, whitelist.contains(id));
			if (reason != null) {
				GatesPlus.LOGGER.warn("allowed-blocks: '{}' is not usable for gates ({}), ignored", id, reason);
				continue;
			}
			out.put(id, block);
		}
		allowed = Collections.unmodifiableMap(out);
	}

	/** Accepts "stone" as well as "minecraft:stone". */
	public static String normalize(String id) {
		String s = id.trim().toLowerCase(java.util.Locale.ROOT);
		return s.contains(":") ? s : "minecraft:" + s;
	}

	/** @return null when the block is fine, otherwise a short English reason for the log */
	static String rejectReason(Block block, boolean nonFullWhitelisted) {
		BlockState state = block.defaultBlockState();
		if (state.isAir()) return "air";
		if (be.jorisclaes.gatesplus.marker.MarkerBlocks.isCustomMarker(state)) return "marker block";
		if (block instanceof LiquidBlock || !state.getFluidState().isEmpty()) return "liquid/waterlogged";
		if (state.hasBlockEntity()) return "has a block entity (dupe/data-loss risk)";
		if (block instanceof TntBlock) return "TNT";
		if (state.isSignalSource()) return "redstone component";
		if (state.isRandomlyTicking()) return "changes by itself (random ticks)";
		if (!nonFullWhitelisted && !(block instanceof FallingBlock)
				&& !state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
			return "not a full block and not in non-full-block-whitelist";
		}
		if (block == Blocks.BEDROCK || block == Blocks.BARRIER || block == Blocks.STRUCTURE_VOID) return "admin block";
		return null;
	}

	public static Map<String, Block> allowed() {
		return allowed;
	}

	public static Block allowedBlock(String id) {
		return allowed.get(normalize(id));
	}

	public static List<String> allowedIds() {
		return new ArrayList<>(allowed.keySet());
	}
}
