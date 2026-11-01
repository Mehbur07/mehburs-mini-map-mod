package com.mehbur.minimap.render;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Turns positions relative to the map's camera into GUI coordinates. Holds the last result, so use one instance per caller. */
public final class MapProjector {
	private final Vector4f clip = new Vector4f();

	/** Returns false for points behind the camera, which have no place on the map. */
	public boolean project(Matrix4f viewProjection, double x, double y, double z) {
		clip.set((float) x, (float) y, (float) z, 1f);
		viewProjection.transform(clip);
		return clip.w > 0f;
	}

	public float guiX(MapLayout.Rect rect) {
		return rect.x() + (clip.x / clip.w + 1f) * 0.5f * rect.size();
	}

	public float guiY(MapLayout.Rect rect) {
		return rect.y() + (1f - clip.y / clip.w) * 0.5f * rect.size();
	}
}
