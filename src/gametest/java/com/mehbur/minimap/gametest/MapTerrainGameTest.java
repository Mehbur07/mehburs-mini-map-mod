package com.mehbur.minimap.gametest;

import com.mehbur.minimap.MehbursMiniMapClient;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/** Captures the map in known situations. Screenshots land in build/run/clientGameTest/screenshots. */
public class MapTerrainGameTest implements FabricClientGameTest {
	private static final int RENDER_DISTANCE = 8;
	private static final int MESH_SETTLE_TICKS = 80;
	private static final int BIOME_SEARCH_RADIUS = 6400;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.renderDistance().set(RENDER_DISTANCE));
		testLandmarks(context);
		testRealTerrain(context);
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
