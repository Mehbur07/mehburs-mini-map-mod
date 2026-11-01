package com.mehbur.minimap.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MapAnimationTest {
	private static final int DURATION = 100;
	private static final float EPSILON = 0.0001f;

	private final MapAnimation animation = new MapAnimation();

	@Test
	void startsClosedAndStill() {
		animation.update(false, 0L, DURATION);

		assertTrue(animation.isClosed());
		assertFalse(animation.isMoving());
		assertEquals(0f, animation.eased());
	}

	@Test
	void opensOverTheDuration() {
		animation.update(true, 0L, DURATION);
		assertTrue(animation.isMoving());
		assertFalse(animation.isClosed());

		animation.update(true, 50L, DURATION);
		assertEquals(0.5f, animation.eased(), EPSILON);
		assertTrue(animation.isMoving());

		animation.update(true, 100L, DURATION);
		assertEquals(1f, animation.eased(), EPSILON);
		assertFalse(animation.isMoving());
	}

	@Test
	void easingStartsAndEndsSlowly() {
		animation.update(true, 0L, DURATION);
		animation.update(true, 10L, DURATION);

		assertTrue(animation.eased() < 0.1f);
	}

	@Test
	void reversingMidwayContinuesFromThere() {
		animation.update(true, 0L, DURATION);
		animation.update(true, 75L, DURATION);
		animation.update(false, 100L, DURATION);

		assertEquals(0.5f, animation.eased(), EPSILON);
		assertTrue(animation.isMoving());

		animation.update(false, 150L, DURATION);
		assertTrue(animation.isClosed());
	}

	@Test
	void zeroDurationSwitchesAtOnce() {
		animation.update(true, 0L, 0);

		assertEquals(1f, animation.eased());
		assertFalse(animation.isMoving());
	}

	@Test
	void aLongGapBetweenFramesDoesNotOvershoot() {
		animation.update(true, 0L, DURATION);
		animation.update(true, 100_000L, DURATION);

		assertEquals(1f, animation.eased());
	}
}
