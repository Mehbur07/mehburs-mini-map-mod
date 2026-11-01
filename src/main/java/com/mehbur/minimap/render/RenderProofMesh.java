package com.mehbur.minimap.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

/**
 * A fixed block staircase proving the map pipeline works. Terrain meshing replaces it in the next phase.
 * The nearest block is written first, so the picture is only correct if the depth test works.
 */
final class RenderProofMesh implements AutoCloseable {
	private static final int VERTEX_SIZE = 24;
	private static final int FACES_PER_BLOCK = 5;
	private static final int BLOCKS = 3;
	private static final int VERTEX_COUNT = BLOCKS * FACES_PER_BLOCK * 4;
	private static final int INDEX_COUNT = BLOCKS * FACES_PER_BLOCK * 6;

	private static final int WHITE = 0xFFFFFF;
	private static final int GRASS_TINT = 0x91BD59;
	private static final float TOP_SHADE = 1f;
	private static final float X_SHADE = 0.6f;
	private static final float Z_SHADE = 0.8f;

	private final GpuBuffer vertices;
	private final TextureAtlasSprite firstSprite;

	RenderProofMesh(TextureAtlas atlas) {
		firstSprite = sprite(atlas, "grass_block_top");
		ByteBuffer data = MemoryUtil.memAlloc(VERTEX_COUNT * VERTEX_SIZE).order(ByteOrder.nativeOrder());
		try {
			block(data, 0, 0, 1, firstSprite, GRASS_TINT, sprite(atlas, "grass_block_side"));
			block(data, 0, 1, 0, sprite(atlas, "stone"), WHITE, sprite(atlas, "stone"));
			block(data, 1, 2, 0, sprite(atlas, "oak_log_top"), WHITE, sprite(atlas, "oak_log"));
			data.flip();
			vertices = RenderSystem.getDevice().createBuffer(() -> "Mehbur's Mini Map proof mesh", GpuBuffer.USAGE_VERTEX, data);
		} finally {
			MemoryUtil.memFree(data);
		}
	}

	/** Atlas coordinates change when resource packs reload, which makes this mesh stale. */
	boolean isStale(TextureAtlas atlas) {
		return sprite(atlas, "grass_block_top") != firstSprite;
	}

	void draw(RenderPass pass) {
		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		pass.setVertexBuffer(0, vertices.slice());
		pass.setIndexBuffer(indices.getBuffer(INDEX_COUNT), indices.type());
		pass.drawIndexed(INDEX_COUNT, 1, 0, 0, 0);
	}

	private static TextureAtlasSprite sprite(TextureAtlas atlas, String name) {
		return atlas.getSprite(Identifier.withDefaultNamespace("block/" + name));
	}

	private static void block(ByteBuffer out, float x, float y, float z, TextureAtlasSprite top, int topTint, TextureAtlasSprite side) {
		float x1 = x + 1;
		float y1 = y + 1;
		float z1 = z + 1;
		int topColor = color(topTint, TOP_SHADE);
		int xColor = color(WHITE, X_SHADE);
		int zColor = color(WHITE, Z_SHADE);
		// Corners are counter-clockwise seen from outside, starting at the sprite's top-left.
		quad(out, top, topColor, x, y1, z, x, y1, z1, x1, y1, z1, x1, y1, z);
		quad(out, side, zColor, x1, y1, z, x1, y, z, x, y, z, x, y1, z);
		quad(out, side, zColor, x, y1, z1, x, y, z1, x1, y, z1, x1, y1, z1);
		quad(out, side, xColor, x, y1, z, x, y, z, x, y, z1, x, y1, z1);
		quad(out, side, xColor, x1, y1, z1, x1, y, z1, x1, y, z, x1, y1, z);
	}

	private static void quad(ByteBuffer out, TextureAtlasSprite sprite, int color,
		float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
		vertex(out, ax, ay, az, sprite.getU0(), sprite.getV0(), color);
		vertex(out, bx, by, bz, sprite.getU0(), sprite.getV1(), color);
		vertex(out, cx, cy, cz, sprite.getU1(), sprite.getV1(), color);
		vertex(out, dx, dy, dz, sprite.getU1(), sprite.getV0(), color);
	}

	private static void vertex(ByteBuffer out, float x, float y, float z, float u, float v, int abgr) {
		out.putFloat(x).putFloat(y).putFloat(z).putFloat(u).putFloat(v).putInt(abgr);
	}

	/** Packs an RGB color scaled by {@code shade} into the opaque little-endian form the vertex format expects. */
	private static int color(int rgb, float shade) {
		int r = (int) (((rgb >> 16) & 0xFF) * shade);
		int g = (int) (((rgb >> 8) & 0xFF) * shade);
		int b = (int) ((rgb & 0xFF) * shade);
		return 0xFF000000 | (b << 16) | (g << 8) | r;
	}

	@Override
	public void close() {
		vertices.close();
	}
}
