package com.mehbur.minimap.render.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ChunkMesherTest {
	private static final FaceSprite SPRITE = new FaceSprite(0f, 0f, 1f, 1f, -1);
	private static final int HEIGHT = 64;
	private static final int WHITE = 0xFFFFFF;

	private final ChunkMesher mesher = new ChunkMesher();

	/** A single solid column at local (5, 5), surrounded by columns at the given height. */
	private static ChunkSnapshot singleColumn(int height, int neighborHeight, int sideDepth) {
		ChunkSnapshot snapshot = new ChunkSnapshot(0, 0);
		Arrays.fill(snapshot.floorHeights, neighborHeight);
		Arrays.fill(snapshot.groundHeights, neighborHeight);
		Arrays.fill(snapshot.surfaceHeights, neighborHeight);
		snapshot.floorHeights[ChunkSnapshot.gridIndex(5, 5)] = height;
		snapshot.groundHeights[ChunkSnapshot.gridIndex(5, 5)] = height;
		snapshot.surfaceHeights[ChunkSnapshot.gridIndex(5, 5)] = height;
		FaceSprite[] sides = new FaceSprite[sideDepth];
		Arrays.fill(sides, SPRITE);
		snapshot.columns[ChunkSnapshot.columnIndex(5, 5)] =
			new ChunkSnapshot.Column(SPRITE, WHITE, sides, new int[sideDepth], ChunkSnapshot.NO_BLOCK, null, WHITE, null, WHITE, 0);
		return snapshot;
	}

	@Test
	void flatGroundOnlyHasTopFace() {
		assertEquals(1, mesher.build(singleColumn(HEIGHT, HEIGHT, 0)).opaqueQuads());
	}

	@Test
	void stepAddsOneSideQuadPerExposedBlockOnEachSide() {
		ChunkMesher.MeshData mesh = mesher.build(singleColumn(HEIGHT, HEIGHT - 3, 3));

		assertEquals(1 + 4 * 3, mesh.opaqueQuads());
	}

	@Test
	void tallerNeighborsHideSideFaces() {
		assertEquals(1, mesher.build(singleColumn(HEIGHT, HEIGHT + 5, 0)).opaqueQuads());
	}

	@Test
	void unloadedNeighborsGetNoSideFaces() {
		assertEquals(1, mesher.build(singleColumn(HEIGHT, ChunkSnapshot.UNKNOWN, 3)).opaqueQuads());
	}

	@Test
	void sidesAreLimitedToTheSnapshotDepth() {
		ChunkMesher.MeshData mesh = mesher.build(singleColumn(HEIGHT, HEIGHT - 100, 2));

		assertEquals(1 + 4 * 2, mesh.opaqueQuads());
	}

	@Test
	void missingSideSpritesAreSkipped() {
		ChunkSnapshot snapshot = singleColumn(HEIGHT, HEIGHT - 2, 2);
		ChunkSnapshot.Column column = snapshot.columns[ChunkSnapshot.columnIndex(5, 5)];
		column.sides()[1] = null;

		assertEquals(1 + 4, mesher.build(snapshot).opaqueQuads());
	}

	@Test
	void waterGoesToTheTranslucentMesh() {
		ChunkSnapshot snapshot = singleColumn(HEIGHT, HEIGHT, 0);
		snapshot.surfaceHeights[ChunkSnapshot.gridIndex(5, 5)] = HEIGHT + 3;
		Arrays.fill(snapshot.surfaceHeights, HEIGHT + 3);
		snapshot.columns[ChunkSnapshot.columnIndex(5, 5)] =
			new ChunkSnapshot.Column(SPRITE, WHITE, new FaceSprite[0], new int[0], HEIGHT + 3, SPRITE, WHITE, null, WHITE, 0);

		ChunkMesher.MeshData mesh = mesher.build(snapshot);

		assertEquals(1, mesh.opaqueQuads());
		assertEquals(1, mesh.translucentQuads());
	}

	@Test
	void groundBelowAGapGetsItsOwnTopFace() {
		ChunkSnapshot snapshot = singleColumn(HEIGHT, HEIGHT, 0);
		snapshot.columns[ChunkSnapshot.columnIndex(5, 5)] =
			new ChunkSnapshot.Column(SPRITE, WHITE, new FaceSprite[0], new int[0], ChunkSnapshot.NO_BLOCK, null, WHITE, SPRITE, WHITE, HEIGHT - 6);

		assertEquals(2, mesher.build(snapshot).opaqueQuads());
	}

	@Test
	void topFaceIsAboveTheBlockInWorldCoordinates() {
		ByteBuffer vertices = mesher.build(singleColumn(HEIGHT, HEIGHT, 0)).opaque();

		assertEquals(5f, vertices.getFloat(0));
		assertEquals(HEIGHT + 1f, vertices.getFloat(4));
		assertEquals(5f, vertices.getFloat(8));
	}
}
