package com.mehbur.minimap.render.terrain;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/** Reads the visible surface of a chunk from the level. Client thread only. */
final class ChunkSnapshotter {
	private static final int MAX_WATER_DEPTH = 24;
	private static final int MAX_SIDE_DEPTH = 32;
	/** Below the cut, floors deeper than this stay dark. Reaches the Nether's lava sea from most of its caves. */
	private static final int MAX_LAYER_DEPTH = 64;
	private static final int WHITE = 0xFFFFFF;

	private record BlockFaces(FaceSprite top, FaceSprite side) {
	}

	private final Map<BlockState, BlockFaces> facesByState = new IdentityHashMap<>();
	private final RandomSource random = RandomSource.create(42L);
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

	void clearSpriteCache() {
		facesByState.clear();
	}

	/** @param layerY the height to cut the world at, or {@link MapLayerTracker#SURFACE} to look from the sky */
	ChunkSnapshot take(ClientLevel level, LevelChunk chunk, TextureAtlas atlas, int layerY) {
		int chunkX = chunk.getPos().x();
		int chunkZ = chunk.getPos().z();
		ChunkSnapshot snapshot = new ChunkSnapshot(chunkX, chunkZ);
		LevelChunk[] nearby = nearbyChunks(level, chunkX, chunkZ, chunk);

		for (int z = -1; z <= ChunkSnapshot.SIZE; z++) {
			for (int x = -1; x <= ChunkSnapshot.SIZE; x++) {
				LevelChunk source = nearby[nearbyIndex(x, z)];
				int index = ChunkSnapshot.gridIndex(x, z);
				if (source == null) {
					snapshot.floorHeights[index] = ChunkSnapshot.UNKNOWN;
					snapshot.surfaceHeights[index] = ChunkSnapshot.UNKNOWN;
					snapshot.groundHeights[index] = ChunkSnapshot.UNKNOWN;
				} else {
					readHeights(level, source, (chunkX << 4) + x, (chunkZ << 4) + z, layerY, snapshot, index);
				}
			}
		}
		for (int z = 0; z < ChunkSnapshot.SIZE; z++) {
			for (int x = 0; x < ChunkSnapshot.SIZE; x++) {
				snapshot.columns[ChunkSnapshot.columnIndex(x, z)] = readColumn(level, chunk, atlas, layerY, snapshot, x, z);
			}
		}
		return snapshot;
	}

	private static LevelChunk[] nearbyChunks(ClientLevel level, int chunkX, int chunkZ, LevelChunk center) {
		LevelChunk[] chunks = new LevelChunk[9];
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				chunks[(dx + 1) + (dz + 1) * 3] = dx == 0 && dz == 0
					? center
					: level.getChunkSource().getChunk(chunkX + dx, chunkZ + dz, ChunkStatus.FULL, false);
			}
		}
		return chunks;
	}

	private static int nearbyIndex(int localX, int localZ) {
		int cellX = localX < 0 ? 0 : localX >= ChunkSnapshot.SIZE ? 2 : 1;
		int cellZ = localZ < 0 ? 0 : localZ >= ChunkSnapshot.SIZE ? 2 : 1;
		return cellX + cellZ * 3;
	}

	private void readHeights(ClientLevel level, LevelChunk chunk, int worldX, int worldZ, int layerY, ChunkSnapshot snapshot, int index) {
		int surface = layerY == MapLayerTracker.SURFACE
			? chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX & 15, worldZ & 15)
			: findHighestBelowCut(level, chunk, worldX, worldZ, layerY);
		if (surface < level.getMinY()) {
			snapshot.floorHeights[index] = ChunkSnapshot.NO_BLOCK;
			snapshot.surfaceHeights[index] = ChunkSnapshot.NO_BLOCK;
			snapshot.groundHeights[index] = ChunkSnapshot.NO_BLOCK;
			return;
		}
		int floor = findFloor(level, chunk, worldX, worldZ, surface);
		snapshot.surfaceHeights[index] = surface;
		snapshot.floorHeights[index] = floor;
		int ground = floor == ChunkSnapshot.NO_BLOCK ? ChunkSnapshot.NO_BLOCK : findGroundBelowGap(level, chunk, worldX, worldZ, floor);
		snapshot.groundHeights[index] = ground == ChunkSnapshot.NO_BLOCK ? floor : ground;
	}

	/** What the heightmap gives on the surface, for a world that ends at the cut. Below the world's bottom when nothing is in reach. */
	private int findHighestBelowCut(ClientLevel level, LevelChunk chunk, int worldX, int worldZ, int layerY) {
		int deepest = Math.max(level.getMinY(), layerY - MAX_LAYER_DEPTH);
		for (int y = layerY; y >= deepest; y--) {
			if (Heightmap.Types.MOTION_BLOCKING.isOpaque().test(chunk.getBlockState(pos.set(worldX, y, worldZ)))) {
				return y;
			}
		}
		return level.getMinY() - 1;
	}

	/** Walks down through water. Returns {@link ChunkSnapshot#NO_BLOCK} when the bottom is too deep to show. */
	private int findFloor(ClientLevel level, LevelChunk chunk, int worldX, int worldZ, int surface) {
		int deepest = Math.max(level.getMinY(), surface - MAX_WATER_DEPTH);
		for (int y = surface; y >= deepest; y--) {
			if (!chunk.getBlockState(pos.set(worldX, y, worldZ)).getFluidState().is(FluidTags.WATER)) {
				return y;
			}
		}
		return ChunkSnapshot.NO_BLOCK;
	}

	private ChunkSnapshot.Column readColumn(ClientLevel level, LevelChunk chunk, TextureAtlas atlas, int layerY, ChunkSnapshot snapshot, int x, int z) {
		int index = ChunkSnapshot.gridIndex(x, z);
		int surface = snapshot.surfaceHeights[index];
		if (surface == ChunkSnapshot.NO_BLOCK) {
			return null;
		}
		int worldX = (chunk.getPos().x() << 4) + x;
		int worldZ = (chunk.getPos().z() << 4) + z;
		int floor = snapshot.floorHeights[index];

		FaceSprite top = null;
		int topRgb = WHITE;
		FaceSprite[] sides = new FaceSprite[0];
		int[] sideRgbs = new int[0];
		FaceSprite under = null;
		int underRgb = WHITE;
		int underY = 0;
		if (floor != ChunkSnapshot.NO_BLOCK) {
			BlockState floorState = chunk.getBlockState(pos.set(worldX, floor, worldZ));
			BlockState above = chunk.getBlockState(pos.set(worldX, floor + 1, worldZ));
			// A single snow layer does not count for the heightmap, but it is what you see from above.
			BlockState topState = above.is(Blocks.SNOW) ? above : floorState;
			// Rock cut by the layer stays open: a cap would hide the tunnel behind it from the tilted camera.
			if (floor != layerY || !CaveDetector.isRock(floorState)) {
				top = faces(topState, atlas).top();
				topRgb = tint(level, topState, top, pos.set(worldX, floor, worldZ));
			}

			int depth = sideDepth(snapshot, x, z, floor);
			sides = new FaceSprite[depth];
			sideRgbs = new int[depth];
			for (int i = 0; i < depth; i++) {
				BlockState state = chunk.getBlockState(pos.set(worldX, floor - i, worldZ));
				if (!state.isAir() && state.getFluidState().isEmpty()) {
					sides[i] = faces(state, atlas).side();
					sideRgbs[i] = tint(level, state, sides[i], pos);
				}
			}

			underY = snapshot.groundHeights[index];
			if (underY != floor) {
				BlockState ground = chunk.getBlockState(pos.set(worldX, underY, worldZ));
				if (ground.getFluidState().is(FluidTags.WATER)) {
					under = faces(Blocks.WATER.defaultBlockState(), atlas).top();
					underRgb = BiomeColors.getAverageWaterColor(level, pos) & WHITE;
				} else {
					under = faces(ground, atlas).top();
					underRgb = tint(level, ground, under, pos);
				}
			}
		}

		if (surface == floor) {
			return new ChunkSnapshot.Column(top, topRgb, sides, sideRgbs, ChunkSnapshot.NO_BLOCK, null, WHITE, under, underRgb, underY);
		}
		FaceSprite water = faces(Blocks.WATER.defaultBlockState(), atlas).top();
		int waterRgb = BiomeColors.getAverageWaterColor(level, pos.set(worldX, surface, worldZ)) & WHITE;
		return new ChunkSnapshot.Column(top, topRgb, sides, sideRgbs, surface, water, waterRgb, under, underRgb, underY);
	}

	/**
	 * The tilted camera sees under tree tops and overhangs, where only the top layer would leave a hole.
	 * Returns the Y of the first ground block below such a gap, or {@link ChunkSnapshot#NO_BLOCK} without a gap.
	 */
	private int findGroundBelowGap(ClientLevel level, LevelChunk chunk, int worldX, int worldZ, int floor) {
		int deepest = Math.max(level.getMinY(), floor - MAX_SIDE_DEPTH);
		boolean gap = chunk.getBlockState(pos.set(worldX, floor, worldZ)).is(BlockTags.LEAVES);
		for (int y = floor - 1; y >= deepest; y--) {
			BlockState state = chunk.getBlockState(pos.set(worldX, y, worldZ));
			if (Heightmap.Types.MOTION_BLOCKING_NO_LEAVES.isOpaque().test(state)) {
				return gap ? y : ChunkSnapshot.NO_BLOCK;
			}
			gap = true;
		}
		return ChunkSnapshot.NO_BLOCK;
	}

	/** How many blocks below the floor are exposed by the lowest neighboring ground. */
	private static int sideDepth(ChunkSnapshot snapshot, int x, int z, int floor) {
		int lowest = floor;
		lowest = lowerOf(lowest, snapshot.groundHeights[ChunkSnapshot.gridIndex(x, z - 1)], floor);
		lowest = lowerOf(lowest, snapshot.groundHeights[ChunkSnapshot.gridIndex(x, z + 1)], floor);
		lowest = lowerOf(lowest, snapshot.groundHeights[ChunkSnapshot.gridIndex(x - 1, z)], floor);
		lowest = lowerOf(lowest, snapshot.groundHeights[ChunkSnapshot.gridIndex(x + 1, z)], floor);
		return Math.min(floor - lowest, MAX_SIDE_DEPTH);
	}

	private static int lowerOf(int lowest, int neighbor, int floor) {
		if (neighbor == ChunkSnapshot.UNKNOWN) {
			return lowest;
		}
		return neighbor == ChunkSnapshot.NO_BLOCK ? floor - MAX_SIDE_DEPTH : Math.min(lowest, neighbor);
	}

	private static int tint(ClientLevel level, BlockState state, FaceSprite sprite, BlockPos pos) {
		if (sprite.tintIndex() < 0) {
			return WHITE;
		}
		BlockColors colors = Minecraft.getInstance().getBlockColors();
		BlockTintSource source = colors.getTintSource(state, sprite.tintIndex());
		return source == null ? WHITE : source.colorInWorld(state, level, pos) & WHITE;
	}

	private BlockFaces faces(BlockState state, TextureAtlas atlas) {
		return facesByState.computeIfAbsent(state, key -> resolveFaces(key, atlas));
	}

	private BlockFaces resolveFaces(BlockState state, TextureAtlas atlas) {
		BlockStateModelSet models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
		BlockStateModel model = models.get(state);
		List<BlockStateModelPart> parts = new ArrayList<>();
		random.setSeed(42L);
		model.collectParts(random, parts);

		FaceSprite particle = toFace(model.particleMaterial().sprite(), -1, atlas);
		FaceSprite top = findFace(parts, Direction.UP, atlas);
		FaceSprite side = findFace(parts, Direction.NORTH, atlas);
		return new BlockFaces(top != null ? top : particle, side != null ? side : particle);
	}

	private static FaceSprite findFace(List<BlockStateModelPart> parts, Direction direction, TextureAtlas atlas) {
		for (BlockStateModelPart part : parts) {
			List<BakedQuad> culled = part.getQuads(direction);
			if (!culled.isEmpty()) {
				return toFace(culled.getFirst(), atlas);
			}
		}
		// Partial blocks such as slabs keep faces that never get culled in the null list.
		for (BlockStateModelPart part : parts) {
			for (BakedQuad quad : part.getQuads(null)) {
				if (quad.direction() == direction) {
					return toFace(quad, atlas);
				}
			}
		}
		return null;
	}

	private static FaceSprite toFace(BakedQuad quad, TextureAtlas atlas) {
		return toFace(quad.materialInfo().sprite(), quad.materialInfo().tintIndex(), atlas);
	}

	private static FaceSprite toFace(TextureAtlasSprite sprite, int tintIndex, TextureAtlas atlas) {
		// Some special blocks use sprites from other atlases, whose coordinates mean nothing here.
		TextureAtlasSprite usable = sprite.atlasLocation().equals(atlas.location()) ? sprite : atlas.missingSprite();
		return new FaceSprite(usable.getU0(), usable.getV0(), usable.getU1(), usable.getV1(), tintIndex);
	}
}
