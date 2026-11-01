package com.mehbur.minimap.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

class MapCameraTest {
	private static final float TOLERANCE = 0.001f;
	private static final float YAW_SOUTH = 0f;
	private static final float YAW_WEST = 90f;
	private static final float YAW_NORTH = 180f;

	private static Vector4f project(Matrix4f matrix, float x, float y, float z) {
		Vector4f clip = matrix.transform(new Vector4f(x, y, z, 1f));
		return clip.div(clip.w);
	}

	@Test
	void playerIsAtTheExactCenter() {
		Matrix4f matrix = MapCamera.viewProjection(1f, 37f, false, false);

		Vector4f center = project(matrix, 0f, 0f, 0f);

		assertEquals(0f, center.x, TOLERANCE);
		assertEquals(0f, center.y, TOLERANCE);
	}

	@Test
	void northLockedKeepsNorthUpAndEastRightForAnyYaw() {
		Matrix4f matrix = MapCamera.viewProjection(1f, YAW_WEST, true, false);

		assertTrue(project(matrix, 0f, 0f, -10f).y > 0.1f, "north is up");
		assertTrue(project(matrix, 10f, 0f, 0f).x > 0.1f, "east is right");
	}

	@Test
	void rotatingMapPutsTheFacingDirectionUp() {
		assertTrue(project(MapCamera.viewProjection(1f, YAW_NORTH, false, false), 0f, 0f, -10f).y > 0.1f);
		assertTrue(project(MapCamera.viewProjection(1f, YAW_SOUTH, false, false), 0f, 0f, 10f).y > 0.1f);
		assertTrue(project(MapCamera.viewProjection(1f, YAW_WEST, false, false), -10f, 0f, 0f).y > 0.1f);
	}

	@Test
	void groundAtTheRadiusReachesTheMapEdgeSideways() {
		Matrix4f matrix = MapCamera.viewProjection(1f, 0f, true, false);

		assertEquals(1f, project(matrix, MapCamera.radiusBlocks(1f), 0f, 0f).x, TOLERANCE);
	}

	@Test
	void reachGrowsWithAltitudeOnly() {
		double atGround = MapCamera.viewReachBlocks(1f, 0.0);

		assertTrue(MapCamera.viewReachBlocks(1f, 100.0) > atGround);
		assertEquals(atGround, MapCamera.viewReachBlocks(1f, -100.0), TOLERANCE);
	}

	@Test
	void zoomingInShrinksTheVisibleRadius() {
		assertTrue(MapCamera.radiusBlocks(4f) < MapCamera.radiusBlocks(1f));
	}

	@Test
	void blendedZoomStaysBetweenItsEnds() {
		assertEquals(1f, MapCamera.blendZoom(1f, 0.25f, 0f), TOLERANCE);
		assertEquals(0.25f, MapCamera.blendZoom(1f, 0.25f, 1f), TOLERANCE);
		assertEquals(0.5f, MapCamera.blendZoom(1f, 0.25f, 0.5f), TOLERANCE);
	}

	@Test
	void textureLodPutsOneTexelOnEachPixel() {
		assertEquals(0f, MapCamera.textureLod(16f), TOLERANCE);
		assertEquals(0f, MapCamera.textureLod(40f), TOLERANCE);
		assertEquals(1f, MapCamera.textureLod(8f), TOLERANCE);
		assertEquals(2f, MapCamera.textureLod(4f), TOLERANCE);
		assertEquals(4f, MapCamera.textureLod(0.1f), TOLERANCE);
	}
}
