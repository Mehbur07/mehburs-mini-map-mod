package com.mehbur.minimap.render.terrain;

/**
 * Decides which part of the world the map shows: the surface, or everything below a height near the player's eyes.
 * Waits before switching, so that a short trip through a cave mouth does not rebuild the whole map twice.
 */
public final class MapLayerTracker {
	/** The map shows the surface as seen from the sky. */
	public static final int SURFACE = Integer.MIN_VALUE;
	static final int SWITCH_DELAY_TICKS = 20;

	private int layerY = SURFACE;
	private int pendingTicks;

	/** Jumps straight to the right view, for changes of dimension where nothing on the map is worth keeping. */
	public int reset(boolean underground, int eyeBlockY) {
		pendingTicks = 0;
		layerY = underground ? eyeBlockY : SURFACE;
		return layerY;
	}

	/** Called once per tick. Returns the height the map should cut at, or {@link #SURFACE}. */
	public int update(boolean underground, int eyeBlockY) {
		boolean inLayer = layerY != SURFACE;
		if (underground == inLayer) {
			pendingTicks = 0;
		} else if (++pendingTicks >= SWITCH_DELAY_TICKS) {
			return reset(underground, eyeBlockY);
		}
		if (inLayer) {
			follow(eyeBlockY);
		}
		return layerY;
	}

	/**
	 * Going down must follow at once, or the cut would pass through the ceiling and hide the player's surroundings.
	 * Going up may lag by one block, which keeps jumping from rebuilding the map.
	 */
	private void follow(int eyeBlockY) {
		if (eyeBlockY < layerY || eyeBlockY > layerY + 1) {
			layerY = eyeBlockY;
		}
	}
}
