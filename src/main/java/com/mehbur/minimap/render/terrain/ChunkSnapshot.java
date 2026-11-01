package com.mehbur.minimap.render.terrain;

/**
 * Everything the mesher needs from one chunk, copied on the client thread so that
 * meshing can run on a worker thread without touching the level.
 */
public final class ChunkSnapshot {
	public static final int SIZE = 16;
	/** Heights cover the chunk plus a one-block border, so edge columns can see their neighbors. */
	public static final int GRID = SIZE + 2;
	/** The column has nothing to draw at this level, for example open void or water too deep to show a floor. */
	public static final int NO_BLOCK = Integer.MIN_VALUE;
	/** The neighboring chunk is not loaded yet, so no side faces should be built against it. */
	public static final int UNKNOWN = Integer.MAX_VALUE;

	public final int chunkX;
	public final int chunkZ;
	public final int[] floorHeights = new int[GRID * GRID];
	public final int[] surfaceHeights = new int[GRID * GRID];
	/** The lowest level visible from above in each column: the ground under leaves or overhangs, otherwise the floor. */
	public final int[] groundHeights = new int[GRID * GRID];
	public final Column[] columns = new Column[SIZE * SIZE];

	/**
	 * @param top       sprite of the floor's top face, or null when there is no floor or the map's layer cuts through it
	 * @param topRgb    tint of the top face
	 * @param sides     side sprite per block going down from the floor; entries are null where nothing solid is exposed
	 * @param sideRgbs  tint per entry of {@code sides}
	 * @param waterTop  Y of the highest water block, or {@link #NO_BLOCK}
	 * @param water     water sprite, or null without water
	 * @param waterRgb  water tint
	 * @param under     top sprite of the ground below a gap under the floor, such as under tree leaves; may be null
	 * @param underRgb  tint of {@code under}
	 * @param underY    Y of the block that {@code under} belongs to
	 */
	public record Column(FaceSprite top, int topRgb, FaceSprite[] sides, int[] sideRgbs, int waterTop, FaceSprite water, int waterRgb,
		FaceSprite under, int underRgb, int underY) {
	}

	public ChunkSnapshot(int chunkX, int chunkZ) {
		this.chunkX = chunkX;
		this.chunkZ = chunkZ;
	}

	/** Grid index of local coordinates, where -1 and {@link #SIZE} address the border. */
	public static int gridIndex(int x, int z) {
		return (x + 1) + (z + 1) * GRID;
	}

	public static int columnIndex(int x, int z) {
		return x + z * SIZE;
	}
}
