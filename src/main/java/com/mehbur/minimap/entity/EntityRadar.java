package com.mehbur.minimap.entity;

import com.mehbur.minimap.config.MinimapConfig;
import com.mehbur.minimap.render.MapLayout;
import com.mehbur.minimap.render.MapProjector;
import com.mehbur.minimap.render.terrain.CaveDetector;
import com.mehbur.minimap.render.terrain.MapLayerTracker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The dots on the map. Deciding which entities count happens once per tick, because it reads blocks and light;
 * placing them happens every frame so they move smoothly. Knows only the entities the server sent to the client.
 */
public final class EntityRadar {
	public static final EntityRadar INSTANCE = new EntityRadar();

	private static final int OPAQUE = 0xFF000000;
	private static final int OUTLINE_COLOR = 0xFF000000;
	/** Maroon disappears against a black outline on the dark cave map, so bosses get a light one and more size. */
	private static final int BOSS_OUTLINE_COLOR = 0xFFFFFFFF;
	private static final int DOT_RADIUS = 1;
	private static final int BOSS_DOT_RADIUS = 2;
	private static final int HOVER_RADIUS = 4;
	private static final int BAR_HALF_LENGTH = 2;
	private static final int BAR_TIP_LENGTH = 1;
	private static final int TIP_TINT_COLOR = 0xFFFFFFFF;
	private static final double FLIGHT_SAMPLE_BLOCKS = 2.0;
	private static final double MIN_MOTION_SQR = 1.0E-6;

	private record Tracked(Entity entity, EntityDotType type) {
	}

	/**
	 * A dot as last drawn, in GUI coordinates.
	 *
	 * @param angle radians from the screen's right toward its bottom, for types drawn as a bar
	 */
	public record Dot(Entity entity, EntityDotType type, int x, int y, float angle) {
	}

	private final List<Tracked> tracked = new ArrayList<>();
	private final List<Dot> dots = new ArrayList<>();
	private final MapProjector projector = new MapProjector();

	private EntityRadar() {
	}

	public void clear() {
		tracked.clear();
		dots.clear();
	}

	public void tick(ClientLevel level, Player viewer, MinimapConfig config, int layerY) {
		tracked.clear();
		for (Entity entity : level.entitiesForRendering()) {
			EntityDotType type = entity == viewer || !isSeen(entity, viewer) ? null : EntityClassifier.classify(entity, viewer);
			if (type != null && type.isEnabled(config) && isOnMapLayer(level, entity, layerY)) {
				tracked.add(new Tracked(entity, type));
			}
		}
		tracked.sort(Comparator.comparing(Tracked::type));
	}

	/** A map must not reveal what the player's own eyes are not allowed to see. */
	private static boolean isSeen(Entity entity, Player viewer) {
		if (!entity.isAlive() || entity.isInvisibleTo(viewer)) {
			return false;
		}
		return !entity.isSpectator() || viewer.isSpectator();
	}

	/** Hides entities the current map cannot show: those in rock below the surface view, and those above or behind rock in a cave view. */
	private static boolean isOnMapLayer(ClientLevel level, Entity entity, int layerY) {
		BlockPos eye = BlockPos.containing(entity.getEyePosition());
		if (layerY == MapLayerTracker.SURFACE) {
			return !CaveDetector.hasRoof(level, eye);
		}
		return entity.getBlockY() <= layerY + 1 && !CaveDetector.hasRoof(level, eye, layerY);
	}

	/**
	 * Places and draws the dots over the map.
	 *
	 * @param viewProjection the camera the map image was last drawn with, so that dots line up with it
	 * @param cameraPosition the player's position when the map image was drawn
	 */
	public void draw(GuiGraphicsExtractor graphics, MinimapConfig config, Matrix4f viewProjection, MapLayout.Rect rect, Vec3 cameraPosition, float partialTick) {
		dots.clear();
		for (Tracked item : tracked) {
			if (item.entity.isRemoved()) {
				continue;
			}
			Vec3 position = item.entity.getPosition(partialTick);
			if (!projector.project(viewProjection, position.x - cameraPosition.x, position.y - cameraPosition.y, position.z - cameraPosition.z)) {
				continue;
			}
			float exactX = projector.guiX(rect);
			float exactY = projector.guiY(rect);
			int x = Math.round(exactX);
			int y = Math.round(exactY);
			int radius = radius(item.type);
			if (x - radius < rect.x() || x + radius > rect.x() + rect.size() || y - radius < rect.y() || y + radius > rect.bottom()) {
				continue;
			}
			int color = OPAQUE | item.type.color(config);
			if (item.type == EntityDotType.ARROW) {
				float angle = flightAngle(item.entity, viewProjection, position, cameraPosition, rect, exactX, exactY);
				dots.add(new Dot(item.entity, item.type, x, y, angle));
				drawBar(graphics, x, y, angle, color);
			} else {
				dots.add(new Dot(item.entity, item.type, x, y, 0f));
				int outline = item.type == EntityDotType.BOSS ? BOSS_OUTLINE_COLOR : OUTLINE_COLOR;
				graphics.fill(x - radius - 1, y - radius - 1, x + radius + 1, y + radius + 1, outline);
				graphics.fill(x - radius, y - radius, x + radius, y + radius, color);
			}
		}
	}

	private static int radius(EntityDotType type) {
		return switch (type) {
			case BOSS -> BOSS_DOT_RADIUS;
			case ARROW -> BAR_HALF_LENGTH;
			default -> DOT_RADIUS;
		};
	}

	/** The on-screen direction of flight, found by projecting a point ahead of the arrow the same way as the arrow itself. */
	private float flightAngle(Entity entity, Matrix4f viewProjection, Vec3 position, Vec3 cameraPosition, MapLayout.Rect rect, float x, float y) {
		Vec3 motion = entity.getDeltaMovement();
		if (motion.lengthSqr() < MIN_MOTION_SQR) {
			return 0f;
		}
		Vec3 ahead = position.add(motion.normalize().scale(FLIGHT_SAMPLE_BLOCKS)).subtract(cameraPosition);
		if (!projector.project(viewProjection, ahead.x, ahead.y, ahead.z)) {
			return 0f;
		}
		return (float) Math.atan2(projector.guiY(rect) - y, projector.guiX(rect) - x);
	}

	/** The front of the bar is lighter, so that the direction of flight can be told from its back. */
	private static void drawBar(GuiGraphicsExtractor graphics, int x, int y, float angle, int color) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().rotate(angle);
		// One unit thick has no center row, so the bar shifts by half a unit to stay centered on the entity.
		graphics.pose().translate(0f, -0.5f);
		graphics.fill(-BAR_HALF_LENGTH - 1, -1, BAR_HALF_LENGTH + 1, 2, OUTLINE_COLOR);
		graphics.fill(-BAR_HALF_LENGTH, 0, BAR_HALF_LENGTH, 1, color);
		graphics.fill(BAR_HALF_LENGTH - BAR_TIP_LENGTH, 0, BAR_HALF_LENGTH, 1, ARGB.average(color, TIP_TINT_COLOR));
		graphics.pose().popMatrix();
	}

	/** The name of the dot nearest to a GUI position, translated by the game, or null when none is close. */
	public Component nameAt(int x, int y) {
		Dot nearest = null;
		int nearestDistance = HOVER_RADIUS * HOVER_RADIUS + 1;
		for (Dot dot : dots) {
			int dx = dot.x - x;
			int dy = dot.y - y;
			int distance = dx * dx + dy * dy;
			if (distance < nearestDistance) {
				nearest = dot;
				nearestDistance = distance;
			}
		}
		return nearest == null ? null : nearest.entity.getName();
	}

	public List<Dot> dots() {
		return dots;
	}
}
