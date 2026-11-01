package com.mehbur.minimap.render.terrain;

import com.mehbur.minimap.MehbursMiniMapClient;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Keeps chunk meshes on the GPU and refreshes only the ones that changed. Snapshots are budgeted on
 * the client thread, meshing runs on one worker thread, and uploads are budgeted on the render thread.
 */
public final class TerrainCache implements AutoCloseable {
	private static final int SNAPSHOTS_PER_TICK = 8;
	private static final int UPLOADS_PER_FRAME = 16;
	private static final long DIRTY_DELAY_MILLIS = 100;
	/** Meshes outside the view are dropped above this size. Visible meshes always stay. */
	private static final long MAX_BYTES = 64L * 1024 * 1024;

	private static TerrainCache instance;

	private static final class Entry {
		ChunkMesh mesh;
		boolean inFlight;
		boolean dirty = true;
		long dirtySinceMillis;
	}

	private record FinishedMesh(int generation, int chunkX, int chunkZ, ChunkMesher.MeshData data) {
	}

	private final Long2ObjectMap<Entry> entries = new Long2ObjectOpenHashMap<>();
	private final Queue<FinishedMesh> finished = new ConcurrentLinkedQueue<>();
	private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Mehbur's Mini Map mesher");
		thread.setDaemon(true);
		return thread;
	});
	private final ChunkMesher mesher = new ChunkMesher();
	private final ChunkSnapshotter snapshotter = new ChunkSnapshotter();

	private ClientLevel level;
	private TextureAtlas atlas;
	private Object atlasStamp;
	private int generation;
	private long totalBytes;
	private int centerChunkX;
	private int centerChunkZ;
	private int viewRadiusChunks;
	private int layerY = MapLayerTracker.SURFACE;

	public TerrainCache() {
		instance = this;
	}

	/** Called by the level mixin whenever a block changes on the client. */
	public static void onBlockChanged(ClientLevel level, BlockPos pos) {
		if (instance != null && instance.level == level) {
			instance.markColumnDirty(pos.getX(), pos.getZ());
		}
	}

	public void onChunkLoaded(ClientLevel level, LevelChunk chunk) {
		if (this.level != level) {
			return;
		}
		int chunkX = chunk.getPos().x();
		int chunkZ = chunk.getPos().z();
		markChunkDirty(chunkX, chunkZ);
		// Neighbors built without this chunk are missing their side faces along the shared edge.
		markChunkDirty(chunkX - 1, chunkZ);
		markChunkDirty(chunkX + 1, chunkZ);
		markChunkDirty(chunkX, chunkZ - 1);
		markChunkDirty(chunkX, chunkZ + 1);
	}

	/** Client thread: starts work on the chunks nearest the player that need it. */
	public void tick(ClientLevel level, TextureAtlas atlas, double playerX, double playerZ, int viewRadiusChunks, int layerY) {
		if (spriteStamp(atlas) != atlasStamp) {
			clear();
			snapshotter.clearSpriteCache();
			atlasStamp = spriteStamp(atlas);
		}
		if (this.level != level) {
			clear();
			this.level = level;
		}
		if (this.layerY != layerY) {
			this.layerY = layerY;
			markAllDirty();
		}
		this.atlas = atlas;
		this.viewRadiusChunks = viewRadiusChunks;
		centerChunkX = (int) Math.floor(playerX) >> 4;
		centerChunkZ = (int) Math.floor(playerZ) >> 4;

		int budget = SNAPSHOTS_PER_TICK;
		long now = Util.getMillis();
		for (int ring = 0; ring <= viewRadiusChunks && budget > 0; ring++) {
			for (int dz = -ring; dz <= ring && budget > 0; dz++) {
				for (int dx = -ring; dx <= ring && budget > 0; dx++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) == ring && schedule(centerChunkX + dx, centerChunkZ + dz, now)) {
						budget--;
					}
				}
			}
		}
	}

	/** Atlas coordinates change when resources reload, which makes every mesh stale. */
	private static Object spriteStamp(TextureAtlas atlas) {
		return atlas.missingSprite();
	}

	private boolean schedule(int chunkX, int chunkZ, long now) {
		long key = ChunkPos.pack(chunkX, chunkZ);
		Entry entry = entries.get(key);
		if (entry != null && (entry.inFlight || !entry.dirty || now - entry.dirtySinceMillis < DIRTY_DELAY_MILLIS)) {
			return false;
		}
		LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
		if (chunk == null) {
			return false;
		}
		if (entry == null) {
			entry = new Entry();
			entries.put(key, entry);
		}
		entry.inFlight = true;
		entry.dirty = false;
		ChunkSnapshot snapshot = snapshotter.take(level, chunk, atlas, layerY);
		int submittedGeneration = generation;
		worker.execute(() -> mesh(snapshot, submittedGeneration));
		return true;
	}

	private void mesh(ChunkSnapshot snapshot, int submittedGeneration) {
		ChunkMesher.MeshData data = null;
		try {
			data = mesher.build(snapshot);
		} catch (RuntimeException e) {
			MehbursMiniMapClient.LOGGER.error("Could not mesh chunk {}, {}", snapshot.chunkX, snapshot.chunkZ, e);
		}
		finished.add(new FinishedMesh(submittedGeneration, snapshot.chunkX, snapshot.chunkZ, data));
	}

	/** Render thread: moves finished meshes to the GPU. Returns whether anything visible changed. */
	public boolean uploadFinished() {
		boolean changed = false;
		for (int i = 0; i < UPLOADS_PER_FRAME; i++) {
			FinishedMesh result = finished.poll();
			if (result == null) {
				break;
			}
			if (result.generation == generation) {
				changed |= install(result);
			}
		}
		if (changed) {
			evictOutsideView();
		}
		return changed;
	}

	private boolean install(FinishedMesh result) {
		Entry entry = entries.get(ChunkPos.pack(result.chunkX, result.chunkZ));
		if (entry == null) {
			return false;
		}
		entry.inFlight = false;
		if (result.data == null) {
			return false;
		}
		releaseMesh(entry);
		entry.mesh = new ChunkMesh(result.chunkX, result.chunkZ, result.data);
		totalBytes += entry.mesh.bytes;
		return true;
	}

	private void evictOutsideView() {
		if (totalBytes <= MAX_BYTES) {
			return;
		}
		List<Long> outside = new ArrayList<>();
		for (Long2ObjectMap.Entry<Entry> item : entries.long2ObjectEntrySet()) {
			if (distanceFromCenter(item.getLongKey()) > viewRadiusChunks && !item.getValue().inFlight) {
				outside.add(item.getLongKey());
			}
		}
		outside.sort((a, b) -> Integer.compare(distanceFromCenter(b), distanceFromCenter(a)));
		for (long key : outside) {
			if (totalBytes <= MAX_BYTES) {
				return;
			}
			releaseMesh(entries.remove(key));
		}
	}

	private int distanceFromCenter(long key) {
		return Math.max(Math.abs(ChunkPos.getX(key) - centerChunkX), Math.abs(ChunkPos.getZ(key) - centerChunkZ));
	}

	/** Adds meshes within {@code radiusBlocks} of the given position to {@code out}. */
	public void collectVisible(double x, double z, double radiusBlocks, List<ChunkMesh> out) {
		double reach = radiusBlocks + ChunkSnapshot.SIZE;
		for (Entry entry : entries.values()) {
			ChunkMesh mesh = entry.mesh;
			if (mesh != null) {
				double centerX = (mesh.chunkX << 4) + ChunkSnapshot.SIZE / 2.0;
				double centerZ = (mesh.chunkZ << 4) + ChunkSnapshot.SIZE / 2.0;
				double dx = centerX - x;
				double dz = centerZ - z;
				if (dx * dx + dz * dz <= reach * reach) {
					out.add(mesh);
				}
			}
		}
	}

	public static long loadedBytes() {
		return instance == null ? 0L : instance.totalBytes;
	}

	/** The height the map currently cuts at, or {@link MapLayerTracker#SURFACE}. */
	public static int currentLayerY() {
		return instance == null ? MapLayerTracker.SURFACE : instance.layerY;
	}

	/** Old meshes stay on screen until their replacements arrive, so the map never goes blank during a switch. */
	private void markAllDirty() {
		for (Entry entry : entries.values()) {
			entry.dirty = true;
			entry.dirtySinceMillis = 0L;
		}
	}

	private void markColumnDirty(int worldX, int worldZ) {
		int chunkX = worldX >> 4;
		int chunkZ = worldZ >> 4;
		markChunkDirty(chunkX, chunkZ);
		// A change on a chunk edge also alters the side faces of the neighboring chunk.
		int localX = worldX & 15;
		int localZ = worldZ & 15;
		if (localX == 0) {
			markChunkDirty(chunkX - 1, chunkZ);
		} else if (localX == 15) {
			markChunkDirty(chunkX + 1, chunkZ);
		}
		if (localZ == 0) {
			markChunkDirty(chunkX, chunkZ - 1);
		} else if (localZ == 15) {
			markChunkDirty(chunkX, chunkZ + 1);
		}
	}

	private void markChunkDirty(int chunkX, int chunkZ) {
		Entry entry = entries.get(ChunkPos.pack(chunkX, chunkZ));
		if (entry != null && !entry.dirty) {
			entry.dirty = true;
			entry.dirtySinceMillis = Util.getMillis();
		}
	}

	private void releaseMesh(Entry entry) {
		if (entry.mesh != null) {
			totalBytes -= entry.mesh.bytes;
			entry.mesh.close();
			entry.mesh = null;
		}
	}

	/** Drops all meshes. Render or client thread, which are the same thread in this game version. */
	public void clear() {
		generation++;
		for (Entry entry : entries.values()) {
			releaseMesh(entry);
		}
		entries.clear();
		finished.clear();
		level = null;
	}

	@Override
	public void close() {
		clear();
		worker.shutdownNow();
		instance = null;
	}
}
