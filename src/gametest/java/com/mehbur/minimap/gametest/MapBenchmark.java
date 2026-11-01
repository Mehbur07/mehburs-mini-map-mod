package com.mehbur.minimap.gametest;

import com.mehbur.minimap.MehbursMiniMapClient;
import com.mehbur.minimap.render.MinimapHud;
import com.mehbur.minimap.render.terrain.TerrainCache;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Measures real frame rates, which client gametests cannot do because they lock frames to game ticks.
 * Runs only with {@code -Dmehburs_minimap.bench=<save name>}: opens that save, then compares the game with and
 * without the map, standing still and turning so the map redraws constantly. Results go to the log, then the game exits.
 */
public class MapBenchmark implements ClientModInitializer {
	private static final String SAVE_PROPERTY = "mehburs_minimap.bench";
	private static final int SETTLE_TICKS = 200;
	private static final int WARMUP_TICKS = 60;
	private static final int WINDOW_TICKS = 300;
	private static final float TURN_DEGREES_PER_TICK = 6f;
	private static final int ROUNDS = 2;
	private static final int LOG_WAITING_EVERY_TICKS = 100;
	private static final int UNLIMITED_FPS = 260;

	private static final String[] PHASES = {"off-still", "on-still", "off-turning", "on-turning"};

	private boolean opened;
	private int waitingTicks;
	private int settle;
	private int phase;
	private int round;
	private int windowTick;
	private long fpsSum;

	@Override
	public void onInitializeClient() {
		String save = System.getProperty(SAVE_PROPERTY);
		if (save != null) {
			ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client, save));
		}
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
		runWindow(client);
	}

	private void runWindow(Minecraft client) {
		String name = PHASES[phase];
		MinimapHud.setEnabled(name.startsWith("on"));
		if (name.endsWith("turning")) {
			client.player.setYRot(client.player.getYRot() + TURN_DEGREES_PER_TICK);
		}
		windowTick++;
		if (windowTick > WARMUP_TICKS) {
			fpsSum += client.getFps();
		}
		if (windowTick < WARMUP_TICKS + WINDOW_TICKS) {
			return;
		}
		MehbursMiniMapClient.LOGGER.info("[minimap-bench] round={} {} fps={} meshMegabytes={}",
			round, name, fpsSum / WINDOW_TICKS, TerrainCache.loadedBytes() / (1024 * 1024));
		windowTick = 0;
		fpsSum = 0;
		phase++;
		if (phase == PHASES.length) {
			phase = 0;
			round++;
			if (round == ROUNDS) {
				client.stop();
			}
		}
	}
}
