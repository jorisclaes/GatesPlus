package be.jorisclaes.gatesplus.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import be.jorisclaes.gatesplus.GatesPlus;

/**
 * Server -> client: the current wand selection, rendered as markers by the client mod.
 * {@code active == false} hides everything.
 *
 * @param triggerMode    wand is selecting the trigger block
 * @param triggerRange   max distance trigger <-> gate (drawn as a faint box in trigger mode)
 * @param invalid        positions that block confirm (non-air), drawn red; capped in size
 * @param outOfRange     real blocks just outside the trigger range (can't be the trigger), drawn red;
 *                       empty when the range isn't shown
 */
public record SelectionSyncPayload(boolean active, Optional<BlockPos> corner1, Optional<BlockPos> corner2,
		Optional<BlockPos> trigger, boolean triggerMode, int triggerRange, List<BlockPos> invalid,
		List<BlockPos> outOfRange)
		implements CustomPacketPayload {

	public static final int MAX_INVALID = 512;
	public static final Type<SelectionSyncPayload> TYPE = new Type<>(GatesPlus.id("selection"));
	public static final SelectionSyncPayload EMPTY =
			new SelectionSyncPayload(false, Optional.empty(), Optional.empty(), Optional.empty(), false, 0, List.of(), List.of());

	public static final StreamCodec<RegistryFriendlyByteBuf, SelectionSyncPayload> CODEC =
			CustomPacketPayload.codec(SelectionSyncPayload::write, SelectionSyncPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeBoolean(active);
		writeOpt(buf, corner1);
		writeOpt(buf, corner2);
		writeOpt(buf, trigger);
		buf.writeBoolean(triggerMode);
		buf.writeVarInt(triggerRange);
		int n = Math.min(invalid.size(), MAX_INVALID);
		buf.writeVarInt(n);
		for (int i = 0; i < n; i++) buf.writeBlockPos(invalid.get(i));
		writeList(buf, outOfRange);
	}

	private static void writeList(RegistryFriendlyByteBuf buf, List<BlockPos> list) {
		int n = Math.min(list.size(), MAX_INVALID);
		buf.writeVarInt(n);
		for (int i = 0; i < n; i++) buf.writeBlockPos(list.get(i));
	}

	private static List<BlockPos> readList(RegistryFriendlyByteBuf buf) {
		int n = Math.min(buf.readVarInt(), MAX_INVALID);
		List<BlockPos> list = new ArrayList<>(n);
		for (int i = 0; i < n; i++) list.add(buf.readBlockPos());
		return list;
	}

	private static SelectionSyncPayload read(RegistryFriendlyByteBuf buf) {
		boolean active = buf.readBoolean();
		Optional<BlockPos> c1 = readOpt(buf), c2 = readOpt(buf), t = readOpt(buf);
		boolean triggerMode = buf.readBoolean();
		int range = buf.readVarInt();
		int n = Math.min(buf.readVarInt(), MAX_INVALID);
		List<BlockPos> invalid = new ArrayList<>(n);
		for (int i = 0; i < n; i++) invalid.add(buf.readBlockPos());
		return new SelectionSyncPayload(active, c1, c2, t, triggerMode, range, invalid, readList(buf));
	}

	private static void writeOpt(RegistryFriendlyByteBuf buf, Optional<BlockPos> pos) {
		buf.writeBoolean(pos.isPresent());
		pos.ifPresent(buf::writeBlockPos);
	}

	private static Optional<BlockPos> readOpt(RegistryFriendlyByteBuf buf) {
		return buf.readBoolean() ? Optional.of(buf.readBlockPos()) : Optional.empty();
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
