package com.mehbur.minimap.render.terrain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Builds vertex data from a {@link ChunkSnapshot}. Holds reusable buffers, so use one instance per thread. */
public final class ChunkMesher {
	public static final int VERTEX_SIZE = 24;
	public static final int VERTICES_PER_QUAD = 4;
	public static final int INDICES_PER_QUAD = 6;

	/** Opaque and translucent geometry in chunk-local X/Z and world Y. */
	public record MeshData(ByteBuffer opaque, int opaqueQuads, ByteBuffer translucent, int translucentQuads) {
	}

	private static final int QUAD_SIZE = VERTEX_SIZE * VERTICES_PER_QUAD;
	private static final int INITIAL_CAPACITY = QUAD_SIZE * 1024;
	private static final float WATER_SURFACE_HEIGHT = 0.875f;
	private static final int MAX_WATER_SIDE_DEPTH = 8;

	private static final float TOP_SHADE = 1f;
	private static final float Z_SHADE = 0.8f;
	private static final float X_SHADE = 0.6f;

	private ByteBuffer opaque = allocate(INITIAL_CAPACITY);
	private ByteBuffer translucent = allocate(INITIAL_CAPACITY);

	public MeshData build(ChunkSnapshot snapshot) {
		opaque.clear();
		translucent.clear();
		for (int z = 0; z < ChunkSnapshot.SIZE; z++) {
			for (int x = 0; x < ChunkSnapshot.SIZE; x++) {
				ChunkSnapshot.Column column = snapshot.columns[ChunkSnapshot.columnIndex(x, z)];
				if (column != null) {
					addColumn(snapshot, column, x, z);
				}
			}
		}
		int opaqueQuads = opaque.position() / QUAD_SIZE;
		int translucentQuads = translucent.position() / QUAD_SIZE;
		return new MeshData(copy(opaque), opaqueQuads, copy(translucent), translucentQuads);
	}

	private void addColumn(ChunkSnapshot snapshot, ChunkSnapshot.Column column, int x, int z) {
		int y = snapshot.floorHeights[ChunkSnapshot.gridIndex(x, z)];
		if (column.top() != null) {
			float x1 = x + 1;
			float z1 = z + 1;
			float top = y + 1;
			// Corners are counter-clockwise seen from outside, starting at the sprite's top-left.
			opaque = quad(opaque, column.top(), color(column.topRgb(), TOP_SHADE), x, top, z, x, top, z1, x1, top, z1, x1, top, z);
		}
		if (column.sides().length > 0) {
			addFloorSides(snapshot, column, x, z, y);
		}
		if (column.under() != null) {
			float x1 = x + 1;
			float z1 = z + 1;
			float top = column.underY() + 1;
			opaque = quad(opaque, column.under(), color(column.underRgb(), TOP_SHADE), x, top, z, x, top, z1, x1, top, z1, x1, top, z);
		}
		if (column.water() != null) {
			addWater(snapshot, column, x, z);
		}
	}

	private void addFloorSides(ChunkSnapshot snapshot, ChunkSnapshot.Column column, int x, int z, int y) {
		for (int direction = 0; direction < 4; direction++) {
			int neighbor = snapshot.groundHeights[ChunkSnapshot.gridIndex(x + dx(direction), z + dz(direction))];
			if (neighbor == ChunkSnapshot.UNKNOWN) {
				continue;
			}
			int deepest = y - column.sides().length + 1;
			int bottom = neighbor == ChunkSnapshot.NO_BLOCK ? deepest : Math.max(neighbor + 1, deepest);
			float shade = shade(direction);
			for (int blockY = y; blockY >= bottom; blockY--) {
				int depth = y - blockY;
				FaceSprite sprite = column.sides()[depth];
				if (sprite != null) {
					opaque = sideQuad(opaque, sprite, color(column.sideRgbs()[depth], shade), direction, x, z, blockY, blockY + 1);
				}
			}
		}
	}

	private void addWater(ChunkSnapshot snapshot, ChunkSnapshot.Column column, int x, int z) {
		int waterTop = column.waterTop();
		float surface = waterTop + WATER_SURFACE_HEIGHT;
		float x1 = x + 1;
		float z1 = z + 1;
		translucent = quad(translucent, column.water(), color(column.waterRgb(), TOP_SHADE), x, surface, z, x, surface, z1, x1, surface, z1, x1, surface, z);
		for (int direction = 0; direction < 4; direction++) {
			int neighbor = snapshot.surfaceHeights[ChunkSnapshot.gridIndex(x + dx(direction), z + dz(direction))];
			if (neighbor == ChunkSnapshot.UNKNOWN || neighbor >= waterTop) {
				continue;
			}
			int deepest = waterTop - MAX_WATER_SIDE_DEPTH + 1;
			int bottom = neighbor == ChunkSnapshot.NO_BLOCK ? deepest : Math.max(neighbor + 1, deepest);
			int rgba = color(column.waterRgb(), shade(direction));
			for (int blockY = waterTop; blockY >= bottom; blockY--) {
				float top = blockY == waterTop ? surface : blockY + 1;
				translucent = sideQuad(translucent, column.water(), rgba, direction, x, z, blockY, top);
			}
		}
	}

	/** Directions in order: north, south, west, east. */
	private static int dx(int direction) {
		return direction == 2 ? -1 : direction == 3 ? 1 : 0;
	}

	private static int dz(int direction) {
		return direction == 0 ? -1 : direction == 1 ? 1 : 0;
	}

	private static float shade(int direction) {
		return direction < 2 ? Z_SHADE : X_SHADE;
	}

	private static ByteBuffer sideQuad(ByteBuffer out, FaceSprite sprite, int color, int direction, float x, float z, float y0, float y1) {
		float x1 = x + 1;
		float z1 = z + 1;
		return switch (direction) {
			case 0 -> quad(out, sprite, color, x1, y1, z, x1, y0, z, x, y0, z, x, y1, z);
			case 1 -> quad(out, sprite, color, x, y1, z1, x, y0, z1, x1, y0, z1, x1, y1, z1);
			case 2 -> quad(out, sprite, color, x, y1, z, x, y0, z, x, y0, z1, x, y1, z1);
			default -> quad(out, sprite, color, x1, y1, z1, x1, y0, z1, x1, y0, z, x1, y1, z);
		};
	}

	private static ByteBuffer quad(ByteBuffer out, FaceSprite sprite, int color,
		float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
		ByteBuffer buffer = ensureRoom(out);
		vertex(buffer, ax, ay, az, sprite.u0(), sprite.v0(), color);
		vertex(buffer, bx, by, bz, sprite.u0(), sprite.v1(), color);
		vertex(buffer, cx, cy, cz, sprite.u1(), sprite.v1(), color);
		vertex(buffer, dx, dy, dz, sprite.u1(), sprite.v0(), color);
		return buffer;
	}

	private static void vertex(ByteBuffer out, float x, float y, float z, float u, float v, int color) {
		out.putFloat(x).putFloat(y).putFloat(z).putFloat(u).putFloat(v).putInt(color);
	}

	/** Packs an RGB color scaled by {@code shade} into the opaque little-endian form the vertex format expects. */
	private static int color(int rgb, float shade) {
		int r = (int) (((rgb >> 16) & 0xFF) * shade);
		int g = (int) (((rgb >> 8) & 0xFF) * shade);
		int b = (int) ((rgb & 0xFF) * shade);
		return 0xFF000000 | (b << 16) | (g << 8) | r;
	}

	private static ByteBuffer ensureRoom(ByteBuffer buffer) {
		if (buffer.remaining() >= QUAD_SIZE) {
			return buffer;
		}
		ByteBuffer larger = allocate(buffer.capacity() * 2);
		buffer.flip();
		larger.put(buffer);
		return larger;
	}

	private static ByteBuffer copy(ByteBuffer buffer) {
		ByteBuffer copy = allocate(buffer.position());
		buffer.flip();
		copy.put(buffer);
		copy.flip();
		return copy;
	}

	private static ByteBuffer allocate(int capacity) {
		return ByteBuffer.allocateDirect(capacity).order(ByteOrder.LITTLE_ENDIAN);
	}
}
