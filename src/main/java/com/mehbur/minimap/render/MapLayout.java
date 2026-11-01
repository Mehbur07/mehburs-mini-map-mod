package com.mehbur.minimap.render;

import com.mehbur.minimap.config.MinimapConfig;

/** Where the map sits on screen, in GUI units. {@code infoHeight} is the room kept under the map for its text lines. */
public final class MapLayout {
	private static final int MINI_MARGIN = 8;
	private static final int BIG_MARGIN = 12;
	private static final int MIN_SIZE = 16;

	public record Rect(int x, int y, int size) {
		public int centerX() {
			return x + size / 2;
		}

		public int centerY() {
			return y + size / 2;
		}

		public int bottom() {
			return y + size;
		}
	}

	private MapLayout() {
	}

	public static Rect mini(MinimapConfig.Corner corner, int mapSize, int guiWidth, int guiHeight, int infoHeight) {
		int size = fit(mapSize, guiWidth, guiHeight, MINI_MARGIN, infoHeight);
		boolean left = corner == MinimapConfig.Corner.TOP_LEFT || corner == MinimapConfig.Corner.BOTTOM_LEFT;
		boolean top = corner == MinimapConfig.Corner.TOP_LEFT || corner == MinimapConfig.Corner.TOP_RIGHT;
		int x = left ? MINI_MARGIN : guiWidth - MINI_MARGIN - size;
		int y = top ? MINI_MARGIN : guiHeight - MINI_MARGIN - infoHeight - size;
		return new Rect(x, y, size);
	}

	/** The largest square centered on screen that leaves room for the text lines. */
	public static Rect big(int guiWidth, int guiHeight, int infoHeight) {
		int size = fit(Integer.MAX_VALUE, guiWidth, guiHeight, BIG_MARGIN, infoHeight);
		return new Rect((guiWidth - size) / 2, (guiHeight - infoHeight - size) / 2, size);
	}

	public static Rect lerp(Rect from, Rect to, float amount) {
		return new Rect(lerp(from.x, to.x, amount), lerp(from.y, to.y, amount), lerp(from.size, to.size, amount));
	}

	private static int fit(int wanted, int guiWidth, int guiHeight, int margin, int infoHeight) {
		return Math.max(MIN_SIZE, Math.min(wanted, Math.min(guiWidth - 2 * margin, guiHeight - 2 * margin - infoHeight)));
	}

	private static int lerp(int from, int to, float amount) {
		return Math.round(from + (to - from) * amount);
	}
}
