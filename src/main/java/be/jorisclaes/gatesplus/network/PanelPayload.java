package be.jorisclaes.gatesplus.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import be.jorisclaes.gatesplus.GatesPlus;

/**
 * Server -> client: ready-to-draw text for a HUD panel in the top-right corner. The server
 * translates everything, so the client needs no language files. Rows are "label\tvalue";
 * an empty list hides the panel.
 *
 * @param panel 0 = gate info panel, 1 = dev stats panel
 */
public record PanelPayload(int panel, String title, List<String> rows) implements CustomPacketPayload {
	public static final int INFO = 0;
	public static final int DEVSTATS = 1;
	private static final int MAX_ROWS = 32;

	public static final Type<PanelPayload> TYPE = new Type<>(GatesPlus.id("panel"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PanelPayload> CODEC =
			CustomPacketPayload.codec(PanelPayload::write, PanelPayload::read);

	public static PanelPayload hide(int panel) {
		return new PanelPayload(panel, "", List.of());
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(panel);
		buf.writeUtf(title);
		int n = Math.min(rows.size(), MAX_ROWS);
		buf.writeVarInt(n);
		for (int i = 0; i < n; i++) buf.writeUtf(rows.get(i));
	}

	private static PanelPayload read(RegistryFriendlyByteBuf buf) {
		int panel = buf.readVarInt();
		String title = buf.readUtf();
		int n = Math.min(buf.readVarInt(), MAX_ROWS);
		List<String> rows = new ArrayList<>(n);
		for (int i = 0; i < n; i++) rows.add(buf.readUtf());
		return new PanelPayload(panel, title, rows);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
