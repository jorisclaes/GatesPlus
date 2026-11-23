package be.jorisclaes.gatesplus.client;

import be.jorisclaes.gatesplus.network.MarkersPayload;
import be.jorisclaes.gatesplus.network.PanelPayload;
import be.jorisclaes.gatesplus.network.SelectionSyncPayload;

/** Latest data received from the server; everything shown by the client mod comes from here. */
public final class ClientState {
	static volatile SelectionSyncPayload selection = SelectionSyncPayload.EMPTY;
	static volatile PanelPayload infoPanel = PanelPayload.hide(PanelPayload.INFO);
	static volatile PanelPayload devPanel = PanelPayload.hide(PanelPayload.DEVSTATS);
	static volatile MarkersPayload markers = MarkersPayload.EMPTY;

	private ClientState() {}

	static void reset() {
		selection = SelectionSyncPayload.EMPTY;
		infoPanel = PanelPayload.hide(PanelPayload.INFO);
		devPanel = PanelPayload.hide(PanelPayload.DEVSTATS);
		markers = MarkersPayload.EMPTY;
	}
}
