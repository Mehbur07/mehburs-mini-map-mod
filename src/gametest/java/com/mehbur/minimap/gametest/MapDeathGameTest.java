package com.mehbur.minimap.gametest;

import com.mehbur.minimap.MehbursMiniMapClient;
import com.mehbur.minimap.death.DeathMarkers;
import com.mehbur.minimap.death.DeathPoint;
import com.mehbur.minimap.entity.EntityDotType;
import com.mehbur.minimap.entity.EntityRadar;
import com.mehbur.minimap.input.MinimapKeys;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import org.lwjgl.glfw.GLFW;

/** Dies at known positions and checks the marks. Screenshots land in build/run/clientGameTest/screenshots. */
public class MapDeathGameTest implements FabricClientGameTest {
	private static final int SETTLE_TICKS = 30;
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";
	private static final int GROUND = -60;
	/** Rough GUI distance of the triangle from the map's frame, which depends on its margin. */
	private static final int EDGE_TOLERANCE = 8;

	@Override
	public void runTest(ClientGameTestContext context) {
		TestWorldSave save;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			save = world.getWorldSave();
			TestServerContext server = world.getServer();
			setUp(context, server);
			testDeathAndMarks(context, server);
			testNewDeathsAndDimensions(context, server);
		}
		try (TestSingleplayerContext reopened = save.open()) {
			context.waitTicks(10);
			DeathPoint point = context.computeOnClient(client -> DeathMarkers.INSTANCE.point());
			check(point != null && NETHER.equals(point.dimension()), "the world forgot its death: " + point);
		}
		try (TestSingleplayerContext other = context.worldBuilder().create()) {
			context.waitTicks(10);
			DeathPoint point = context.computeOnClient(client -> DeathMarkers.INSTANCE.point());
			check(point == null, "another world's death showed up: " + point);
		}
		// A real server needs the EULA accepted, which only the development run may do on its own.
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			testDedicatedServer(context);
		}
	}

	private static void setUp(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("time set noon");
		server.runCommand("gamerule spawn_mobs false");
		context.runOnClient(client -> {
			MehbursMiniMapClient.config().deathLineEnabled = true;
			MehbursMiniMapClient.config().deathTriangleEnabled = true;
		});
	}

	private static void testDeathAndMarks(ClientGameTestContext context, TestServerContext server) {
		check(context.computeOnClient(client -> DeathMarkers.INSTANCE.point()) == null, "a new world must have no death");
		teleport(context, server, 20, 180);
		die(context, server);
		assertPoint(context, OVERWORLD, 20, 0);

		// The line is on by default, and a death inside the map gets the skull.
		teleport(context, server, 0, 180);
		context.takeScreenshot("death-inside-map-line");
		DeathMarkers.Drawn drawn = drawn(context);
		check(drawn.line(), "the line must be on by default");
		check(drawn.mark() == DeathMarkers.Mark.SKULL, "inside the map the skull must show, not " + drawn.mark());

		pressK(context);
		boolean line = context.computeOnClient(client -> MehbursMiniMapClient.config().deathLineEnabled);
		check(!line, "K must turn the line off");
		drawn = drawn(context);
		check(!drawn.line() && drawn.mark() == DeathMarkers.Mark.SKULL, "the skull stays without the line, was " + drawn);
		context.takeScreenshot("death-inside-map-no-line");

		// 200 blocks east of the death, so the mark has to point west.
		teleport(context, server, 230, 180);
		context.takeScreenshot("death-triangle-facing-north");
		assertTriangleAt(context, Edge.LEFT, Edge.MIDDLE);
		teleport(context, server, 230, 90);
		context.takeScreenshot("death-triangle-facing-west");
		assertTriangleAt(context, Edge.MIDDLE, Edge.TOP);
		teleport(context, server, 230, 0);
		assertTriangleAt(context, Edge.RIGHT, Edge.MIDDLE);
		teleport(context, server, 230, -90);
		context.takeScreenshot("death-triangle-facing-east");
		assertTriangleAt(context, Edge.MIDDLE, Edge.BOTTOM);

		// Far enough that a plain projection would land behind the camera.
		teleport(context, server, 4030, -90);
		assertTriangleAt(context, Edge.MIDDLE, Edge.BOTTOM);
		teleport(context, server, 4030, 90);
		assertTriangleAt(context, Edge.MIDDLE, Edge.TOP);

		server.runCommand("tp @a 230 " + GROUND + " 200 180 0");
		context.waitTicks(SETTLE_TICKS);
		context.takeScreenshot("death-triangle-diagonal");
		// The tilted camera flattens north and south, so a 45 degree direction meets the left edge above the middle, not the corner.
		assertTriangleAt(context, Edge.LEFT, Edge.MIDDLE);
		check(drawn(context).y() < 40, "a death to the north-west must show in the map's upper half, was " + drawn(context));

		pressK(context);
		drawn = drawn(context);
		check(drawn.line() && drawn.mark() == DeathMarkers.Mark.NONE, "outside the map the line replaces the triangle, was " + drawn);
		context.takeScreenshot("death-line-to-corner");
		pressK(context);

		context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT);
		context.waitTicks(2);
		context.getInput().pressKey(GLFW.GLFW_KEY_K);
		context.waitTicks(2);
		context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);
		context.waitTicks(3);
		check(context.computeOnClient(client -> DeathMarkers.INSTANCE.isMarkerRemoved()), "Shift+K must remove the marker");
		check(!context.computeOnClient(client -> MehbursMiniMapClient.config().deathLineEnabled), "Shift+K must not touch the line");
		check(drawn(context).mark() == DeathMarkers.Mark.NONE, "the triangle must be gone");
		context.takeScreenshot("death-triangle-removed");

		teleport(context, server, 0, 180);
		check(drawn(context).mark() == DeathMarkers.Mark.NONE, "the skull must be gone too");

		context.getInput().pressKey(GLFW.GLFW_KEY_M);
		context.waitTicks(10);
		context.getInput().pressKey(GLFW.GLFW_KEY_K);
		context.waitTicks(3);
		check(context.computeOnClient(client -> MehbursMiniMapClient.config().deathLineEnabled), "K must also work on the big map");
		context.takeScreenshot("death-big-map");
		context.getInput().pressKey(GLFW.GLFW_KEY_M);
		context.waitTicks(20);

		boolean sameKey = context.computeOnClient(client -> MinimapKeys.TOGGLE_DEATH_LINE.same(MinimapKeys.REMOVE_DEATH_MARKER));
		check(sameKey, "both death keys must default to K");
	}

	private static void testNewDeathsAndDimensions(ClientGameTestContext context, TestServerContext server) {
		teleport(context, server, -25, 180);
		die(context, server);
		assertPoint(context, OVERWORLD, -25, 0);
		check(!context.computeOnClient(client -> DeathMarkers.INSTANCE.isMarkerRemoved()), "a new death must bring the marker back");
		teleport(context, server, 0, 180);
		check(drawn(context).mark() == DeathMarkers.Mark.SKULL, "the new death must show");

		// Without a death screen the client may never see its health at zero, only the server's report.
		server.runCommand("gamerule immediate_respawn true");
		server.runCommand("tp @a 15 " + GROUND + " 0 180 0");
		context.waitTicks(10);
		server.runCommand("kill @a");
		context.waitTicks(SETTLE_TICKS);
		assertPoint(context, OVERWORLD, 15, 0);
		server.runCommand("gamerule immediate_respawn false");

		server.runCommand("execute in minecraft:the_nether run tp @a 0.5 70 0.5 180 0");
		context.waitTicks(SETTLE_TICKS);
		DeathMarkers.Drawn drawn = drawn(context);
		check(drawn.mark() == DeathMarkers.Mark.NONE && !drawn.line(), "an overworld death must not show in the Nether, was " + drawn);
		context.takeScreenshot("death-other-dimension");

		server.runCommand("execute in minecraft:the_nether run tp @a 8.5 70 8.5 180 0");
		context.waitTicks(10);
		server.runCommand("kill @a");
		context.waitTicks(SETTLE_TICKS);
		context.runOnClient(client -> {
			if (client.gui.screen() instanceof DeathScreen) {
				client.player.respawn();
				client.gui.setScreen(null);
			}
		});
		context.waitTicks(SETTLE_TICKS);
		DeathPoint point = context.computeOnClient(client -> DeathMarkers.INSTANCE.point());
		check(point != null && NETHER.equals(point.dimension()), "the Nether death must be saved, was " + point);
		drawn = drawn(context);
		check(drawn.mark() == DeathMarkers.Mark.NONE && !drawn.line(), "a Nether death must not show in the overworld, was " + drawn);
	}

	/** A real server: the death arrives over the network, and the dots come from what it sends. */
	private static void testDedicatedServer(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set noon");
			try (var connection = server.connect()) {
				context.waitTicks(SETTLE_TICKS);
				String player = context.computeOnClient(client -> client.player.getGameProfile().name());
				server.runCommand("tp " + player + " 0.5 " + GROUND + " 0.5 180 0");
				server.runCommand("summon minecraft:cow 0.5 " + GROUND + " -12.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
				server.runCommand("summon minecraft:zombie 12.5 " + GROUND + " 0.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
				server.runCommand("summon minecraft:item -12.5 " + GROUND + " 0.5 {Item:{id:\"minecraft:diamond\",count:1},PickupDelay:32767s}");
				context.waitTicks(SETTLE_TICKS);
				context.takeScreenshot("server-dots");
				assertDot(context, "cow", EntityDotType.PASSIVE);
				assertDot(context, "zombie", EntityDotType.HOSTILE);
				assertDot(context, "item", EntityDotType.ITEM);

				server.runCommand("tp " + player + " 20 " + GROUND + " 0 180 0");
				context.waitTicks(10);
				server.runCommand("kill " + player);
				context.waitTicks(SETTLE_TICKS);
				context.runOnClient(client -> {
					client.player.respawn();
					client.gui.setScreen(null);
				});
				context.waitTicks(SETTLE_TICKS);
				DeathPoint point = context.computeOnClient(client -> DeathMarkers.INSTANCE.point());
				check(point != null && OVERWORLD.equals(point.dimension()) && point.x() == 20 && point.z() == 0, "death on a server saved as " + point);
				server.runCommand("tp " + player + " 0.5 " + GROUND + " 0.5 180 0");
				context.waitTicks(SETTLE_TICKS);
				context.takeScreenshot("server-death-mark");
				check(drawn(context).mark() == DeathMarkers.Mark.SKULL, "the death on a server must show");
			}
		}
	}

	private static void assertDot(ClientGameTestContext context, String id, EntityDotType type) {
		boolean found = context.computeOnClient(client -> {
			for (EntityRadar.Dot dot : EntityRadar.INSTANCE.dots()) {
				if (dot.type() == type && BuiltInRegistries.ENTITY_TYPE.getKey(dot.entity().getType()).getPath().equals(id)) {
					return true;
				}
			}
			return false;
		});
		check(found, "on a server, " + id + " has no " + type + " dot");
	}

	private static void teleport(ClientGameTestContext context, TestServerContext server, int x, int yaw) {
		server.runCommand("tp @a " + x + " " + GROUND + " 0 " + yaw + " 0");
		context.waitTicks(SETTLE_TICKS);
	}

	private static void die(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("kill @a");
		context.waitTicks(10);
		boolean deathScreen = context.computeOnClient(client -> client.gui.screen() instanceof DeathScreen);
		check(deathScreen, "the death screen must be open");
		context.runOnClient(client -> {
			client.player.respawn();
			client.gui.setScreen(null);
		});
		context.waitTicks(SETTLE_TICKS);
	}

	private static void pressK(ClientGameTestContext context) {
		context.getInput().pressKey(GLFW.GLFW_KEY_K);
		context.waitTicks(3);
	}

	private static DeathMarkers.Drawn drawn(ClientGameTestContext context) {
		return context.computeOnClient(client -> DeathMarkers.INSTANCE.drawn());
	}

	private static void assertPoint(ClientGameTestContext context, String dimension, int x, int z) {
		DeathPoint point = context.computeOnClient(client -> DeathMarkers.INSTANCE.point());
		check(point != null && dimension.equals(point.dimension()) && point.x() == x && point.z() == z,
			"expected a death at " + x + ", " + z + " in " + dimension + " but found " + point);
	}

	private enum Edge { LEFT, TOP, RIGHT, BOTTOM, MIDDLE }

	/** The small map sits in the top-left corner, 8 units from the screen's edges and 80 wide. */
	private static void assertTriangleAt(ClientGameTestContext context, Edge horizontal, Edge vertical) {
		DeathMarkers.Drawn drawn = drawn(context);
		check(drawn.mark() == DeathMarkers.Mark.TRIANGLE, "expected a triangle, was " + drawn);
		int mapStart = 8;
		int mapEnd = 88;
		int middle = (mapStart + mapEnd) / 2;
		checkPosition(drawn.x(), horizontal, mapStart, mapEnd, middle, drawn);
		checkPosition(drawn.y(), vertical, mapStart, mapEnd, middle, drawn);
	}

	private static void checkPosition(int value, Edge edge, int start, int end, int middle, DeathMarkers.Drawn drawn) {
		int expected = switch (edge) {
			case LEFT, TOP -> start;
			case RIGHT, BOTTOM -> end;
			case MIDDLE -> middle;
		};
		int tolerance = edge == Edge.MIDDLE ? 4 * EDGE_TOLERANCE : EDGE_TOLERANCE;
		check(Math.abs(value - expected) <= tolerance, "expected the triangle near " + edge + " (" + expected + "), was " + drawn);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
