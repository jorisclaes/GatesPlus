package be.jorisclaes.gatesplus.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import be.jorisclaes.gatesplus.GatesPlus;
import be.jorisclaes.gatesplus.network.MarkersPayload;
import be.jorisclaes.gatesplus.network.PanelPayload;
import be.jorisclaes.gatesplus.network.SelectionSyncPayload;

public class GatesPlusClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(SelectionSyncPayload.TYPE,
				(payload, ctx) -> ClientState.selection = payload);
		ClientPlayNetworking.registerGlobalReceiver(PanelPayload.TYPE, (payload, ctx) -> {
			if (payload.panel() == PanelPayload.DEVSTATS) ClientState.devPanel = payload;
			else ClientState.infoPanel = payload;
		});
		ClientPlayNetworking.registerGlobalReceiver(MarkersPayload.TYPE, (payload, ctx) -> ClientState.markers = payload);
		ClientTickEvents.END_CLIENT_TICK.register(MarkerSkin::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.reset());

		LevelRenderEvents.BEFORE_GIZMOS.register(SelectionRenderer::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, GatesPlus.id("panels"), new GatePanelsHud());
	}
}
