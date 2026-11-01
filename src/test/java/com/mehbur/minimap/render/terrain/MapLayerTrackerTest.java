package com.mehbur.minimap.render.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MapLayerTrackerTest {
	private static final int EYE = 20;

	private final MapLayerTracker tracker = new MapLayerTracker();

	private int runTicks(int ticks, boolean underground, int eye) {
		int layer = MapLayerTracker.SURFACE;
		for (int i = 0; i < ticks; i++) {
			layer = tracker.update(underground, eye);
		}
		return layer;
	}

	@Test
	void entersACaveOnlyAfterTheDelay() {
		assertEquals(MapLayerTracker.SURFACE, runTicks(MapLayerTracker.SWITCH_DELAY_TICKS - 1, true, EYE));
		assertEquals(EYE, tracker.update(true, EYE));
	}

	@Test
	void aShortTripUndergroundDoesNotSwitch() {
		runTicks(MapLayerTracker.SWITCH_DELAY_TICKS - 1, true, EYE);
		tracker.update(false, EYE);

		assertEquals(MapLayerTracker.SURFACE, runTicks(MapLayerTracker.SWITCH_DELAY_TICKS - 1, true, EYE));
	}

	@Test
	void leavesACaveOnlyAfterTheDelay() {
		tracker.reset(true, EYE);

		assertEquals(EYE, runTicks(MapLayerTracker.SWITCH_DELAY_TICKS - 1, false, EYE));
		assertEquals(MapLayerTracker.SURFACE, tracker.update(false, EYE));
	}

	@Test
	void resetSwitchesAtOnce() {
		assertEquals(EYE, tracker.reset(true, EYE));
		assertEquals(MapLayerTracker.SURFACE, tracker.reset(false, EYE));
	}

	@Test
	void goingDownFollowsAtOnce() {
		tracker.reset(true, EYE);

		assertEquals(EYE - 1, tracker.update(true, EYE - 1));
	}

	@Test
	void jumpingDoesNotMoveTheLayer() {
		tracker.reset(true, EYE);

		assertEquals(EYE, tracker.update(true, EYE + 1));
		assertEquals(EYE, tracker.update(true, EYE));
	}

	@Test
	void climbingMovesTheLayerOnceTheEyeIsTwoBlocksHigher() {
		tracker.reset(true, EYE);

		assertEquals(EYE + 2, tracker.update(true, EYE + 2));
	}

	@Test
	void theLayerKeepsFollowingWhileWaitingToLeave() {
		tracker.reset(true, EYE);

		assertEquals(EYE - 3, tracker.update(false, EYE - 3));
	}

	@Test
	void theLayerIgnoresHeightOnTheSurface() {
		assertEquals(MapLayerTracker.SURFACE, tracker.update(false, EYE - 5));
	}
}
