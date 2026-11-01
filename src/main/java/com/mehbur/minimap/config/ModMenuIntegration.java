package com.mehbur.minimap.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/** Adds the settings button to Mod Menu, which only loads this class when it is installed. */
public final class ModMenuIntegration implements ModMenuApi {
	private static final String CLOTH_CONFIG = "cloth-config";

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		// A null screen makes Mod Menu show no button, and the screen class must not load without Cloth Config.
		return FabricLoader.getInstance().isModLoaded(CLOTH_CONFIG) ? MinimapConfigScreen::create : parent -> null;
	}
}
