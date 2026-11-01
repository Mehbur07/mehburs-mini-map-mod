package com.mehbur.minimap.death;

import com.mehbur.minimap.MehbursMiniMapClient;
import com.mehbur.minimap.config.MinimapConfig;
import com.mehbur.minimap.render.MapLayout;
import com.mehbur.minimap.render.MapProjector;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Remembers the player's last death and points at it on the map. */
public final class DeathMarkers {
	public static final DeathMarkers INSTANCE = new DeathMarkers();

	/** {@code SKULL} sits on the death itself. {@code TRIANGLE} is a skull on the map's edge with a triangle pointing at it. */
	public enum Mark { NONE, SKULL, TRIANGLE }

	/** What the last frame showed, in GUI coordinates. */
	public record Drawn(Mark mark, int x, int y, boolean line) {
	}

	private static final Drawn NOTHING = new Drawn(Mark.NONE, 0, 0, false);
	private static final String WORLD_FILE = "mehburs_minimap_death.json";
	private static final int COLOR = 0xFFFFFFFF;
	private static final int OUTLINE_COLOR = 0xFF000000;
	private static final int LINE_OUTLINE_COLOR = 0x90000000;
	/** Keeps marks fully inside the frame. */
	private static final int EDGE_MARGIN = 4;
	private static final String[] SKULL_PIXELS = {
		".###.",
		"#####",
		"#.#.#",
		"#####",
		".#.#."
	};
	/** Triangle sizes count in fractions of a GUI unit. */
	private static final int TRIANGLE_SUBDIVISION = 4;
	private static final int TRIANGLE_LENGTH = 14;
	private static final int TRIANGLE_HALF_BASE = 10;
	private static final int TRIANGLE_OUTLINE_TIP = 6;
	private static final int TRIANGLE_OUTLINE_LENGTH = 24;
	private static final int TRIANGLE_OUTLINE_HALF_BASE = 17;
	/** From the skull's center to the triangle's tip, clearing the skull in every direction. */
	private static final float TRIANGLE_DISTANCE = 7f;
	/** Far beyond the map's edge in every direction, yet close enough that the camera's perspective cannot flip the point. */
	private static final double DIRECTION_SAMPLE_RADII = 3.0;

	private final MapProjector projector = new MapProjector();
	private DeathStore store;
	private ClientPacketListener lastConnection;
	private boolean wasDead;
	private Drawn drawn = NOTHING;

	private DeathMarkers() {
	}

	public void tick(Minecraft minecraft) {
		ClientPacketListener connection = minecraft.getConnection();
		Player player = minecraft.player;
		if (connection == null || player == null || minecraft.level == null) {
			store = null;
			lastConnection = null;
			wasDead = false;
			return;
		}
		if (connection != lastConnection) {
			lastConnection = connection;
			store = DeathStore.load(storePath(minecraft));
			wasDead = false;
		}
		// The report first: until respawning it still describes the previous death, which is then already known.
		player.getLastDeathLocation().ifPresent(death -> store.onServerDeath(toPoint(death)));
		boolean dead = player.isDeadOrDying();
		if (dead && !wasDead) {
			store.onClientDeath(toPoint(GlobalPos.of(minecraft.level.dimension(), player.blockPosition())));
		}
		wasDead = dead;
	}

	private static DeathPoint toPoint(GlobalPos position) {
		BlockPos pos = position.pos();
		return new DeathPoint(position.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ());
	}

	/** Worlds keep their death in their own folder, so that it goes away with them. Servers get a file per address. */
	private static Path storePath(Minecraft minecraft) {
		if (minecraft.hasSingleplayerServer()) {
			return minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT).resolve(WORLD_FILE);
		}
		ServerData server = minecraft.getCurrentServer();
		String address = server == null ? "unknown" : server.ip;
		String readable = address.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.-]", "_");
		// Different addresses can look the same once cleaned up, and only the hash tells them apart.
		String hash = HexFormat.of().toHexDigits(address.hashCode());
		return FabricLoader.getInstance().getConfigDir().resolve(MehbursMiniMapClient.MOD_ID).resolve("deaths").resolve(readable + "_" + hash + ".json");
	}

	public void toggleLine(Player player) {
		MinimapConfig config = MehbursMiniMapClient.config();
		config.deathLineEnabled = !config.deathLineEnabled;
		config.save();
		player.sendOverlayMessage(Component.translatable(config.deathLineEnabled ? "message.mehburs_minimap.death_line_on" : "message.mehburs_minimap.death_line_off"));
	}

	public void removeMarker(Player player) {
		if (store == null || store.point() == null) {
			player.sendOverlayMessage(Component.translatable("message.mehburs_minimap.no_death"));
			return;
		}
		store.removeMarker();
		player.sendOverlayMessage(Component.translatable("message.mehburs_minimap.death_marker_removed"));
	}

	/**
	 * @param viewProjection the camera the map image was last drawn with
	 * @param cameraPosition the player's position when the map image was drawn
	 * @param radiusBlocks   blocks from the map's center to its edge in that image
	 */
	public void draw(GuiGraphicsExtractor graphics, MinimapConfig config, String dimension, Matrix4f viewProjection, MapLayout.Rect rect, Vec3 cameraPosition, double radiusBlocks) {
		drawn = NOTHING;
		DeathPoint point = store == null ? null : store.point();
		if (point == null || !point.dimension().equals(dimension)) {
			return;
		}
		if (!project(point, viewProjection, cameraPosition, radiusBlocks)) {
			return;
		}
		float targetX = projector.guiX(rect);
		float targetY = projector.guiY(rect);
		float centerX = rect.x() + rect.size() / 2f;
		float centerY = rect.y() + rect.size() / 2f;
		MapGeometry.Point edge = MapGeometry.clampAlongRay(centerX, centerY, targetX, targetY,
			rect.x() + EDGE_MARGIN, rect.y() + EDGE_MARGIN, rect.x() + rect.size() - EDGE_MARGIN, rect.bottom() - EDGE_MARGIN);
		boolean inside = edge.x() == targetX && edge.y() == targetY;
		int x = Math.round(edge.x());
		int y = Math.round(edge.y());
		float angle = (float) Math.atan2(targetY - centerY, targetX - centerX);

		if (config.deathLineEnabled) {
			drawLine(graphics, centerX, centerY, angle, (float) Math.hypot(edge.x() - centerX, edge.y() - centerY));
		}
		Mark mark = Mark.NONE;
		if (config.deathTriangleEnabled && !store.isMarkerRemoved()) {
			if (inside) {
				mark = Mark.SKULL;
				drawSkull(graphics, x, y);
			} else if (!config.deathLineEnabled) {
				mark = Mark.TRIANGLE;
				drawSkull(graphics, x, y);
				drawTriangle(graphics, edge.x() - Mth.cos(angle) * TRIANGLE_DISTANCE, edge.y() - Mth.sin(angle) * TRIANGLE_DISTANCE, angle);
			}
		}
		drawn = new Drawn(mark, x, y, config.deathLineEnabled);
	}

	/** Leaves the point, or a point in its direction when it is too far for the camera, in the projector. */
	private boolean project(DeathPoint point, Matrix4f viewProjection, Vec3 cameraPosition, double radiusBlocks) {
		double dx = point.x() + 0.5 - cameraPosition.x;
		double dy = point.y() - cameraPosition.y;
		double dz = point.z() + 0.5 - cameraPosition.z;
		double limit = radiusBlocks * DIRECTION_SAMPLE_RADII;
		double horizontal = Math.hypot(dx, dz);
		if (horizontal <= limit && projector.project(viewProjection, dx, dy, dz)) {
			return true;
		}
		double scale = limit / Math.max(horizontal, 1.0);
		return projector.project(viewProjection, dx * scale, 0.0, dz * scale);
	}

	private static void drawLine(GuiGraphicsExtractor graphics, float x, float y, float angle, float length) {
		int end = Math.round(length);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().rotate(angle);
		graphics.pose().translate(0f, -0.5f);
		graphics.fill(0, -1, end, 2, LINE_OUTLINE_COLOR);
		graphics.fill(0, 0, end, 1, COLOR);
		graphics.pose().popMatrix();
	}

	/** Always upright, so that it reads as a skull. The outline also fills the eyes. */
	private static void drawSkull(GuiGraphicsExtractor graphics, int x, int y) {
		int left = x - SKULL_PIXELS[0].length() / 2;
		int top = y - SKULL_PIXELS.length / 2;
		for (int row = 0; row < SKULL_PIXELS.length; row++) {
			for (int column = 0; column < SKULL_PIXELS[row].length(); column++) {
				if (SKULL_PIXELS[row].charAt(column) == '#') {
					graphics.fill(left + column - 1, top + row - 1, left + column + 2, top + row + 2, OUTLINE_COLOR);
				}
			}
		}
		for (int row = 0; row < SKULL_PIXELS.length; row++) {
			for (int column = 0; column < SKULL_PIXELS[row].length(); column++) {
				if (SKULL_PIXELS[row].charAt(column) == '#') {
					graphics.fill(left + column, top + row, left + column + 1, top + row + 1, COLOR);
				}
			}
		}
	}

	/** The tip sits at the given position and points along the angle. */
	private static void drawTriangle(GuiGraphicsExtractor graphics, float x, float y, float angle) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().rotate(angle);
		// Fill only takes whole numbers, and whole GUI units would turn the slopes into visible stairs.
		graphics.pose().scale(1f / TRIANGLE_SUBDIVISION);
		fillTriangle(graphics, TRIANGLE_OUTLINE_TIP, TRIANGLE_OUTLINE_LENGTH, TRIANGLE_OUTLINE_HALF_BASE, OUTLINE_COLOR);
		fillTriangle(graphics, 0, TRIANGLE_LENGTH, TRIANGLE_HALF_BASE, COLOR);
		graphics.pose().popMatrix();
	}

	/** Nested rectangles, each reaching from the base toward the tip, because side-by-side ones would leave seams once rotated. */
	private static void fillTriangle(GuiGraphicsExtractor graphics, int tip, int length, int halfBase, int color) {
		for (int i = 0; i < length; i++) {
			int halfHeight = i * halfBase / length;
			graphics.fill(tip - length, -halfHeight, tip - i, halfHeight + 1, color);
		}
	}

	public Drawn drawn() {
		return drawn;
	}

	public DeathPoint point() {
		return store == null ? null : store.point();
	}

	public boolean isMarkerRemoved() {
		return store != null && store.isMarkerRemoved();
	}
}
