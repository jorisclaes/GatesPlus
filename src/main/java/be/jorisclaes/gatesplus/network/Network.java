package be.jorisclaes.gatesplus.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class Network {
	private Network() {}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(SelectionSyncPayload.TYPE, SelectionSyncPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PanelPayload.TYPE, PanelPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MarkersPayload.TYPE, MarkersPayload.CODEC);
	}

	/** True when the player runs the GatesPlus client mod. */
	public static boolean hasClientMod(ServerPlayer player) {
		return ServerPlayNetworking.canSend(player, SelectionSyncPayload.TYPE);
	}

	/** Sends only when the client mod is installed. */
	public static boolean send(ServerPlayer player, CustomPacketPayload payload) {
		if (!ServerPlayNetworking.canSend(player, payload.type())) return false;
		ServerPlayNetworking.send(player, payload);
		return true;
	}
}
