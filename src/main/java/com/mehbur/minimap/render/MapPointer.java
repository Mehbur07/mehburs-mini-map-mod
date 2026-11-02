package com.mehbur.minimap.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Finds where a block in the world falls on the map, and draws what points at it from the map's center. */
public final class MapPointer {
	/**
	 * In GUI coordinates. The edge point is the block itself when it lies inside the map, otherwise where the way to it leaves the map.
	 */
	public record Aim(float centerX, float centerY, float edgeX, float edgeY, float angle, boolean inside) {
	}

	private static final int OUTLINE_COLOR = 0xFF000000;
	private static final int LINE_OUTLINE_COLOR = 0x90000000;
	/** Keeps marks fully inside the frame. */
	private static final int EDGE_MARGIN = 4;
	/** Triangle sizes count in fractions of a GUI unit. */
	private static final int TRIANGLE_SUBDIVISION = 4;
	private static final int TRIANGLE_LENGTH = 14;
	private static final int TRIANGLE_HALF_BASE = 10;
	private static final int TRIANGLE_OUTLINE_TIP = 6;
	private static final int TRIANGLE_OUTLINE_LENGTH = 24;
	private static final int TRIANGLE_OUTLINE_HALF_BASE = 17;
	/** Far beyond the map's edge in every direction, yet close enough that the camera's perspective cannot flip the point. */
	private static final double DIRECTION_SAMPLE_RADII = 3.0;

	private final MapProjector projector = new MapProjector();

	/**
	 * @param viewProjection the camera the map image was last drawn with
	 * @param cameraPosition the player's position when the map image was drawn
	 * @param radiusBlocks   blocks from the map's center to its edge in that image
	 * @return null when the block cannot be placed on the map
	 */
	public Aim aim(int x, int y, int z, Matrix4f viewProjection, MapLayout.Rect rect, Vec3 cameraPosition, double radiusBlocks) {
		if (!project(x, y, z, viewProjection, cameraPosition, radiusBlocks)) {
			return null;
		}
		float targetX = projector.guiX(rect);
		float targetY = projector.guiY(rect);
		float centerX = rect.x() + rect.size() / 2f;
		float centerY = rect.y() + rect.size() / 2f;
		MapGeometry.Point edge = MapGeometry.clampAlongRay(centerX, centerY, targetX, targetY,
			rect.x() + EDGE_MARGIN, rect.y() + EDGE_MARGIN, rect.x() + rect.size() - EDGE_MARGIN, rect.bottom() - EDGE_MARGIN);
		boolean inside = edge.x() == targetX && edge.y() == targetY;
		return new Aim(centerX, centerY, edge.x(), edge.y(), (float) Math.atan2(targetY - centerY, targetX - centerX), inside);
	}

	/** Leaves the point, or a point in its direction when it is too far for the camera, in the projector. */
	private boolean project(int x, int y, int z, Matrix4f viewProjection, Vec3 cameraPosition, double radiusBlocks) {
		double dx = x + 0.5 - cameraPosition.x;
		double dy = y - cameraPosition.y;
		double dz = z + 0.5 - cameraPosition.z;
		double limit = radiusBlocks * DIRECTION_SAMPLE_RADII;
		double horizontal = Math.hypot(dx, dz);
		if (horizontal <= limit && projector.project(viewProjection, dx, dy, dz)) {
			return true;
		}
		double scale = limit / Math.max(horizontal, 1.0);
		return projector.project(viewProjection, dx * scale, 0.0, dz * scale);
	}

	public static void drawLine(GuiGraphicsExtractor graphics, Aim aim, int color) {
		int end = Math.round((float) Math.hypot(aim.edgeX() - aim.centerX(), aim.edgeY() - aim.centerY()));
		graphics.pose().pushMatrix();
		graphics.pose().translate(aim.centerX(), aim.centerY());
		graphics.pose().rotate(aim.angle());
		graphics.pose().translate(0f, -0.5f);
		graphics.fill(0, -1, end, 2, LINE_OUTLINE_COLOR);
		graphics.fill(0, 0, end, 1, color);
		graphics.pose().popMatrix();
	}

	/** The tip sits at the given position and points along the angle. */
	public static void drawTriangle(GuiGraphicsExtractor graphics, float x, float y, float angle, int color) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().rotate(angle);
		// Fill only takes whole numbers, and whole GUI units would turn the slopes into visible stairs.
		graphics.pose().scale(1f / TRIANGLE_SUBDIVISION);
		fillTriangle(graphics, TRIANGLE_OUTLINE_TIP, TRIANGLE_OUTLINE_LENGTH, TRIANGLE_OUTLINE_HALF_BASE, OUTLINE_COLOR);
		fillTriangle(graphics, 0, TRIANGLE_LENGTH, TRIANGLE_HALF_BASE, color);
		graphics.pose().popMatrix();
	}

	/** Nested rectangles, each reaching from the base toward the tip, because side-by-side ones would leave seams once rotated. */
	private static void fillTriangle(GuiGraphicsExtractor graphics, int tip, int length, int halfBase, int color) {
		for (int i = 0; i < length; i++) {
			int halfHeight = i * halfBase / length;
			graphics.fill(tip - length, -halfHeight, tip - i, halfHeight + 1, color);
		}
	}
}
