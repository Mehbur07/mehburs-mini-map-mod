package com.mehbur.minimap.entity;

import com.mehbur.minimap.config.MinimapConfig;
import com.mehbur.minimap.render.MapLayout;
import com.mehbur.minimap.render.terrain.CaveDetector;
import com.mehbur.minimap.render.terrain.MapLayerTracker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

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

	private record Tracked(Entity entity, EntityDotType type) {
	}

	/** A dot as last drawn, in GUI coordinates. */
	public record Dot(Entity entity, EntityDotType type, int x, int y) {
	}

	private final List<Tracked> tracked = new ArrayList<>();
	private final List<Dot> dots = new ArrayList<>();
	private final Vector4f clip = new Vector4f();

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
			if (type != null && isEnabled(type, config) && isOnMapLayer(level, entity, layerY)) {
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

	private static boolean isEnabled(EntityDotType type, MinimapConfig config) {
		return type == EntityDotType.OTHER_PLAYER ? config.showPlayers : config.showMobs;
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
			clip.set((float) (position.x - cameraPosition.x), (float) (position.y - cameraPosition.y), (float) (position.z - cameraPosition.z), 1f);
			viewProjection.transform(clip);
			if (clip.w <= 0f) {
				continue;
			}
			int radius = item.type == EntityDotType.BOSS ? BOSS_DOT_RADIUS : DOT_RADIUS;
			int x = rect.x() + Math.round((clip.x / clip.w + 1f) * 0.5f * rect.size());
			int y = rect.y() + Math.round((1f - clip.y / clip.w) * 0.5f * rect.size());
			if (x - radius >= rect.x() && x + radius <= rect.x() + rect.size() && y - radius >= rect.y() && y + radius <= rect.bottom()) {
				dots.add(new Dot(item.entity, item.type, x, y));
				int outline = item.type == EntityDotType.BOSS ? BOSS_OUTLINE_COLOR : OUTLINE_COLOR;
				graphics.fill(x - radius - 1, y - radius - 1, x + radius + 1, y + radius + 1, outline);
				graphics.fill(x - radius, y - radius, x + radius, y + radius, OPAQUE | item.type.color(config));
			}
		}
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
