package com.mehbur.minimap.death;

import com.mehbur.minimap.MehbursMiniMapClient;
import com.mehbur.minimap.WorldFiles;
import com.mehbur.minimap.config.MinimapConfig;
import com.mehbur.minimap.render.MapLayout;
import com.mehbur.minimap.render.MapPointer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
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
	private static final String SERVER_FOLDER = "deaths";
	private static final int COLOR = 0xFFFFFFFF;
	private static final int OUTLINE_COLOR = 0xFF000000;
	private static final String[] SKULL_PIXELS = {
		".###.",
		"#####",
		"#.#.#",
		"#####",
		".#.#."
	};
	/** From the skull's center to the triangle's tip, clearing the skull in every direction. */
	private static final float TRIANGLE_DISTANCE = 7f;

	private final MapPointer pointer = new MapPointer();
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
			store = DeathStore.load(WorldFiles.path(minecraft, WORLD_FILE, SERVER_FOLDER));
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
		MapPointer.Aim aim = pointer.aim(point.x(), point.y(), point.z(), viewProjection, rect, cameraPosition, radiusBlocks);
		if (aim == null) {
			return;
		}
		int x = Math.round(aim.edgeX());
		int y = Math.round(aim.edgeY());

		if (config.deathLineEnabled) {
			MapPointer.drawLine(graphics, aim, COLOR);
		}
		Mark mark = Mark.NONE;
		if (config.deathTriangleEnabled && !store.isMarkerRemoved()) {
			if (aim.inside()) {
				mark = Mark.SKULL;
				drawSkull(graphics, x, y);
			} else if (!config.deathLineEnabled) {
				mark = Mark.TRIANGLE;
				drawSkull(graphics, x, y);
				MapPointer.drawTriangle(graphics, aim.edgeX() - Mth.cos(aim.angle()) * TRIANGLE_DISTANCE, aim.edgeY() - Mth.sin(aim.angle()) * TRIANGLE_DISTANCE, aim.angle(), COLOR);
			}
		}
		drawn = new Drawn(mark, x, y, config.deathLineEnabled);
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
