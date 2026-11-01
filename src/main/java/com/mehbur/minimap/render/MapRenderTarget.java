package com.mehbur.minimap.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

/** Off-screen color and depth textures the map is drawn into before the HUD samples the result. */
final class MapRenderTarget implements AutoCloseable {
	private static final int CAMERA_UBO_SIZE = 64;
	private static final int COLOR_USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
	private static final int DEPTH_USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT;
	private static final double CLEAR_DEPTH = 0.0; // The game uses reversed depth, so 0 is the far plane.
	private static final Vector4f CLEAR_COLOR = new Vector4f(0.02f, 0.03f, 0.05f, 1f);

	private final GpuBuffer cameraBuffer;
	private GpuTexture colorTexture;
	private GpuTextureView colorView;
	private GpuTexture depthTexture;
	private GpuTextureView depthView;

	MapRenderTarget() {
		cameraBuffer = RenderSystem.getDevice().createBuffer(() -> "Mehbur's Mini Map camera UBO",
			GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, CAMERA_UBO_SIZE);
	}

	/** Clears the target to {@code width} x {@code height} pixels and runs {@code draw} inside a render pass. */
	void render(int width, int height, Matrix4f viewProjection, Consumer<RenderPass> draw) {
		resize(width, height);
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ByteBuffer camera = Std140Builder.onStack(stack, CAMERA_UBO_SIZE).putMat4f(viewProjection).get();
			encoder.writeToBuffer(cameraBuffer.slice(), camera);
		}
		try (RenderPass pass = encoder.createRenderPass(() -> "Mehbur's Mini Map",
			colorView, Optional.of(CLEAR_COLOR), depthView, OptionalDouble.of(CLEAR_DEPTH))) {
			pass.setPipeline(MapPipelines.TERRAIN);
			pass.setUniform(MapPipelines.CAMERA_UNIFORM, cameraBuffer);
			draw.accept(pass);
		}
	}

	GpuTextureView colorView() {
		return colorView;
	}

	private void resize(int width, int height) {
		if (colorTexture != null && colorTexture.getWidth(0) == width && colorTexture.getHeight(0) == height) {
			return;
		}
		closeTextures();
		GpuDevice device = RenderSystem.getDevice();
		colorTexture = device.createTexture(() -> "Mehbur's Mini Map color", COLOR_USAGE, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
		colorView = device.createTextureView(colorTexture);
		depthTexture = device.createTexture(() -> "Mehbur's Mini Map depth", DEPTH_USAGE, GpuFormat.D32_FLOAT, width, height, 1, 1);
		depthView = device.createTextureView(depthTexture);
	}

	private void closeTextures() {
		if (colorTexture != null) {
			colorTexture.close();
			colorView.close();
			depthTexture.close();
			depthView.close();
			colorTexture = null;
		}
	}

	@Override
	public void close() {
		closeTextures();
		cameraBuffer.close();
	}
}
