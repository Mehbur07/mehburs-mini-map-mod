package com.mehbur.minimap.death;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MapGeometryTest {
	private static final float TOLERANCE = 0.001f;

	private static MapGeometry.Point clamp(float targetX, float targetY) {
		return MapGeometry.clampAlongRay(50f, 50f, targetX, targetY, 0f, 0f, 100f, 100f);
	}

	@Test
	void aTargetInsideStaysWhereItIs() {
		assertEquals(new MapGeometry.Point(70f, 30f), clamp(70f, 30f));
	}

	@Test
	void theCenterStaysWhereItIs() {
		assertEquals(new MapGeometry.Point(50f, 50f), clamp(50f, 50f));
	}

	@Test
	void straightDirectionsStopAtTheirEdge() {
		assertEquals(new MapGeometry.Point(100f, 50f), clamp(500f, 50f));
		assertEquals(new MapGeometry.Point(0f, 50f), clamp(-500f, 50f));
		assertEquals(new MapGeometry.Point(50f, 0f), clamp(50f, -500f));
		assertEquals(new MapGeometry.Point(50f, 100f), clamp(50f, 500f));
	}

	@Test
	void diagonalsStopInTheCorner() {
		MapGeometry.Point point = clamp(500f, -400f);

		assertEquals(100f, point.x(), TOLERANCE);
		assertEquals(0f, point.y(), TOLERANCE);
	}

	@Test
	void shallowAnglesKeepTheirDirection() {
		MapGeometry.Point point = clamp(250f, 100f);

		assertEquals(100f, point.x(), TOLERANCE);
		assertEquals(62.5f, point.y(), TOLERANCE);
	}
}
