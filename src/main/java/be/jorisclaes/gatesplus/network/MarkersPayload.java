package be.jorisclaes.gatesplus.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import be.jorisclaes.gatesplus.GatesPlus;

/**
 * Server -> client: every placed (vanilla) marker on the server. The client mod shows these
 * positions as the textured marker blocks (client-side only; the server keeps the vanilla block).
 */
public record MarkersPayload(List<Entry> markers) implements CustomPacketPayload {
	/** @param corner 1 = top left, 2 = bottom right */
	public record Entry(String dimension, BlockPos pos, int corner) {}

	private static final int MAX = 1024;
	public static final Type<MarkersPayload> TYPE = new Type<>(GatesPlus.id("markers"));
	public static final MarkersPayload EMPTY = new MarkersPayload(List.of());
	public static final StreamCodec<RegistryFriendlyByteBuf, MarkersPayload> CODEC =
			CustomPacketPayload.codec(MarkersPayload::write, MarkersPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		int n = Math.min(markers.size(), MAX);
		buf.writeVarInt(n);
		for (int i = 0; i < n; i++) {
			Entry e = markers.get(i);
			buf.writeUtf(e.dimension());
			buf.writeBlockPos(e.pos());
			buf.writeVarInt(e.corner());
		}
	}

	private static MarkersPayload read(RegistryFriendlyByteBuf buf) {
		int n = Math.min(buf.readVarInt(), MAX);
		List<Entry> list = new ArrayList<>(n);
		for (int i = 0; i < n; i++) list.add(new Entry(buf.readUtf(), buf.readBlockPos(), buf.readVarInt()));
		return new MarkersPayload(list);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
