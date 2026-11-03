package com.mehbur.minimap.gametest;

import com.mehbur.minimap.MehbursMiniMapClient;
import com.mehbur.minimap.config.MinimapConfig;
import com.mehbur.minimap.input.BigMapScreen;
import com.mehbur.minimap.input.MinimapKeys;
import com.mehbur.minimap.render.MapLayout;
import com.mehbur.minimap.render.terrain.MapLayerTracker;
import com.mehbur.minimap.render.terrain.TerrainCache;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.datafixers.util.Pair;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.lwjgl.glfw.GLFW;

/** Captures the map in known situations. Screenshots land in build/run/clientGameTest/screenshots. */
public class MapTerrainGameTest implements FabricClientGameTest {
	private static final int RENDER_DISTANCE = 8;
	private static final int MESH_SETTLE_TICKS = 80;
	private static final int BIOME_SEARCH_RADIUS = 6400;
	private static final int DEEP_WATER_Y = 40;
	/** The clear color of the map's render target, where no terrain was drawn. */
	private static final int MAP_BACKGROUND_RGB = 0x05080D;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.renderDistance().set(RENDER_DISTANCE));
		testLandmarks(context);
		testBigMap(context);
		testBuiltCave(context);
		testOverhang(context);
		testRealTerrain(context);
	}

	/** The M key, the animation, wheel zoom, closing, and the text lines in every corner. */
	private static void testBigMap(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("fill 0 -60 -40 1 -55 -40 gold_block"); // north, outside the small map
			lookAt(context, server, "tp @a 0.5 -60 0.5 180 0");
			context.takeScreenshot("bigmap-closed");

			String category = context.computeOnClient(client -> MinimapKeys.TOGGLE_MAP.getCategory().label().getString());
			if (!"Mehbur's Mini Map".equals(category)) {
				throw new AssertionError("Key category shows as " + category);
			}

			float zoomBefore = context.computeOnClient(client -> MehbursMiniMapClient.config().bigMapZoom);
			context.getInput().pressKey(GLFW.GLFW_KEY_M);
			context.waitTicks(2);
			context.takeScreenshot("bigmap-opening");
			context.waitTicks(MESH_SETTLE_TICKS);
			assertBigMapOpen(context, true);
			boolean paused = context.computeOnClient(Minecraft::isPaused);
			if (paused) {
				throw new AssertionError("The big map paused the game");
			}
			context.takeScreenshot("bigmap-open");

			context.getInput().scroll(3.0);
			context.waitTicks(5);
			float zoomedIn = context.computeOnClient(client -> MehbursMiniMapClient.config().bigMapZoom);
			if (zoomedIn <= zoomBefore) {
				throw new AssertionError("Scrolling up did not zoom in");
			}
			context.takeScreenshot("bigmap-zoomed-in");
			context.getInput().scroll(-3.0);
			context.waitTicks(5);

			context.getInput().pressKey(GLFW.GLFW_KEY_M);
			context.waitTicks(2);
			assertBigMapOpen(context, false);
			context.takeScreenshot("bigmap-closing");
			context.waitTicks(20);
			context.takeScreenshot("bigmap-closed-again");

			context.getInput().pressKey(GLFW.GLFW_KEY_M);
			context.waitTicks(10);
			assertBigMapOpen(context, true);
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTicks(2);
			assertBigMapOpen(context, false);

			for (MinimapConfig.Corner corner : MinimapConfig.Corner.values()) {
				context.runOnClient(client -> MehbursMiniMapClient.config().corner = corner);
				context.waitTicks(5);
				context.takeScreenshot("corner-" + corner);
			}
			context.runOnClient(client -> MehbursMiniMapClient.config().corner = MinimapConfig.Corner.TOP_LEFT);
		}
	}

	private static void assertBigMapOpen(ClientGameTestContext context, boolean expected) {
		boolean actual = context.computeOnClient(client -> client.gui.screen() instanceof BigMapScreen);
		if (actual != expected) {
			throw new AssertionError("Big map open: expected " + expected + ", was " + actual);
		}
	}

	/** A cave dug at known positions, plus the places that must keep showing the surface. */
	private static void testBuiltCave(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamemode creative @a");
			lookAt(context, server, "tp @a 0.5 -40 0.5 180 0");
			assertLayer(context, MapLayerTracker.SURFACE, "open air above the flat world");

			server.runCommand("fill -20 -60 -20 20 -45 20 stone");
			server.runCommand("fill -2 -56 -2 2 -55 2 air"); // room around the player, floor at -57
			server.runCommand("fill 0 -57 -18 1 -57 -3 gold_block"); // north corridor
			server.runCommand("fill 0 -56 -18 1 -55 -3 air");
			server.runCommand("fill -18 -57 0 -3 -57 1 red_wool"); // west corridor
			server.runCommand("fill -18 -56 0 -3 -55 1 air");
			server.runCommand("fill 3 -56 0 12 -55 1 air"); // east corridor, flooded
			server.runCommand("fill 3 -56 0 12 -56 1 water");
			server.runCommand("fill 0 -60 3 1 -55 12 air"); // south pit, floor five blocks lower
			server.runCommand("fill 0 -61 3 1 -61 12 emerald_block");
			server.runCommand("fill -12 -50 -12 12 -48 12 air"); // chamber above the player, which the map must hide
			server.runCommand("fill -12 -51 -12 12 -51 12 diamond_block");

			lookAt(context, server, "tp @a 0.5 -56 0.5 180 0");
			assertLayer(context, -55, "built cave");
			context.takeScreenshot("cave-built-facing-north");

			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 90 0");
			context.waitTicks(10);
			context.takeScreenshot("cave-built-facing-west");

			server.runCommand("fill 0 -56 -8 1 -55 -8 sea_lantern");
			context.waitTicks(20);
			context.takeScreenshot("cave-built-corridor-walled");

			// One block of roof over a room is a building, not a cave.
			server.runCommand("fill 30 -60 30 36 -57 36 oak_planks hollow");
			lookAt(context, server, "tp @a 33.5 -60 33.5 180 0");
			assertLayer(context, MapLayerTracker.SURFACE, "closed house");

			lookAt(context, server, "tp @a 0.5 -40 0.5 180 0");
			assertLayer(context, MapLayerTracker.SURFACE, "back above the cave");
			context.takeScreenshot("cave-built-left");
		}
	}

	private static void testDimensions(ClientGameTestContext context, TestServerContext server) {
		lookAt(context, server, "execute in minecraft:the_nether run tp @a 0.5 70 0.5 180 0");
		int netherLayer = context.computeOnClient(client -> TerrainCache.currentLayerY());
		if (netherLayer == MapLayerTracker.SURFACE) {
			throw new AssertionError("Map shows the surface inside the Nether");
		}
		context.takeScreenshot("nether-inside");

		lookAt(context, server, "execute in minecraft:the_nether run tp @a 0.5 135 0.5 180 0");
		assertLayer(context, MapLayerTracker.SURFACE, "above the Nether roof");
		context.takeScreenshot("nether-roof");

		lookAt(context, server, "execute in minecraft:the_end run tp @a 0.5 75 0.5 180 0");
		assertLayer(context, MapLayerTracker.SURFACE, "the End");
		context.takeScreenshot("end-island");

		lookAt(context, server, "execute in minecraft:overworld run tp @a 0.5 200 0.5 180 0");
	}

	/** The real caves the game generates, found by searching for a dark pocket under the spawn area. */
	private static void visitRealCave(ClientGameTestContext context, TestServerContext server) {
		BlockPos found = server.computeOnServer(minecraft -> findCave(minecraft.overworld()));
		if (found == null) {
			throw new AssertionError("No cave found to test in");
		}
		lookAt(context, server, "tp @a " + (found.getX() + 0.5) + " " + found.getY() + " " + (found.getZ() + 0.5) + " 180 0");
		assertLayer(context, found.getY() + 1, "real cave");
		context.takeScreenshot("cave-real");
	}

	private static BlockPos findCave(ServerLevel level) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = -128; x <= 128; x += 16) {
			for (int z = -128; z <= 128; z += 16) {
				for (int y = 20; y >= -40; y--) {
					boolean standable = !level.getBlockState(pos.set(x, y - 1, z)).isAir()
						&& level.getBlockState(pos.set(x, y, z)).isAir()
						&& level.getBlockState(pos.set(x, y + 1, z)).isAir();
					if (standable && level.getBrightness(LightLayer.SKY, pos) == 0) {
						return pos.set(x, y, z).immutable();
					}
				}
			}
		}
		return null;
	}

	/** Swimming down in an ocean has nothing overhead but water, so the map keeps showing the ocean. */
	private static void visitDeepWater(ClientGameTestContext context, TestServerContext server) {
		BlockPos found = server.computeOnServer(minecraft -> {
			ServerLevel level = minecraft.overworld();
			Pair<BlockPos, Holder<Biome>> result = level.findClosestBiome3d(
				holder -> holder.is(Biomes.DEEP_OCEAN), BlockPos.ZERO, BIOME_SEARCH_RADIUS, 32, 64);
			return result == null ? null : result.getFirst();
		});
		if (found == null) {
			MehbursMiniMapClient.LOGGER.warn("[minimap-test] deep ocean not found");
			return;
		}
		lookAt(context, server, "tp @a " + found.getX() + " " + DEEP_WATER_Y + " " + found.getZ() + " 180 0");
		assertLayer(context, MapLayerTracker.SURFACE, "under deep water");
		context.takeScreenshot("ocean-under-water");
	}

	private static void assertLayer(ClientGameTestContext context, int expected, String where) {
		int actual = context.computeOnClient(client -> TerrainCache.currentLayerY());
		if (actual != expected) {
			throw new AssertionError("Map layer in " + where + ": expected " + expected + ", was " + actual);
		}
	}

	/**
	 * A hollow under a cliff, open toward the player. The map used to read only the cliff's top, so its
	 * inside showed the empty background in black.
	 */
	private static void testOverhang(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamemode creative @a");
			server.runCommand("fill -12 -60 -20 12 -45 -6 stone");
			server.runCommand("fill -8 -60 -14 8 -54 -6 air"); // nine blocks of rock over the hollow
			server.runCommand("fill -8 -61 -14 8 -61 -6 gold_block");
			lookAt(context, server, "tp @a 0.5 -60 0.5 180 0");
			Path screenshot = context.takeScreenshot("overhang-hollow");

			MapLayout.Rect rect = context.computeOnClient(client -> MapLayout.mini(MinimapConfig.Corner.TOP_LEFT,
				MehbursMiniMapClient.config().mapSize, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), 0));
			int scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
			int background = countPixels(screenshot, rect, scale, MAP_BACKGROUND_RGB);
			if (background > 0) {
				throw new AssertionError(background + " map pixels show the empty background under the overhang");
			}
		}
	}

	private static int countPixels(Path screenshot, MapLayout.Rect rect, int scale, int rgb) {
		try (InputStream stream = Files.newInputStream(screenshot); NativeImage image = NativeImage.read(stream)) {
			int count = 0;
			// One pixel in from the frame on every side.
			for (int y = (rect.y() + 1) * scale; y < (rect.y() + rect.size() - 1) * scale; y++) {
				for (int x = (rect.x() + 1) * scale; x < (rect.x() + rect.size() - 1) * scale; x++) {
					if (closeTo(image.getPixel(x, y), rgb)) {
						count++;
					}
				}
			}
			return count;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static boolean closeTo(int argb, int rgb) {
		for (int shift = 0; shift < 24; shift += Byte.SIZE) {
			if (Math.abs((argb >> shift & 0xFF) - (rgb >> shift & 0xFF)) > 2) {
				return false;
			}
		}
		return true;
	}

	/** Flat world with a block pattern at known compass positions, to check rotation and live updates. */
	private static void testLandmarks(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("fill 0 -60 -10 0 -54 -10 gold_block"); // north
			server.runCommand("fill -12 -60 -3 -12 -58 3 red_wool"); // west
			server.runCommand("fill 8 -62 -3 14 -61 3 water"); // east
			server.runCommand("fill -6 -60 8 6 -58 14 stone"); // south
			server.runCommand("fill -3 -57 10 3 -55 12 oak_planks");
			server.runCommand("place feature minecraft:oak -8 -60 -8");

			lookAt(context, server, "tp @a 0.5 -60 0.5 180 0");
			context.takeScreenshot("flat-facing-north");

			lookAt(context, server, "tp @a 0.5 -60 0.5 90 0");
			context.takeScreenshot("flat-facing-west");

			setNorthLocked(context, true);
			context.waitTicks(5);
			context.takeScreenshot("flat-facing-west-north-locked");

			server.runCommand("fill 0 -60 -10 0 -54 -10 air");
			context.waitTicks(20);
			context.takeScreenshot("flat-pillar-broken");
			setNorthLocked(context, false);
		}
	}

	private static void testRealTerrain(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(settings -> {
				settings.setSeed("mehbur");
				settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
			})
			.create()) {
			TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamerule spawn_mobs false");

			testDimensions(context, server);
			visitRealCave(context, server);
			visitDeepWater(context, server);

			visitBiome(context, server, Biomes.JAGGED_PEAKS, "peaks");
			visitBiome(context, server, Biomes.SNOWY_PLAINS, "snow");
			visitBiome(context, server, Biomes.FOREST, "forest");
			visitBiome(context, server, Biomes.BEACH, "beach");

			setZoom(context, 0.25f);
			context.waitTicks(MESH_SETTLE_TICKS);
			context.takeScreenshot("beach-zoom-far");
			setZoom(context, 4f);
			context.waitTicks(10);
			context.takeScreenshot("beach-zoom-near");
			setZoom(context, 1f);
			context.waitTicks(10);

			// Whatever is behind the player on the map must match the real view.
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ ~180 25");
			context.waitTicks(MESH_SETTLE_TICKS);
			context.takeScreenshot("beach-turned-around");
		}
	}

	private static void visitBiome(ClientGameTestContext context, TestServerContext server, ResourceKey<Biome> biome, String name) {
		BlockPos found = server.computeOnServer(minecraft -> {
			ServerLevel level = minecraft.overworld();
			Pair<BlockPos, Holder<Biome>> result = level.findClosestBiome3d(
				holder -> holder.is(biome), BlockPos.ZERO, BIOME_SEARCH_RADIUS, 32, 64);
			return result == null ? null : result.getFirst();
		});
		if (found == null) {
			MehbursMiniMapClient.LOGGER.warn("[minimap-test] biome {} not found", name);
			return;
		}
		lookAt(context, server, "spreadplayers " + found.getX() + " " + found.getZ() + " 0 1 false @a");
		context.takeScreenshot("biome-" + name);
	}

	private static void lookAt(ClientGameTestContext context, TestServerContext server, String command) {
		server.runCommand(command);
		context.waitTicks(MESH_SETTLE_TICKS);
	}

	private static void setNorthLocked(ClientGameTestContext context, boolean locked) {
		context.runOnClient(client -> MehbursMiniMapClient.config().northLocked = locked);
	}

	private static void setZoom(ClientGameTestContext context, float zoom) {
		context.runOnClient(client -> MehbursMiniMapClient.config().zoom = zoom);
	}
}
