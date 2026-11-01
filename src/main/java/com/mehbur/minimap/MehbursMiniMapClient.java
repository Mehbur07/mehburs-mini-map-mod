package com.mehbur.minimap;

import com.mehbur.minimap.config.MinimapConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MehbursMiniMapClient implements ClientModInitializer {
	public static final String MOD_ID = "mehburs_minimap";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static MinimapConfig config;

	@Override
	public void onInitializeClient() {
		config = MinimapConfig.load(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json"));
		LOGGER.info("Mehbur's Mini Map initialized");
	}

	public static MinimapConfig config() {
		return config;
	}
}
