package com.mehbur.minimap.gametest;

import com.mehbur.minimap.MehbursMiniMapClient;
import com.mehbur.minimap.config.MinimapConfig;
import com.mehbur.minimap.input.BigMapScreen;
import com.mehbur.minimap.render.MinimapHud;
import com.mehbur.minimap.render.terrain.TerrainCache;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;

/**
 * Measures real frame rates, which client gametests cannot do because they lock frames to game ticks.
 * Runs only with {@code -Dmehburs_minimap.bench=<save name>}: opens that save, then compares the game with and
 * without the map while standing, turning, travelling into new chunks, descending underground, which makes the
 * map rebuild its layer, and surrounded by crowds of mobs, items and arrows. Results go to the log, then the game exits.
 */
public class MapBenchmark implements ClientModInitializer {
	private static final String SAVE_PROPERTY = "mehburs_minimap.bench";
	private static final int SETTLE_TICKS = 200;
	private static final int WARMUP_TICKS = 60;
	private static final int WINDOW_TICKS = 150;
	private static final int MOB_COUNT = 500;
	private static final int ARROW_COUNT = 100;
	private static final double MOB_RADIUS = 30.0;
	private static final double GOLDEN_ANGLE = 2.39996;
	private static final String MOB_TAG = "minimap_bench";
	private static final float TURN_DEGREES_PER_TICK = 6f;
	private static final float DEFAULT_BIG_MAP_ZOOM = 0.5f;
	private static final int MOVE_BLOCKS_PER_TICK = 2;
	private static final int DESCEND_TOP_Y = 40;
	private static final int DESCEND_RANGE = 60;
	private static final int TICKS_PER_DESCENDED_BLOCK = 5;
	private static final int ROUNDS = 2;
	private static final int LOG_WAITING_EVERY_TICKS = 100;
	private static final int UNLIMITED_FPS = 260;
	private static final double NANOS_PER_MILLI = 1_000_000.0;

	private static final String[] PHASES = {
		"off-still", "on-still", "big-still", "off-turning", "on-turning", "big-turning", "bigfar-turning",
		"off-moving", "on-moving", "big-moving", "off-descending", "on-descending", "off-mobs", "on-mobs", "big-mobs", "off-items", "on-items", "big-items"
	};

	private final LongArrayList frameNanos = new LongArrayList();
	private boolean opened;
	private boolean started;
	private int waitingTicks;
	private int settle;
	private int phase;
	private int round;
	private int windowTick;
	private long lastFrame;
	private double startX;
	private double startY;
	private double startZ;
	private int travelled;

	@Override
	public void onInitializeClient() {
		String save = System.getProperty(SAVE_PROPERTY);
		if (save != null) {
			ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client, save));
			HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("mehburs_minimap_test", "bench_frames"), (graphics, deltaTracker) -> onFrame());
		}
	}

	private void onFrame() {
		long now = Util.getNanos();
		if (lastFrame != 0L && windowTick > WARMUP_TICKS) {
			frameNanos.add(now - lastFrame);
		}
		lastFrame = now;
	}

	private void tick(Minecraft client, String save) {
		if (client.level == null && ++waitingTicks % LOG_WAITING_EVERY_TICKS == 0) {
			Object screen = client.gui.screen();
			MehbursMiniMapClient.LOGGER.info("[minimap-bench] waiting on {}", screen == null ? "no screen" : screen.getClass().getName());
		}
		if (!opened) {
			if (client.gui.screen() instanceof TitleScreen || client.gui.screen() instanceof AccessibilityOnboardingScreen) {
				opened = true;
				MehbursMiniMapClient.LOGGER.info("[minimap-bench] opening {}", save);
				client.options.enableVsync().set(false);
				client.options.framerateLimit().set(UNLIMITED_FPS);
				// Otherwise the game drops to 30 FPS once nobody has touched it for a while.
				client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
				client.options.pauseOnLostFocus = false;
				client.createWorldOpenFlows().openWorld(save, () -> client.stop());
			}
			return;
		}
		if (client.player == null || client.level == null) {
			return;
		}
		if (settle < SETTLE_TICKS) {
			settle++;
			return;
		}
		if (!started) {
			started = true;
			startX = client.player.getX();
			startY = client.player.getY();
			startZ = client.player.getZ();
			// Spectators neither fall nor suffocate, so any position works.
			runCommand(client, "gamemode spectator @a");
		}
		runWindow(client);
	}

	private void runWindow(Minecraft client) {
		String name = PHASES[phase];
		MinimapHud.setEnabled(!name.startsWith("off"));
		if (windowTick == 0) {
			MehbursMiniMapClient.config().bigMapZoom = name.startsWith("bigfar") ? MinimapConfig.MIN_ZOOM : DEFAULT_BIG_MAP_ZOOM;
			client.gui.setScreen(name.startsWith("big") ? new BigMapScreen() : null);
		}
		windowTick++;
		movePlayer(client, name);
		if (windowTick < WARMUP_TICKS + WINDOW_TICKS) {
			return;
		}
		report(name);
		if (name.equals("big-mobs") || name.equals("big-items")) {
			runCommand(client, "kill @e[tag=" + MOB_TAG + "]");
		}
		windowTick = 0;
		frameNanos.clear();
		phase++;
		if (phase == PHASES.length) {
			phase = 0;
			round++;
			if (round == ROUNDS) {
				client.stop();
			}
		}
	}

	private void movePlayer(Minecraft client, String name) {
		if (name.endsWith("turning")) {
			client.player.setYRot(client.player.getYRot() + TURN_DEGREES_PER_TICK);
		} else if (name.endsWith("moving")) {
			travelled += MOVE_BLOCKS_PER_TICK;
			teleport(client, startX + travelled, startY, startZ);
		} else if (name.endsWith("descending")) {
			if (windowTick % TICKS_PER_DESCENDED_BLOCK == 0) {
				int depth = windowTick / TICKS_PER_DESCENDED_BLOCK % DESCEND_RANGE;
				teleport(client, startX, DESCEND_TOP_Y - depth, startZ);
			}
		} else if (windowTick == 1) {
			teleport(client, startX, startY, startZ);
			if (name.equals("off-mobs")) {
				summonCrowd(client, MOB_COUNT, "cow", "{NoAI:1b,Silent:1b,Tags:[\"" + MOB_TAG + "\"]}");
			} else if (name.equals("off-items")) {
				// The drops of the mob crowd would otherwise count as part of this crowd.
				runCommand(client, "kill @e[type=minecraft:item]");
				summonCrowd(client, MOB_COUNT, "item", "{Item:{id:\"minecraft:cobblestone\",count:1},PickupDelay:32767s,NoGravity:1b,Tags:[\"" + MOB_TAG + "\"]}");
				summonCrowd(client, ARROW_COUNT, "arrow", "{NoGravity:1b,Motion:[0.001,0.0,0.0],Tags:[\"" + MOB_TAG + "\"]}");
			}
		} else if (windowTick == WARMUP_TICKS && name.equals("off-still")) {
			// Otherwise the drops of the previous round's crowd would slow this round down.
			runCommand(client, "kill @e[type=minecraft:item]");
			runCommand(client, "kill @e[type=minecraft:experience_orb]");
		}
	}

	/** A crowd far beyond normal play, all inside the map, to show what the radar costs at worst. */
	private void summonCrowd(Minecraft client, int count, String type, String data) {
		for (int i = 0; i < count; i++) {
			double angle = i * GOLDEN_ANGLE;
			double distance = MOB_RADIUS * Math.sqrt((double) i / count);
			runCommand(client, "summon minecraft:" + type + " " + (startX + Math.cos(angle) * distance) + " " + startY + " " + (startZ + Math.sin(angle) * distance) + " " + data);
		}
	}

	private void report(String name) {
		long[] sorted = frameNanos.toLongArray();
		java.util.Arrays.sort(sorted);
		long total = java.util.Arrays.stream(sorted).sum();
		double fps = sorted.length * (NANOS_PER_MILLI * 1000.0) / Math.max(total, 1L);
		double p99 = sorted[(int) (sorted.length * 0.99)] / NANOS_PER_MILLI;
		double worst = sorted[sorted.length - 1] / NANOS_PER_MILLI;
		MehbursMiniMapClient.LOGGER.info("[minimap-bench] round={} {} fps={} p99Ms={} worstMs={} layer={} meshMegabytes={}",
			round, name, Math.round(fps), String.format(java.util.Locale.ROOT, "%.1f", p99), String.format(java.util.Locale.ROOT, "%.1f", worst),
			TerrainCache.currentLayerY() == Integer.MIN_VALUE ? "surface" : TerrainCache.currentLayerY(), TerrainCache.loadedBytes() / (1024 * 1024));
	}

	private static void teleport(Minecraft client, double x, double y, double z) {
		runCommand(client, "tp @a " + x + " " + y + " " + z);
	}

	private static void runCommand(Minecraft client, String command) {
		MinecraftServer server = client.getSingleplayerServer();
		server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command));
	}
}
