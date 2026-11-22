package be.jorisclaes.gatesplus.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import be.jorisclaes.gatesplus.network.PanelPayload;

/** The gate info panel and the dev stats panel, stacked in the top-right corner. */
final class GatePanelsHud implements HudElement {
	private static final int MARGIN = 4;
	private static final int PADDING = 5;
	private static final int GAP = 8;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		int y = MARGIN;
		y = draw(g, mc.font, ClientState.infoPanel, y, 0xFF4CFF5A);
		if (y > MARGIN) y += MARGIN;
		draw(g, mc.font, ClientState.devPanel, y, 0xFFFFC21A);
	}

	/** @return the y below the drawn panel (unchanged when the panel is hidden) */
	private static int draw(GuiGraphicsExtractor g, Font font, PanelPayload panel, int top, int accent) {
		if (panel.rows().isEmpty()) return top;
		int labelWidth = 0, valueWidth = 0;
		for (String row : panel.rows()) {
			int tab = row.indexOf('\t');
			String label = tab < 0 ? row : row.substring(0, tab);
			String value = tab < 0 ? "" : row.substring(tab + 1);
			labelWidth = Math.max(labelWidth, font.width(label));
			valueWidth = Math.max(valueWidth, font.width(value));
		}
		int lineHeight = font.lineHeight + 2;
		int width = Math.max(font.width(panel.title()), labelWidth + GAP + valueWidth) + PADDING * 2;
		int height = PADDING * 2 + lineHeight * (panel.rows().size() + 1) + 2;
		int right = g.guiWidth() - MARGIN;
		int left = right - width;

		g.fill(left, top, right, top + height, 0xB0101418);
		g.fill(left, top, left + 2, top + height, accent);
		int x = left + PADDING + 1;
		int y = top + PADDING;
		g.text(font, panel.title(), x, y, accent, true);
		y += lineHeight + 2;
		for (String row : panel.rows()) {
			int tab = row.indexOf('\t');
			String label = tab < 0 ? row : row.substring(0, tab);
			String value = tab < 0 ? "" : row.substring(tab + 1);
			g.text(font, label, x, y, 0xFFAAAAAA, true);
			g.text(font, value, x + labelWidth + GAP, y, 0xFFFFFFFF, true);
			y += lineHeight;
		}
		return top + height;
	}
}
