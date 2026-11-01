package com.mehbur.minimap.render;

import com.mehbur.minimap.MehbursMiniMapClient;
import com.mehbur.minimap.config.MinimapConfig;
import com.mehbur.minimap.death.DeathMarkers;
import com.mehbur.minimap.entity.EntityRadar;
import com.mehbur.minimap.input.BigMapScreen;
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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Draws the off-screen map, its frame, the player's dot and the text lines under it, in a corner or as the big map. */
public final class MinimapHud implements AutoCloseable {
	private static final int FRAME_COLOR = 0xFF00FFFF;
	private static final int DOT_OUTLINE_COLOR = 0xFF000000;
	private static final int OPAQUE = 0xFF000000;
	private static final int INFO_TEXT_COLOR = 0xFF00FFFF;
	private static final int INFO_BACKDROP_COLOR = 0x90000000;
	private static final int INFO_LINE_HEIGHT = 10;
	/** Leaves room for the frame between the map and its first text line. */
	private static final int INFO_GAP = 3;
	private static final int INFO_PADDING = 2;
	/** Redraws wait this long after a change, which caps the map at about 60 FPS. */
	private static final long MIN_FRAME_MILLIS = 16;
	/** Animated textures such as water keep moving at this rate while the player stands still. */
	private static final long IDLE_FRAME_MILLIS = 100;

	private static boolean enabled = true;

	private final TerrainCache terrain = new TerrainCache();
	private final List<ChunkMesh> visibleMeshes = new ArrayList<>();
	private final MapLayerTracker layerTracker = new MapLayerTracker();
	private final MapAnimation animation = new MapAnimation();
	private ClientLevel lastLevel;
	private MapRenderTarget target;
	private Matrix4f lastViewProjection;
	private long lastFrameMillis;
	private Vec3 lastPosition = Vec3.ZERO;
	private float lastYaw;
	private float lastZoom;
	private int lastPixels;
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
		DeathMarkers.INSTANCE.tick(minecraft);
		if (minecraft.level == null || minecraft.player == null) {
			terrain.clear();
			EntityRadar.INSTANCE.clear();
			lastLevel = null;
			return;
		}
		MinimapConfig config = MehbursMiniMapClient.config();
		float widestZoom = animation.isClosed() ? config.zoom : Math.min(config.zoom, config.bigMapZoom);
		int radiusChunks = (int) Math.ceil(viewReach(minecraft, minecraft.player.getY(), widestZoom) / 16.0) + 1;
		int layerY = updateLayer(minecraft);
		terrain.tick(minecraft.level, blockAtlas(minecraft), minecraft.player.getX(), minecraft.player.getZ(), radiusChunks, layerY);
		EntityRadar.INSTANCE.tick(minecraft.level, minecraft.player, config, layerY);
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

	private static double viewReach(Minecraft minecraft, double playerY, float zoom) {
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
		animation.update(minecraft.gui.screen() instanceof BigMapScreen, Util.getMillis(), config.animationMillis);
		float openness = animation.eased();

		boolean showCoordinates = config.showCoordinates && !minecraft.showOnlyReducedInfo();
		int infoLines = (config.showFps ? 1 : 0) + (showCoordinates ? 1 : 0);
		int infoHeight = infoLines == 0 ? 0 : INFO_GAP + infoLines * INFO_LINE_HEIGHT;
		MapLayout.Rect mini = MapLayout.mini(config.corner, config.mapSize, graphics.guiWidth(), graphics.guiHeight(), infoHeight);
		MapLayout.Rect big = MapLayout.big(graphics.guiWidth(), graphics.guiHeight(), infoHeight);
		MapLayout.Rect rect = MapLayout.lerp(mini, big, openness);

		// Stays at the big map's resolution for the whole animation, since resizing the render target every frame is costly.
		int pixels = (animation.isClosed() ? mini.size() : big.size()) * minecraft.getWindow().getGuiScale();
		float zoom = MapCamera.blendZoom(config.zoom, config.bigMapZoom, openness);

		boolean meshesChanged = terrain.uploadFinished();
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
		Vec3 position = player.getPosition(partialTick);
		float yaw = player.getViewYRot(partialTick);
		if (shouldRedraw(meshesChanged, position, yaw, zoom, pixels, config)) {
			renderMap(minecraft, pixels, position, yaw, zoom, config);
		}

		FilterMode filter = animation.isMoving() ? FilterMode.LINEAR : FilterMode.NEAREST;
		// Render targets store rows bottom-up, so V runs from 1 to 0.
		graphics.blit(target.colorView(), RenderSystem.getSamplerCache().getClampToEdge(filter),
			rect.x(), rect.y(), rect.x() + rect.size(), rect.bottom(), 0f, 1f, 1f, 0f);
		drawFrame(graphics, rect);
		DeathMarkers.INSTANCE.draw(graphics, config, minecraft.level.dimension().identifier().toString(), lastViewProjection, rect, lastPosition, MapCamera.radiusBlocks(lastZoom));
		EntityRadar.INSTANCE.draw(graphics, config, lastViewProjection, rect, lastPosition, partialTick);
		drawPlayerDot(graphics, rect.centerX(), rect.centerY(), OPAQUE | config.playerDotColor);

		int lineY = rect.bottom() + INFO_GAP;
		if (config.showFps) {
			drawInfoLine(graphics, minecraft.font, Component.translatable("hud.mehburs_minimap.fps", minecraft.getFps()), rect, lineY);
			lineY += INFO_LINE_HEIGHT;
		}
		if (showCoordinates) {
			drawInfoLine(graphics, minecraft.font, Component.literal(coordinates(position)), rect, lineY);
		}
	}

	private boolean shouldRedraw(boolean meshesChanged, Vec3 position, float yaw, float zoom, int pixels, MinimapConfig config) {
		long sinceLastFrame = Util.getMillis() - lastFrameMillis;
		if (target == null || pixels != lastPixels || sinceLastFrame >= IDLE_FRAME_MILLIS) {
			return true;
		}
		boolean moved = !position.equals(lastPosition) || yaw != lastYaw;
		boolean settingsChanged = zoom != lastZoom || config.northLocked != lastNorthLocked;
		return sinceLastFrame >= MIN_FRAME_MILLIS && (meshesChanged || moved || settingsChanged);
	}

	private void renderMap(Minecraft minecraft, int pixels, Vec3 position, float yaw, float zoom, MinimapConfig config) {
		lastFrameMillis = Util.getMillis();
		lastPosition = position;
		lastYaw = yaw;
		lastZoom = zoom;
		lastPixels = pixels;
		lastNorthLocked = config.northLocked;
		if (target == null) {
			target = new MapRenderTarget();
		}
		visibleMeshes.clear();
		terrain.collectVisible(position.x, position.z, viewReach(minecraft, position.y, zoom), visibleMeshes);

		boolean zZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
		lastViewProjection = MapCamera.viewProjection(zoom, yaw, config.northLocked, zZeroToOne);
		float pixelsPerBlock = pixels / (2f * MapCamera.radiusBlocks(zoom));
		float textureLod = config.simplifyDistantTextures ? MapCamera.textureLod(pixelsPerBlock) : 0f;
		target.render(pixels, lastViewProjection, textureLod, blockAtlas(minecraft), visibleMeshes, position.x, position.y, position.z);
	}

	private static String coordinates(Vec3 position) {
		return Mth.floor(position.x) + ", " + Mth.floor(position.y) + ", " + Mth.floor(position.z);
	}

	/** Centers the line under the map, sliding it sideways where it would leave the screen. */
	private static void drawInfoLine(GuiGraphicsExtractor graphics, Font font, Component text, MapLayout.Rect rect, int y) {
		int width = font.width(text);
		int x = Math.clamp(rect.centerX() - width / 2, INFO_PADDING, Math.max(INFO_PADDING, graphics.guiWidth() - width - INFO_PADDING));
		graphics.fill(x - INFO_PADDING, y - 1, x + width + INFO_PADDING, y + font.lineHeight, INFO_BACKDROP_COLOR);
		graphics.text(font, text, x, y, INFO_TEXT_COLOR, true);
	}

	private static void drawFrame(GuiGraphicsExtractor graphics, MapLayout.Rect rect) {
		int x = rect.x();
		int y = rect.y();
		int size = rect.size();
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
