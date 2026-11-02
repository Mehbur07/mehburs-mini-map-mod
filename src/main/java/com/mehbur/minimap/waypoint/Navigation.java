package com.mehbur.minimap.waypoint;

import com.mehbur.minimap.WorldFiles;
import com.mehbur.minimap.config.MinimapConfig;
import com.mehbur.minimap.render.MapLayout;
import com.mehbur.minimap.render.MapPointer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Keeps the player's saved locations and points at the one they chose to go to. */
public final class Navigation {
	public static final Navigation INSTANCE = new Navigation();

	/** What the last frame showed, in GUI coordinates. {@code target} means the location itself is on the map. */
	public record Drawn(boolean line, boolean target, int x, int y) {
	}

	private static final Drawn NOTHING = new Drawn(false, false, 0, 0);
	private static final String WORLD_FILE = "mehburs_minimap_waypoints.json";
	private static final String SERVER_FOLDER = "waypoints";
	private static final double ARRIVAL_DISTANCE = 4.0;
	private static final int OPAQUE = 0xFF000000;
	private static final int OUTLINE_COLOR = 0xFF000000;
	private static final int TARGET_RADIUS = 3;
	private static final float TARGET_ANGLE = Mth.PI / 4f;

	private final MapPointer pointer = new MapPointer();
	private WaypointStore store;
	private ClientPacketListener lastConnection;
	private Drawn drawn = NOTHING;

	private Navigation() {
	}

	public void tick(Minecraft minecraft) {
		ClientPacketListener connection = minecraft.getConnection();
		Player player = minecraft.player;
		if (connection == null || player == null || minecraft.level == null) {
			store = null;
			lastConnection = null;
			return;
		}
		if (connection != lastConnection) {
			lastConnection = connection;
			store = WaypointStore.load(WorldFiles.path(minecraft, WORLD_FILE, SERVER_FOLDER));
		}
		int slot = store.active();
		Waypoint target = target();
		if (target != null && target.dimension().equals(dimension(minecraft)) && distance(target, player.position()) <= ARRIVAL_DISTANCE) {
			store.setActive(WaypointStore.NO_SLOT);
			player.sendOverlayMessage(Component.translatable("message.mehburs_minimap.navigation_arrived", slot + 1));
		}
	}

	private static String dimension(Minecraft minecraft) {
		return minecraft.level.dimension().identifier().toString();
	}

	private static double distance(Waypoint waypoint, Vec3 position) {
		double dx = waypoint.x() + 0.5 - position.x;
		double dy = waypoint.y() - position.y;
		double dz = waypoint.z() + 0.5 - position.z;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** Saves where the player stands, replacing what the slot held. */
	public void save(int slot, Minecraft minecraft) {
		if (store == null || minecraft.player == null) {
			return;
		}
		BlockPos pos = minecraft.player.blockPosition();
		store.set(slot, new Waypoint(dimension(minecraft), pos.getX(), pos.getY(), pos.getZ()));
		minecraft.player.sendOverlayMessage(Component.translatable("message.mehburs_minimap.waypoint_saved", slot + 1));
	}

	public void delete(int slot, Player player) {
		if (store == null) {
			return;
		}
		store.clear(slot);
		player.sendOverlayMessage(Component.translatable("message.mehburs_minimap.waypoint_deleted", slot + 1));
	}

	/** One location at a time: the slot's key starts leading there, and pressing it again stops. */
	public void toggle(int slot, Player player) {
		if (waypoint(slot) == null) {
			player.sendOverlayMessage(Component.translatable("message.mehburs_minimap.waypoint_empty", slot + 1));
			return;
		}
		boolean start = store.active() != slot;
		store.setActive(start ? slot : WaypointStore.NO_SLOT);
		player.sendOverlayMessage(start
			? Component.translatable("message.mehburs_minimap.navigation_on", slot + 1)
			: Component.translatable("message.mehburs_minimap.navigation_off"));
	}

	/** The location in the slot, or null if it is empty. */
	public Waypoint waypoint(int slot) {
		return store == null ? null : store.get(slot);
	}

	/** The slot being navigated to, or {@link WaypointStore#NO_SLOT}. */
	public int activeSlot() {
		return store == null ? WaypointStore.NO_SLOT : store.active();
	}

	private Waypoint target() {
		int slot = activeSlot();
		return slot == WaypointStore.NO_SLOT ? null : store.get(slot);
	}

	/** The text line under the map, or null without a navigation. */
	public Component infoLine(String dimension, Vec3 position) {
		Waypoint target = target();
		if (target == null) {
			return null;
		}
		int number = store.active() + 1;
		if (!target.dimension().equals(dimension)) {
			return Component.translatable("hud.mehburs_minimap.navigation_other_dimension", number);
		}
		return Component.translatable("hud.mehburs_minimap.navigation", number, (int) Math.round(distance(target, position)));
	}

	/**
	 * @param viewProjection the camera the map image was last drawn with
	 * @param cameraPosition the player's position when the map image was drawn
	 * @param radiusBlocks   blocks from the map's center to its edge in that image
	 */
	public void draw(GuiGraphicsExtractor graphics, MinimapConfig config, String dimension, Matrix4f viewProjection, MapLayout.Rect rect, Vec3 cameraPosition, double radiusBlocks) {
		drawn = NOTHING;
		Waypoint target = target();
		if (target == null || !target.dimension().equals(dimension)) {
			return;
		}
		MapPointer.Aim aim = pointer.aim(target.x(), target.y(), target.z(), viewProjection, rect, cameraPosition, radiusBlocks);
		if (aim == null) {
			return;
		}
		int color = OPAQUE | config.navigationColor;
		MapPointer.drawLine(graphics, aim, color);
		if (aim.inside()) {
			drawTarget(graphics, aim.edgeX(), aim.edgeY(), color);
		} else {
			MapPointer.drawTriangle(graphics, aim.edgeX(), aim.edgeY(), aim.angle(), color);
		}
		drawn = new Drawn(true, aim.inside(), Math.round(aim.edgeX()), Math.round(aim.edgeY()));
	}

	/** A diamond, so that it does not read as an entity's dot. */
	private static void drawTarget(GuiGraphicsExtractor graphics, float x, float y, int color) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().rotate(TARGET_ANGLE);
		graphics.fill(-TARGET_RADIUS - 1, -TARGET_RADIUS - 1, TARGET_RADIUS + 1, TARGET_RADIUS + 1, OUTLINE_COLOR);
		graphics.fill(-TARGET_RADIUS, -TARGET_RADIUS, TARGET_RADIUS, TARGET_RADIUS, color);
		graphics.pose().popMatrix();
	}

	public Drawn drawn() {
		return drawn;
	}
}
