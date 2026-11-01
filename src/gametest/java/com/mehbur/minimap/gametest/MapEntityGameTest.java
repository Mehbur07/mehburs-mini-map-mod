package com.mehbur.minimap.gametest;

import com.mehbur.minimap.MehbursMiniMapClient;
import com.mehbur.minimap.config.MinimapConfig;
import com.mehbur.minimap.entity.EntityClassifier;
import com.mehbur.minimap.entity.EntityDotType;
import com.mehbur.minimap.entity.EntityRadar;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import org.lwjgl.glfw.GLFW;

/** Checks every dot rule against mobs placed at known positions. Screenshots land in build/run/clientGameTest/screenshots. */
public class MapEntityGameTest implements FabricClientGameTest {
	private static final int SETTLE_TICKS = 40;
	private static final String STAY = "{NoAI:1b,Silent:1b,PersistenceRequired:1b}";
	private static final String REMOTE_PLAYER = "other-player";
	private static final int REMOTE_PLAYER_ENTITY_ID = Integer.MAX_VALUE - 1;

	private record Seen(EntityDotType type, int x, int y, String name) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		checkEveryEntityType(context);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamemode creative @a");
			server.runCommand("tp @a 0.5 -60 0.5 180 0");
			context.waitTicks(SETTLE_TICKS);

			// A gold block under each mob of the compass, to compare dots against the terrain by eye.
			server.runCommand("setblock 0 -61 -15 gold_block");
			server.runCommand("setblock 15 -61 0 gold_block");
			server.runCommand("setblock -15 -61 0 gold_block");
			server.runCommand("setblock 0 -61 15 gold_block");
			summon(server, "cow", "0.5 -60 -14.5", STAY);
			summon(server, "wolf", "15.5 -60 0.5", STAY);
			summon(server, "zombie", "-14.5 -60 0.5", STAY);
			summon(server, "warden", "0.5 -60 15.5", STAY);
			summon(server, "spider", "10.5 -60 -10.5", STAY);
			summon(server, "piglin", "-10.5 -60 -10.5", "{NoAI:1b,Silent:1b,PersistenceRequired:1b,IsImmuneToZombification:1b}");
			summon(server, "skeleton", "10.5 -60 10.5", "{NoAI:1b,Silent:1b,active_effects:[{id:\"minecraft:invisibility\",duration:-1}]}");
			server.runCommand("fill 18 -60 18 26 -52 26 stone");
			server.runCommand("fill 21 -60 21 23 -59 23 air");
			summon(server, "creeper", "22.5 -60 22.5", STAY);
			addRemotePlayer(context);
			context.waitTicks(SETTLE_TICKS);
			context.takeScreenshot("dots-facing-north");

			Map<String, Seen> seen = seen(context);
			assertType(seen, "cow", EntityDotType.PASSIVE);
			assertType(seen, "wolf", EntityDotType.NEUTRAL);
			assertType(seen, "zombie", EntityDotType.HOSTILE);
			assertType(seen, "warden", EntityDotType.BOSS);
			assertType(seen, "spider", EntityDotType.NEUTRAL);
			assertType(seen, "piglin", EntityDotType.HOSTILE);
			assertType(seen, REMOTE_PLAYER, EntityDotType.OTHER_PLAYER);
			assertAbsent(seen, "skeleton", "an invisible mob");
			assertAbsent(seen, "creeper", "a mob in a cave under the surface view");
			assertAbsent(seen, "player", "the player's own entity");

			int centerX = (seen.get("zombie").x + seen.get("wolf").x) / 2;
			int centerY = (seen.get("cow").y + seen.get("warden").y) / 2;
			check(seen.get("cow").y < centerY, "north must be up when facing north");
			check(seen.get("warden").y > centerY, "south must be down when facing north");
			check(seen.get("wolf").x > centerX, "east must be right when facing north");
			check(seen.get("zombie").x < centerX, "west must be left when facing north");

			server.runCommand("item replace entity @a armor.head with minecraft:golden_helmet");
			server.runCommand("time set midnight");
			context.waitTicks(SETTLE_TICKS);
			seen = seen(context);
			assertType(seen, "piglin", EntityDotType.NEUTRAL);
			assertType(seen, "spider", EntityDotType.HOSTILE);
			server.runCommand("time set noon");

			server.runCommand("tp @a 0.5 -60 0.5 90 0");
			context.waitTicks(SETTLE_TICKS);
			context.takeScreenshot("dots-facing-west");
			seen = seen(context);
			centerX = (seen.get("cow").x + seen.get("warden").x) / 2;
			check(seen.get("cow").x > centerX, "north must be right when facing west");

			testHover(context);
			testToggles(context);
			testCaveLayer(context, server);
		}
	}

	private static void testHover(ClientGameTestContext context) {
		context.getInput().pressKey(GLFW.GLFW_KEY_M);
		context.waitTicks(SETTLE_TICKS);
		context.takeScreenshot("dots-big-map");
		Map<String, Seen> seen = seen(context);
		Seen cow = seen.get("cow");
		int scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		context.getInput().setCursorPos(cow.x * scale, cow.y * scale);
		context.waitTicks(5);
		context.takeScreenshot("dots-big-map-hover-cow");
		String hovered = context.computeOnClient(client -> {
			var name = EntityRadar.INSTANCE.nameAt(cow.x, cow.y);
			return name == null ? null : name.getString();
		});
		check(cow.name.equals(hovered), "hovering the cow's dot showed " + hovered);

		Seen player = seen.get(REMOTE_PLAYER);
		context.getInput().setCursorPos(player.x * scale, player.y * scale);
		context.waitTicks(5);
		context.takeScreenshot("dots-big-map-hover-player");
		check("Steve".equals(player.name), "the other player's dot is named " + player.name);

		context.getInput().setCursorPos(2.0, 2.0);
		String nothing = context.computeOnClient(client -> {
			var name = EntityRadar.INSTANCE.nameAt(0, 0);
			return name == null ? null : name.getString();
		});
		check(nothing == null, "an empty spot showed the name " + nothing);
		context.getInput().pressKey(GLFW.GLFW_KEY_M);
		context.waitTicks(20);
	}

	private static void testToggles(ClientGameTestContext context) {
		context.runOnClient(client -> MehbursMiniMapClient.config().showMobs = false);
		context.waitTicks(5);
		Map<String, Seen> seen = seen(context);
		assertAbsent(seen, "cow", "a mob with mob dots turned off");
		assertType(seen, REMOTE_PLAYER, EntityDotType.OTHER_PLAYER);

		context.runOnClient(client -> {
			MinimapConfig config = MehbursMiniMapClient.config();
			config.showMobs = true;
			config.showPlayers = false;
		});
		context.waitTicks(5);
		seen = seen(context);
		assertAbsent(seen, REMOTE_PLAYER, "a player with player dots turned off");
		assertType(seen, "cow", EntityDotType.PASSIVE);
		context.runOnClient(client -> MehbursMiniMapClient.config().showPlayers = true);
	}

	private static void testCaveLayer(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("tp @a 100.5 -40 100.5 180 0");
		context.waitTicks(SETTLE_TICKS);
		server.runCommand("fill 80 -60 80 120 -45 120 stone");
		server.runCommand("fill 100 -56 100 101 -55 118 air"); // the player's corridor, running south
		server.runCommand("fill 96 -60 106 98 -59 108 air"); // a sealed cave below the corridor
		server.runCommand("fill 108 -50 106 110 -49 108 air"); // a chamber above the cut
		summon(server, "zombie", "100.5 -56 112.5", STAY);
		summon(server, "skeleton", "97.5 -60 107.5", STAY);
		summon(server, "creeper", "109.5 -50 107.5", STAY);
		summon(server, "cow", "104.5 -44 104.5", STAY);
		server.runCommand("tp @a 100.5 -56 100.5 180 0");
		context.waitTicks(SETTLE_TICKS);
		context.takeScreenshot("dots-in-cave");

		Map<String, Seen> seen = seen(context);
		assertType(seen, "zombie", EntityDotType.HOSTILE);
		assertAbsent(seen, "skeleton", "a mob behind rock below the cave view");
		assertAbsent(seen, "creeper", "a mob above the cut");
		assertAbsent(seen, "cow", "a mob on the surface above the cave");
	}

	/** Runs the classifier on everything the game can spawn, so that a wrong color cannot hide in a mob nobody placed. */
	private static void checkEveryEntityType(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getServer().runCommand("time set noon");
			context.waitTicks(10);
			Map<String, EntityDotType> types = context.computeOnClient(MapEntityGameTest::classifyAll);
			Map<EntityDotType, List<String>> byType = new HashMap<>();
			types.forEach((id, type) -> byType.computeIfAbsent(type, key -> new ArrayList<>()).add(id));
			byType.forEach((type, ids) -> MehbursMiniMapClient.LOGGER.info("[minimap-test] {}: {}", type, ids.stream().sorted().toList()));

			expect(types, EntityDotType.BOSS, Set.of("ender_dragon", "wither", "warden", "elder_guardian"));
			expect(types, EntityDotType.HOSTILE, Set.of("zombie", "skeleton", "creeper", "witch", "slime", "magma_cube", "ghast", "phantom",
				"hoglin", "zoglin", "shulker", "vex", "blaze", "drowned", "guardian", "silverfish", "piglin_brute", "ravager", "breeze", "creaking"));
			expect(types, EntityDotType.NEUTRAL, Set.of("wolf", "bee", "enderman", "iron_golem", "zombified_piglin", "polar_bear"));
			expect(types, EntityDotType.PASSIVE, Set.of("cow", "sheep", "pig", "chicken", "villager", "wandering_trader", "bat", "cod", "squid",
				"allay", "snow_golem", "axolotl", "strider", "horse", "cat"));
			check(!types.containsKey("armor_stand"), "armor stands must not get a dot");
			check(!types.containsKey("oak_boat"), "boats must not get a dot");
		}
	}

	private static Map<String, EntityDotType> classifyAll(Minecraft client) {
		Map<String, EntityDotType> types = new HashMap<>();
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			Entity entity = type.create(client.level, EntitySpawnReason.COMMAND);
			if (entity != null) {
				entity.setPos(client.player.position());
				EntityDotType dot = EntityClassifier.classify(entity, client.player);
				if (dot != null) {
					types.put(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath(), dot);
				}
			}
		}
		return types;
	}

	private static void expect(Map<String, EntityDotType> types, EntityDotType expected, Set<String> ids) {
		for (String id : ids) {
			check(types.get(id) == expected, id + " must be " + expected + " but is " + types.get(id));
		}
	}

	private static void summon(TestServerContext server, String type, String position, String data) {
		server.runCommand("summon minecraft:" + type + " " + position + " " + data);
	}

	/** A second real player cannot join a test world, so the client gets the same entity a server would announce. */
	private static void addRemotePlayer(ClientGameTestContext context) {
		context.runOnClient(client -> {
			RemotePlayer player = new RemotePlayer(client.level, new GameProfile(UUID.nameUUIDFromBytes(new byte[] {1}), "Steve"));
			player.setId(REMOTE_PLAYER_ENTITY_ID);
			player.setPos(-11.5, -60.0, 12.5);
			client.level.addEntity(player);
		});
	}

	private static Map<String, Seen> seen(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			Map<String, Seen> result = new HashMap<>();
			for (EntityRadar.Dot dot : EntityRadar.INSTANCE.dots()) {
				String id = dot.entity() instanceof RemotePlayer ? REMOTE_PLAYER : BuiltInRegistries.ENTITY_TYPE.getKey(dot.entity().getType()).getPath();
				result.put(id, new Seen(dot.type(), dot.x(), dot.y(), dot.entity().getName().getString()));
			}
			return result;
		});
	}

	private static void assertType(Map<String, Seen> seen, String id, EntityDotType type) {
		Seen dot = seen.get(id);
		check(dot != null, id + " has no dot, the map shows " + seen.keySet());
		check(dot.type == type, id + " must be " + type + " but is " + dot.type);
	}

	private static void assertAbsent(Map<String, Seen> seen, String id, String description) {
		check(!seen.containsKey(id), "The map shows " + description + " (" + id + ")");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
