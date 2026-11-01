package com.mehbur.minimap.render;

import com.mehbur.minimap.MehbursMiniMapClient;
import com.mehbur.minimap.config.MinimapConfig;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.joml.Matrix4f;

/** Draws the off-screen map into a screen corner. */
public final class MinimapHud implements AutoCloseable {
	private static final int MARGIN = 8;
	private static final long MIN_FRAME_MILLIS = 33;
	private static final float SPIN_MILLIS_PER_TURN = 8000f;
	private static final float CAMERA_DISTANCE = 4.5f;
	private static final float CAMERA_PITCH_RADIANS = (float) Math.toRadians(35);
	private static final float FOV_RADIANS = (float) Math.toRadians(45);
	private static final float NEAR_PLANE = 0.1f;
	private static final float FAR_PLANE = 32f;

	private MapRenderTarget target;
	private RenderProofMesh mesh;
	private long lastFrameMillis;

	public static void register() {
		MinimapHud hud = new MinimapHud();
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MehbursMiniMapClient.MOD_ID, "minimap"), hud::extract);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> hud.close());
	}

	private void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		MinimapConfig config = MehbursMiniMapClient.config();
		int size = Math.min(config.mapSize, Math.min(graphics.guiWidth(), graphics.guiHeight()) - 2 * MARGIN);
		int guiScale = minecraft.getWindow().getGuiScale();

		long now = Util.getMillis();
		if (target == null || now - lastFrameMillis >= MIN_FRAME_MILLIS) {
			lastFrameMillis = now;
			renderMap(minecraft, size * guiScale, now);
		}

		boolean left = config.corner == MinimapConfig.Corner.TOP_LEFT || config.corner == MinimapConfig.Corner.BOTTOM_LEFT;
		boolean top = config.corner == MinimapConfig.Corner.TOP_LEFT || config.corner == MinimapConfig.Corner.TOP_RIGHT;
		int x = left ? MARGIN : graphics.guiWidth() - MARGIN - size;
		int y = top ? MARGIN : graphics.guiHeight() - MARGIN - size;
		// Render targets store rows bottom-up, so V runs from 1 to 0.
		graphics.blit(target.colorView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST),
			x, y, x + size, y + size, 0f, 1f, 1f, 0f);
	}

	private void renderMap(Minecraft minecraft, int pixels, long now) {
		if (target == null) {
			target = new MapRenderTarget();
		}
		TextureAtlas atlas = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
		if (mesh != null && mesh.isStale(atlas)) {
			mesh.close();
			mesh = null;
		}
		if (mesh == null) {
			mesh = new RenderProofMesh(atlas);
		}
		target.render(pixels, pixels, viewProjection(now), pass -> drawMesh(pass, atlas));
	}

	private void drawMesh(RenderPass pass, TextureAtlas atlas) {
		pass.bindTexture(MapPipelines.ATLAS_SAMPLER, atlas.getTextureView(), atlas.getSampler());
		mesh.draw(pass);
	}

	private static Matrix4f viewProjection(long now) {
		boolean zZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
		float yaw = (now % (long) SPIN_MILLIS_PER_TURN) / SPIN_MILLIS_PER_TURN * (float) (Math.PI * 2);
		// Near and far are swapped because the game uses reversed depth.
		return new Matrix4f()
			.setPerspective(FOV_RADIANS, 1f, FAR_PLANE, NEAR_PLANE, zZeroToOne)
			.translate(0f, 0f, -CAMERA_DISTANCE)
			.rotateX(CAMERA_PITCH_RADIANS)
			.rotateY(yaw)
			.translate(-1f, -1.5f, -1f);
	}

	@Override
	public void close() {
		if (mesh != null) {
			mesh.close();
			mesh = null;
		}
		if (target != null) {
			target.close();
			target = null;
		}
	}
}
