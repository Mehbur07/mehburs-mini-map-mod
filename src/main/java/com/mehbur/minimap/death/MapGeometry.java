package com.mehbur.minimap.death;

/** Plain 2D math for marks on the map. */
final class MapGeometry {
	record Point(float x, float y) {
	}

	private MapGeometry() {
	}

	/**
	 * Follows the ray from a point inside a rectangle toward a target and stops at the rectangle's edge.
	 * Returns the target itself when it lies inside.
	 */
	static Point clampAlongRay(float fromX, float fromY, float targetX, float targetY, float minX, float minY, float maxX, float maxY) {
		float dx = targetX - fromX;
		float dy = targetY - fromY;
		float scale = 1f;
		if (dx > 0f) {
			scale = Math.min(scale, (maxX - fromX) / dx);
		} else if (dx < 0f) {
			scale = Math.min(scale, (minX - fromX) / dx);
		}
		if (dy > 0f) {
			scale = Math.min(scale, (maxY - fromY) / dy);
		} else if (dy < 0f) {
			scale = Math.min(scale, (minY - fromY) / dy);
		}
		return scale == 1f ? new Point(targetX, targetY) : new Point(fromX + dx * scale, fromY + dy * scale);
	}
}
