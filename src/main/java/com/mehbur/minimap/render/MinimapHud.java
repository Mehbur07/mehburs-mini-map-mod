package com.mehbur.minimap.render;

import com.mehbur.minimap.MehbursMiniMapClient;
import com.mehbur.minimap.config.MinimapConfig;
import com.mehbur.minimap.render.terrain.CaveDetector;
import com.mehbur.minimap.render.terrain.ChunkMesh;
import com.mehbur.minimap.render.terrain.MapLayerTracker;
import com.mehbur.minimap.render.terrain.TerrainCache;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Draws the off-screen map, its frame and the player's dot into a screen corner. */
public final class MinimapHud implements AutoCloseable {
	private static final int MARGIN = 8;
	private static final int FRAME_COLOR = 0xFF00FFFF;
	private static final int DOT_OUTLINE_COLOR = 0xFF000000;
	private static final int OPAQUE = 0xFF000000;
	/** Redraws wait this long after a change, which caps the map at about 60 FPS. */
	private static final long MIN_FRAME_MILLIS = 16;
	/** Animated textures such as water keep moving at this rate while the player stands still. */
	private static final long IDLE_FRAME_MILLIS = 100;
	/** Below this many screen pixels per block, block textures only shimmer. */
	private static final float SIMPLIFY_BELOW_PIXELS_PER_BLOCK = 2f;
	private static final float SIMPLIFIED_TEXTURE_LOD = 4f;

	private static boolean enabled = true;

	private final TerrainCache terrain = new TerrainCache();
	private final List<ChunkMesh> visibleMeshes = new ArrayList<>();
	private final MapLayerTracker layerTracker = new MapLayerTracker();
	private ClientLevel lastLevel;
	private MapRenderTarget target;
	private long lastFrameMillis;
	private Vec3 lastPosition = Vec3.ZERO;
	private float lastYaw;
	private float lastZoom;
	private boolean lastNorthLocked;

	public static void register() {
		MinimapHud hud = new MinimapHud();
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MehbursMiniMapClient.MOD_ID, "minimap"), hud::extract);
		ClientTickEvents.END_CLIENT_TICK.register(hud::tick);
		ClientChunkEvents.CHUNK_LOAD.register(hud.terrain::onChunkLoaded);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> hud.close());
	}

	/** Lets tests measure the game's frame time without the map. */
	public static void setEnabled(boolean value) {
		enabled = value;
	}

	private void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null) {
			terrain.clear();
			lastLevel = null;
			return;
		}
		int radiusChunks = (int) Math.ceil(viewReach(minecraft, minecraft.player.getY()) / 16.0) + 1;
		terrain.tick(minecraft.level, blockAtlas(minecraft), minecraft.player.getX(), minecraft.player.getZ(), radiusChunks, updateLayer(minecraft));
	}

	private int updateLayer(Minecraft minecraft) {
		boolean underground = MehbursMiniMapClient.config().autoCaveMode && CaveDetector.isUnderground(minecraft.level, minecraft.player);
		int eyeBlockY = CaveDetector.eyeBlockY(minecraft.player);
		if (minecraft.level != lastLevel) {
			lastLevel = minecraft.level;
			return layerTracker.reset(underground, eyeBlockY);
		}
		return layerTracker.update(underground, eyeBlockY);
	}

	private static double viewReach(Minecraft minecraft, double playerY) {
		float zoom = MehbursMiniMapClient.config().zoom;
		return MapCamera.viewReachBlocks(zoom, playerY - minecraft.level.getSeaLevel());
	}

	private static TextureAtlas blockAtlas(Minecraft minecraft) {
		return minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
	}

	private void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;
		if (player == null || !enabled) {
			return;
		}
		MinimapConfig config = MehbursMiniMapClient.config();
		int size = Math.min(config.mapSize, Math.min(graphics.guiWidth(), graphics.guiHeight()) - 2 * MARGIN);
		int pixels = size * minecraft.getWindow().getGuiScale();

		boolean meshesChanged = terrain.uploadFinished();
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
		Vec3 position = player.getPosition(partialTick);
		float yaw = player.getViewYRot(partialTick);
		if (shouldRedraw(meshesChanged, position, yaw, config)) {
			renderMap(minecraft, pixels, position, yaw, config);
		}

		boolean left = config.corner == MinimapConfig.Corner.TOP_LEFT || config.corner == MinimapConfig.Corner.BOTTOM_LEFT;
		boolean top = config.corner == MinimapConfig.Corner.TOP_LEFT || config.corner == MinimapConfig.Corner.TOP_RIGHT;
		int x = left ? MARGIN : graphics.guiWidth() - MARGIN - size;
		int y = top ? MARGIN : graphics.guiHeight() - MARGIN - size;
		// Render targets store rows bottom-up, so V runs from 1 to 0.
		graphics.blit(target.colorView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST),
			x, y, x + size, y + size, 0f, 1f, 1f, 0f);
		drawFrame(graphics, x, y, size);
		drawPlayerDot(graphics, x + size / 2, y + size / 2, OPAQUE | config.playerDotColor);
	}

	private boolean shouldRedraw(boolean meshesChanged, Vec3 position, float yaw, MinimapConfig config) {
		long sinceLastFrame = Util.getMillis() - lastFrameMillis;
		if (target == null || sinceLastFrame >= IDLE_FRAME_MILLIS) {
			return true;
		}
		boolean moved = !position.equals(lastPosition) || yaw != lastYaw;
		boolean settingsChanged = config.zoom != lastZoom || config.northLocked != lastNorthLocked;
		return sinceLastFrame >= MIN_FRAME_MILLIS && (meshesChanged || moved || settingsChanged);
	}

	private void renderMap(Minecraft minecraft, int pixels, Vec3 position, float yaw, MinimapConfig config) {
		lastFrameMillis = Util.getMillis();
		lastPosition = position;
		lastYaw = yaw;
		lastZoom = config.zoom;
		lastNorthLocked = config.northLocked;
		if (target == null) {
			target = new MapRenderTarget();
		}
		visibleMeshes.clear();
		terrain.collectVisible(position.x, position.z, viewReach(minecraft, position.y), visibleMeshes);

		boolean zZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
		Matrix4f viewProjection = MapCamera.viewProjection(config.zoom, yaw, config.northLocked, zZeroToOne);
		float pixelsPerBlock = pixels / (2f * MapCamera.radiusBlocks(config.zoom));
		float textureLod = config.simplifyDistantTextures && pixelsPerBlock < SIMPLIFY_BELOW_PIXELS_PER_BLOCK ? SIMPLIFIED_TEXTURE_LOD : 0f;
		target.render(pixels, viewProjection, textureLod, blockAtlas(minecraft), visibleMeshes, position.x, position.y, position.z);
	}

	private static void drawFrame(GuiGraphicsExtractor graphics, int x, int y, int size) {
		graphics.fill(x - 1, y - 1, x + size + 1, y, FRAME_COLOR);
		graphics.fill(x - 1, y + size, x + size + 1, y + size + 1, FRAME_COLOR);
		graphics.fill(x - 1, y, x, y + size, FRAME_COLOR);
		graphics.fill(x + size, y, x + size + 1, y + size, FRAME_COLOR);
	}

	private static void drawPlayerDot(GuiGraphicsExtractor graphics, int centerX, int centerY, int color) {
		graphics.fill(centerX - 1, centerY - 2, centerX + 2, centerY + 3, DOT_OUTLINE_COLOR);
		graphics.fill(centerX - 2, centerY - 1, centerX + 3, centerY + 2, DOT_OUTLINE_COLOR);
		graphics.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, color);
	}

	@Override
	public void close() {
		terrain.close();
		if (target != null) {
			target.close();
			target = null;
		}
	}
}
