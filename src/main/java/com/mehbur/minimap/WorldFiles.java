package com.mehbur.minimap;

import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;

/** Where data tied to the current world goes. */
public final class WorldFiles {
	private WorldFiles() {
	}

	/** Worlds keep the file in their own folder, so that it goes away with them. Servers get a file per address. */
	public static Path path(Minecraft minecraft, String worldFile, String serverFolder) {
		if (minecraft.hasSingleplayerServer()) {
			return minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT).resolve(worldFile);
		}
		ServerData server = minecraft.getCurrentServer();
		String address = server == null ? "unknown" : server.ip;
		String readable = address.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.-]", "_");
		// Different addresses can look the same once cleaned up, and only the hash tells them apart.
		String hash = HexFormat.of().toHexDigits(address.hashCode());
		return FabricLoader.getInstance().getConfigDir().resolve(MehbursMiniMapClient.MOD_ID).resolve(serverFolder).resolve(readable + "_" + hash + ".json");
	}
}
