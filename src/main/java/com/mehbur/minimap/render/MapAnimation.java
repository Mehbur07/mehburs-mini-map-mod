package com.mehbur.minimap.render;

/** Tracks how far the map has grown from its corner toward the big map. Reversing midway continues from where it is. */
public final class MapAnimation {
	private float progress;
	private boolean open;
	private long lastMillis = -1L;

	public void update(boolean open, long nowMillis, int durationMillis) {
		long elapsed = lastMillis < 0L ? 0L : nowMillis - lastMillis;
		lastMillis = nowMillis;
		this.open = open;
		float step = durationMillis <= 0 ? 1f : (float) elapsed / durationMillis;
		progress = open ? Math.min(1f, progress + step) : Math.max(0f, progress - step);
	}

	/** Progress with ease-in and ease-out applied, from 0 in the corner to 1 fully open. */
	public float eased() {
		return progress * progress * (3f - 2f * progress);
	}

	public boolean isClosed() {
		return progress == 0f && !open;
	}

	public boolean isMoving() {
		return progress != (open ? 1f : 0f);
	}
}
