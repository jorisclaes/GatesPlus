package be.jorisclaes.gatesplus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.Block;

import be.jorisclaes.gatesplus.marker.MarkerBlocks;
import be.jorisclaes.gatesplus.network.MarkersPayload;
import be.jorisclaes.gatesplus.session.CreateSession;

/**
 * On a server with vanilla markers (yellow / red concrete) the client mod shows them as the own
 * textured marker blocks. This only changes the client's copy of the world: the server keeps the
 * concrete, and every block update from the server (which brings the concrete back) is
 * re-skinned on the next tick.
 */
final class MarkerSkin {
	private MarkerSkin() {}

	static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		MarkersPayload markers = ClientState.markers;
		if (level == null || markers.markers().isEmpty()) return;
		String dim = level.dimension().identifier().toString();
		for (MarkersPayload.Entry e : markers.markers()) {
			if (!e.dimension().equals(dim) || !level.isLoaded(e.pos())) continue;
			CreateSession.Point corner = e.corner() == 1 ? CreateSession.Point.CORNER1 : CreateSession.Point.CORNER2;
			Block skin = MarkerBlocks.customBlockFor(corner);
			if (skin == null || !level.getBlockState(e.pos()).is(MarkerBlocks.vanillaBlockFor(corner))) continue;
			level.setBlock(e.pos(), skin.defaultBlockState(), Block.UPDATE_ALL);
		}
	}
}
