package com.mehbur.minimap.render;

import org.joml.Matrix4f;

/** The tilted camera that looks at the player, who stays at the world origin of the map's coordinates. */
public final class MapCamera {
	private static final float BASE_RADIUS_BLOCKS = 32f;
	private static final float PITCH_RADIANS = (float) Math.toRadians(55);
	private static final float FOV_RADIANS = (float) Math.toRadians(30);
	private static final float NEAR_PLANE = 1f;
	private static final float EXTRA_FAR_BLOCKS = 1024f;
	/** Tilt and perspective show more ground past the top edge than the plain radius. */
	private static final double VIEW_REACH = 1.8;
	/** The map's top edge looks 40 degrees below the horizon, so ground further down is seen further away. */
	private static final double REACH_PER_BLOCK_OF_ALTITUDE = 1.2;
	private static final float BLOCK_TEXTURE_SIZE = 16f;
	/** The game's highest mipmap setting. */
	private static final double MAX_TEXTURE_LOD = 4.0;

	private MapCamera() {
	}

	/** Blocks from the map's center to its edge at a zoom level. */
	public static float radiusBlocks(float zoom) {
		return BASE_RADIUS_BLOCKS / zoom;
	}

	/**
	 * The mipmap level that puts one texel on each screen pixel. Sampling more texels than pixels leaves wave
	 * patterns across flat ground, so one fixed level for the whole map looks steadier than per-pixel selection.
	 */
	public static float textureLod(float pixelsPerBlock) {
		double lod = Math.log(BLOCK_TEXTURE_SIZE / pixelsPerBlock) / Math.log(2.0);
		return (float) Math.clamp(lod, 0.0, MAX_TEXTURE_LOD);
	}

	/** A zoom between two levels. Interpolating logarithmically makes the view scale at an even pace. */
	public static float blendZoom(float from, float to, float amount) {
		if (from == to || amount <= 0f) {
			return from;
		}
		return amount >= 1f ? to : (float) Math.exp(Math.log(from) + (Math.log(to) - Math.log(from)) * amount);
	}

	/**
	 * Blocks from the player within which terrain can be on the map.
	 *
	 * @param altitude how far above typical ground the player is; negative values count as zero
	 */
	public static double viewReachBlocks(float zoom, double altitude) {
		return radiusBlocks(zoom) * VIEW_REACH + Math.max(altitude, 0.0) * REACH_PER_BLOCK_OF_ALTITUDE;
	}

	/**
	 * @param zoom        map zoom
	 * @param yawDegrees  the player's yaw
	 * @param northLocked keeps north up instead of the player's facing direction
	 * @param zZeroToOne  whether the backend's clip space depth runs from 0 to 1
	 */
	public static Matrix4f viewProjection(float zoom, float yawDegrees, boolean northLocked, boolean zZeroToOne) {
		float radius = radiusBlocks(zoom);
		float distance = radius / (float) Math.tan(FOV_RADIANS / 2);
		// Rotating by yaw + 180 degrees turns the direction the player faces toward the top of the map.
		float rotation = northLocked ? 0f : (float) Math.toRadians(yawDegrees + 180f);
		// Near and far are swapped because the game uses reversed depth.
		return new Matrix4f()
			.setPerspective(FOV_RADIANS, 1f, distance + EXTRA_FAR_BLOCKS, NEAR_PLANE, zZeroToOne)
			.translate(0f, 0f, -distance)
			.rotateX(PITCH_RADIANS)
			.rotateY(rotation);
	}
}
