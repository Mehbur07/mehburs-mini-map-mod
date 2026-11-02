package com.mehbur.minimap.input;

import com.mehbur.minimap.MehbursMiniMapClient;
import com.mehbur.minimap.death.DeathMarkers;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/** The mod's entries in the Controls screen. */
public final class MinimapKeys {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MehbursMiniMapClient.MOD_ID, "main"));

	public static final KeyMapping TOGGLE_MAP = new KeyMapping("key.mehburs_minimap.toggle_map", InputConstants.KEY_M, CATEGORY);
	/** Iris toggles shaders on K, so every press of a death key would also turn the player's shaders off. */
	private static final int DEATH_KEY = FabricLoader.getInstance().isModLoaded("iris") ? InputConstants.KEY_J : InputConstants.KEY_K;

	public static final KeyMapping TOGGLE_DEATH_LINE = new KeyMapping("key.mehburs_minimap.toggle_death_line", DEATH_KEY, CATEGORY);
	/** Shares its key with the line by default, because key mappings cannot include Shift. Shift then tells the two apart. */
	public static final KeyMapping REMOVE_DEATH_MARKER = new KeyMapping("key.mehburs_minimap.remove_death_marker", DEATH_KEY, CATEGORY);

	private MinimapKeys() {
	}

	public static void register() {
		KeyMappingHelper.registerKeyMapping(TOGGLE_MAP);
		KeyMappingHelper.registerKeyMapping(TOGGLE_DEATH_LINE);
		KeyMappingHelper.registerKeyMapping(REMOVE_DEATH_MARKER);
		ClientTickEvents.END_CLIENT_TICK.register(MinimapKeys::tick);
	}

	private static void tick(Minecraft minecraft) {
		Player player = minecraft.player;
		while (TOGGLE_MAP.consumeClick()) {
			// Key mappings only fire without a screen open, so closing happens in the big map's own key handler.
			if (player != null) {
				minecraft.gui.setScreen(new BigMapScreen());
			}
		}
		boolean shift = minecraft.hasShiftDown();
		while (TOGGLE_DEATH_LINE.consumeClick()) {
			if (player != null && linePressed(shift)) {
				DeathMarkers.INSTANCE.toggleLine(player);
			}
		}
		while (REMOVE_DEATH_MARKER.consumeClick()) {
			if (player != null && markerPressed(shift)) {
				DeathMarkers.INSTANCE.removeMarker(player);
			}
		}
	}

	/** The same keys for screens that let the game keep running, which receive key events instead of mapping clicks. */
	static boolean handleDeathKeys(KeyEvent event, Player player) {
		if (TOGGLE_DEATH_LINE.matches(event) && linePressed(event.hasShiftDown())) {
			DeathMarkers.INSTANCE.toggleLine(player);
			return true;
		}
		if (REMOVE_DEATH_MARKER.matches(event) && markerPressed(event.hasShiftDown())) {
			DeathMarkers.INSTANCE.removeMarker(player);
			return true;
		}
		return false;
	}

	private static boolean linePressed(boolean shift) {
		return !TOGGLE_DEATH_LINE.same(REMOVE_DEATH_MARKER) || !shift;
	}

	private static boolean markerPressed(boolean shift) {
		return !TOGGLE_DEATH_LINE.same(REMOVE_DEATH_MARKER) || shift;
	}
}
