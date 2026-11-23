package be.jorisclaes.gatesplus.marker;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.mojang.math.Transformation;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;

import be.jorisclaes.gatesplus.mixin.DisplayAccessor;
import be.jorisclaes.gatesplus.mixin.TextDisplayAccessor;
import be.jorisclaes.gatesplus.session.CreateSession;

/**
 * Floating "1" / "2" above a vanilla marker block (vanilla clients can't see a custom texture).
 * Labels only live as long as their session; a leftover one (crash, unloaded chunk) is removed
 * when its chunk loads because its UUID is no longer known.
 */
public final class MarkerLabels {
	public static final String TAG = "gatesplus_marker_label";
	private static final Set<UUID> OWN = new HashSet<>();

	private MarkerLabels() {}

	public static UUID spawn(ServerLevel level, BlockPos pos, CreateSession.Point corner) {
		boolean one = corner == CreateSession.Point.CORNER1;
		Display.TextDisplay label = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
		label.setPos(pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5);
		label.addTag(TAG);
		((TextDisplayAccessor) label).gatesplus$setText(Component.literal(one ? "1" : "2")
				.withStyle(one ? ChatFormatting.GOLD : ChatFormatting.RED, ChatFormatting.BOLD));
		((TextDisplayAccessor) label).gatesplus$setBackgroundColor(0x60000000);
		((TextDisplayAccessor) label).gatesplus$setFlags((byte) (Display.TextDisplay.FLAG_SHADOW | Display.TextDisplay.FLAG_SEE_THROUGH));
		DisplayAccessor d = (DisplayAccessor) label;
		d.gatesplus$setBillboardConstraints(Display.BillboardConstraints.CENTER);
		d.gatesplus$setBrightnessOverride(Brightness.FULL_BRIGHT);
		d.gatesplus$setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
				new Vector3f(2, 2, 2), new Quaternionf()));
		level.addFreshEntity(label);
		OWN.add(label.getUUID());
		return label.getUUID();
	}

	public static void remove(ServerLevel level, UUID id) {
		if (id == null) return;
		OWN.remove(id);
		Entity e = level.getEntity(id);
		if (e != null) e.discard();
	}

	/** True for a label that no longer belongs to a session (to be discarded on load). */
	public static boolean isStale(Entity entity) {
		return entity.entityTags().contains(TAG) && !OWN.contains(entity.getUUID());
	}
}
