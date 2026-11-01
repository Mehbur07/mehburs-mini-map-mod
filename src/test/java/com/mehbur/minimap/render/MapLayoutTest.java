package com.mehbur.minimap.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mehbur.minimap.config.MinimapConfig;
import org.junit.jupiter.api.Test;

class MapLayoutTest {
	private static final int WIDTH = 480;
	private static final int HEIGHT = 270;
	private static final int INFO = 23;

	@Test
	void topLeftSitsInTheCorner() {
		assertEquals(new MapLayout.Rect(8, 8, 80), MapLayout.mini(MinimapConfig.Corner.TOP_LEFT, 80, WIDTH, HEIGHT, INFO));
	}

	@Test
	void rightCornersTouchTheRightMargin() {
		MapLayout.Rect rect = MapLayout.mini(MinimapConfig.Corner.TOP_RIGHT, 80, WIDTH, HEIGHT, INFO);

		assertEquals(WIDTH - 8, rect.x() + rect.size());
	}

	@Test
	void bottomCornersKeepRoomForTheTextUnderTheMap() {
		MapLayout.Rect rect = MapLayout.mini(MinimapConfig.Corner.BOTTOM_LEFT, 80, WIDTH, HEIGHT, INFO);

		assertEquals(HEIGHT - 8, rect.bottom() + INFO);
	}

	@Test
	void miniMapShrinksToFitSmallWindows() {
		MapLayout.Rect rect = MapLayout.mini(MinimapConfig.Corner.TOP_LEFT, 512, WIDTH, HEIGHT, INFO);

		assertEquals(HEIGHT - 2 * 8 - INFO, rect.size());
	}

	@Test
	void bigMapIsCenteredAndLeavesRoomForTheText() {
		MapLayout.Rect rect = MapLayout.big(WIDTH, HEIGHT, INFO);

		assertEquals(HEIGHT - 2 * 12 - INFO, rect.size());
		assertEquals(WIDTH / 2, rect.centerX(), 1);
		assertTrue(rect.bottom() + INFO <= HEIGHT);
		assertTrue(rect.y() >= 0);
	}

	@Test
	void bigMapFitsAPortraitWindow() {
		MapLayout.Rect rect = MapLayout.big(200, 600, INFO);

		assertEquals(200 - 2 * 12, rect.size());
	}

	@Test
	void lerpReachesBothEnds() {
		MapLayout.Rect from = new MapLayout.Rect(8, 8, 80);
		MapLayout.Rect to = new MapLayout.Rect(100, 12, 223);

		assertEquals(from, MapLayout.lerp(from, to, 0f));
		assertEquals(to, MapLayout.lerp(from, to, 1f));
		assertEquals(new MapLayout.Rect(54, 10, 152), MapLayout.lerp(from, to, 0.5f));
	}
}
