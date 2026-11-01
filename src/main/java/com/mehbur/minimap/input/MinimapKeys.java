package com.mehbur.minimap.input;

import com.mehbur.minimap.MehbursMiniMapClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** The mod's entries in the Controls screen. */
public final class MinimapKeys {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MehbursMiniMapClient.MOD_ID, "main"));

	public static final KeyMapping TOGGLE_MAP = new KeyMapping("key.mehburs_minimap.toggle_map", InputConstants.KEY_M, CATEGORY);

	private MinimapKeys() {
	}

	public static void register() {
		KeyMappingHelper.registerKeyMapping(TOGGLE_MAP);
		ClientTickEvents.END_CLIENT_TICK.register(MinimapKeys::tick);
	}

	private static void tick(Minecraft minecraft) {
		while (TOGGLE_MAP.consumeClick()) {
			// Key mappings only fire without a screen open, so closing happens in the big map's own key handler.
			if (minecraft.player != null) {
				minecraft.gui.setScreen(new BigMapScreen());
			}
		}
	}
}
