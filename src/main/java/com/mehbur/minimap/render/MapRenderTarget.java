package com.mehbur.minimap.render;

import com.mehbur.minimap.render.terrain.ChunkMesh;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

/** Off-screen color and depth textures the map is drawn into before the HUD samples the result. */
final class MapRenderTarget implements AutoCloseable {
	private static final int CAMERA_UBO_SIZE = 80;
	private static final int CHUNK_UBO_SIZE = 16;
	private static final int COLOR_USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
	private static final int DEPTH_USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT;
	private static final double CLEAR_DEPTH = 0.0; // The game uses reversed depth, so 0 is the far plane.
	private static final Vector4f CLEAR_COLOR = new Vector4f(0.02f, 0.03f, 0.05f, 1f);

	private final GpuBuffer cameraBuffer;
	private final int chunkStride;
	private GpuBuffer chunkBuffer;
	private ByteBuffer chunkData;
	private int chunkCapacity;
	private GpuTexture colorTexture;
	private GpuTextureView colorView;
	private GpuTexture depthTexture;
	private GpuTextureView depthView;

	MapRenderTarget() {
		GpuDevice device = RenderSystem.getDevice();
		cameraBuffer = device.createBuffer(() -> "Mehbur's Mini Map camera UBO", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, CAMERA_UBO_SIZE);
		int alignment = device.getDeviceInfo().limits().minUniformOffsetAlignment();
		chunkStride = Math.ceilDiv(CHUNK_UBO_SIZE, alignment) * alignment;
	}

	/**
	 * Draws {@code meshes} with the player at the origin.
	 *
	 * @param minTextureLod forces this mipmap level to simplify distant textures; 0 samples normally
	 */
	void render(int pixels, Matrix4f viewProjection, float minTextureLod, TextureAtlas atlas, List<ChunkMesh> meshes, double playerX, double playerY, double playerZ) {
		resize(pixels);
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ByteBuffer camera = Std140Builder.onStack(stack, CAMERA_UBO_SIZE).putMat4f(viewProjection).putFloat(minTextureLod).get();
			encoder.writeToBuffer(cameraBuffer.slice(), camera);
		}
		writeChunkOffsets(encoder, meshes, playerX, playerY, playerZ);

		// The index buffer may grow, which is not allowed once the pass is open.
		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		int maxIndices = 0;
		for (ChunkMesh mesh : meshes) {
			maxIndices = Math.max(maxIndices, Math.max(mesh.opaqueIndices, mesh.translucentIndices));
		}
		GpuBuffer indexBuffer = indices.getBuffer(Math.max(maxIndices, 6));

		try (RenderPass pass = encoder.createRenderPass(() -> "Mehbur's Mini Map",
			colorView, Optional.of(CLEAR_COLOR), depthView, OptionalDouble.of(CLEAR_DEPTH))) {
			pass.setPipeline(MapPipelines.OPAQUE);
			pass.setUniform(MapPipelines.CAMERA_UNIFORM, cameraBuffer);
			pass.bindTexture(MapPipelines.ATLAS_SAMPLER, atlas.getTextureView(), atlas.getSampler());
			pass.setIndexBuffer(indexBuffer, indices.type());
			for (int i = 0; i < meshes.size(); i++) {
				ChunkMesh mesh = meshes.get(i);
				if (mesh.opaque != null) {
					pass.setUniform(MapPipelines.CHUNK_UNIFORM, chunkBuffer.slice((long) i * chunkStride, CHUNK_UBO_SIZE));
					pass.setVertexBuffer(0, mesh.opaque.slice());
					pass.drawIndexed(mesh.opaqueIndices, 1, 0, 0, 0);
				}
			}
			pass.setPipeline(MapPipelines.TRANSLUCENT);
			for (int i = 0; i < meshes.size(); i++) {
				ChunkMesh mesh = meshes.get(i);
				if (mesh.translucent != null) {
					pass.setUniform(MapPipelines.CHUNK_UNIFORM, chunkBuffer.slice((long) i * chunkStride, CHUNK_UBO_SIZE));
					pass.setVertexBuffer(0, mesh.translucent.slice());
					pass.drawIndexed(mesh.translucentIndices, 1, 0, 0, 0);
				}
			}
		}
	}

	private void writeChunkOffsets(CommandEncoder encoder, List<ChunkMesh> meshes, double playerX, double playerY, double playerZ) {
		int count = Math.max(meshes.size(), 1);
		if (count > chunkCapacity) {
			if (chunkBuffer != null) {
				chunkBuffer.close();
			}
			chunkCapacity = Math.max(count * 2, 128);
			chunkBuffer = RenderSystem.getDevice().createBuffer(() -> "Mehbur's Mini Map chunk UBO",
				GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, (long) chunkCapacity * chunkStride);
			chunkData = ByteBuffer.allocateDirect(chunkCapacity * chunkStride).order(ByteOrder.nativeOrder());
		}
		chunkData.clear();
		for (int i = 0; i < meshes.size(); i++) {
			ChunkMesh mesh = meshes.get(i);
			chunkData.position(i * chunkStride);
			// Subtracting in double precision keeps the map steady far from the world origin.
			chunkData.putFloat((float) ((mesh.chunkX << 4) - playerX));
			chunkData.putFloat((float) -playerY);
			chunkData.putFloat((float) ((mesh.chunkZ << 4) - playerZ));
		}
		chunkData.position(0).limit(count * chunkStride);
		encoder.writeToBuffer(chunkBuffer.slice(0L, (long) count * chunkStride), chunkData);
	}

	GpuTextureView colorView() {
		return colorView;
	}

	private void resize(int pixels) {
		if (colorTexture != null && colorTexture.getWidth(0) == pixels) {
			return;
		}
		closeTextures();
		GpuDevice device = RenderSystem.getDevice();
		colorTexture = device.createTexture(() -> "Mehbur's Mini Map color", COLOR_USAGE, GpuFormat.RGBA8_UNORM, pixels, pixels, 1, 1);
		colorView = device.createTextureView(colorTexture);
		depthTexture = device.createTexture(() -> "Mehbur's Mini Map depth", DEPTH_USAGE, GpuFormat.D32_FLOAT, pixels, pixels, 1, 1);
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
		if (chunkBuffer != null) {
			chunkBuffer.close();
		}
	}
}
