package com.mehbur.minimap.render.terrain;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;

/** A chunk's vertex data on the GPU. Each buffer is null when that part of the mesh is empty. */
public final class ChunkMesh implements AutoCloseable {
	public final int chunkX;
	public final int chunkZ;
	public final GpuBuffer opaque;
	public final int opaqueIndices;
	public final GpuBuffer translucent;
	public final int translucentIndices;
	final long bytes;

	ChunkMesh(int chunkX, int chunkZ, ChunkMesher.MeshData data) {
		this.chunkX = chunkX;
		this.chunkZ = chunkZ;
		opaque = data.opaqueQuads() == 0 ? null : upload("opaque", data.opaque());
		opaqueIndices = data.opaqueQuads() * ChunkMesher.INDICES_PER_QUAD;
		translucent = data.translucentQuads() == 0 ? null : upload("translucent", data.translucent());
		translucentIndices = data.translucentQuads() * ChunkMesher.INDICES_PER_QUAD;
		bytes = data.opaque().remaining() + (long) data.translucent().remaining();
	}

	private static GpuBuffer upload(String kind, ByteBuffer vertices) {
		return RenderSystem.getDevice().createBuffer(() -> "Mehbur's Mini Map " + kind + " chunk", GpuBuffer.USAGE_VERTEX, vertices);
	}

	@Override
	public void close() {
		if (opaque != null) {
			opaque.close();
		}
		if (translucent != null) {
			translucent.close();
		}
	}
}
