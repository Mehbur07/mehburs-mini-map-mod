package com.mehbur.minimap.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.JsonAdapter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** User settings, persisted as JSON. Fields missing from the file keep their defaults. */
public final class MinimapConfig {
	public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

	private static final Logger LOGGER = LoggerFactory.getLogger("mehburs_minimap");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final int MIN_MAP_SIZE = 64;
	public static final int MAX_MAP_SIZE = 512;
	public static final float MIN_ZOOM = 0.25f;
	public static final float MAX_ZOOM = 8f;
	public static final int MAX_ANIMATION_MILLIS = 2000;

	@JsonAdapter(HexColorAdapter.class) public int playerDotColor = 0x00FFFF;
	@JsonAdapter(HexColorAdapter.class) public int passiveColor = 0x39FF14;
	@JsonAdapter(HexColorAdapter.class) public int neutralColor = 0xFF8C00;
	@JsonAdapter(HexColorAdapter.class) public int hostileColor = 0xFF073A;
	@JsonAdapter(HexColorAdapter.class) public int bossColor = 0x800020;
	@JsonAdapter(HexColorAdapter.class) public int otherPlayerColor = 0xFFFFFF;
	@JsonAdapter(HexColorAdapter.class) public int arrowColor = 0x8B5A2B;
	@JsonAdapter(HexColorAdapter.class) public int itemColor = 0xFFFF33;
	@JsonAdapter(HexColorAdapter.class) public int enderPearlColor = 0xBC13FE;

	public int mapSize = 80;
	public Corner corner = Corner.TOP_LEFT;
	public float zoom = 1f;
	public boolean northLocked = false;
	public boolean simplifyDistantTextures = true;
	public boolean autoCaveMode = true;

	public boolean showMobs = true;
	public boolean showPlayers = true;
	public boolean showItems = true;
	public boolean showArrows = true;
	public boolean showEnderPearls = true;

	public int animationMillis = 350;

	public boolean deathLineEnabled = true;
	public boolean deathTriangleEnabled = true;

	private transient Path path;

	/** Reads the config at {@code path}, writing a default file if none exists. Never throws. */
	public static MinimapConfig load(Path path) {
		MinimapConfig config = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				config = GSON.fromJson(reader, MinimapConfig.class);
			} catch (IOException | JsonParseException e) {
				LOGGER.warn("Could not read {}; using defaults", path, e);
				backUpBrokenFile(path);
			}
		}
		boolean isNew = config == null;
		if (isNew) {
			config = new MinimapConfig();
		}
		config.path = path;
		config.sanitize();
		if (isNew) {
			config.save();
		}
		return config;
	}

	/** Writes through a temporary file so a crash mid-write cannot corrupt the existing config. */
	public void save() {
		try {
			Files.createDirectories(path.getParent());
			Path temp = path.resolveSibling(path.getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(this), StandardCharsets.UTF_8);
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOGGER.error("Could not save {}", path, e);
		}
	}

	private void sanitize() {
		mapSize = Math.clamp(mapSize, MIN_MAP_SIZE, MAX_MAP_SIZE);
		zoom = Float.isNaN(zoom) ? 1f : Math.clamp(zoom, MIN_ZOOM, MAX_ZOOM);
		animationMillis = Math.clamp(animationMillis, 0, MAX_ANIMATION_MILLIS);
		if (corner == null) {
			corner = Corner.TOP_LEFT;
		}
	}

	private static void backUpBrokenFile(Path path) {
		try {
			Files.move(path, path.resolveSibling(path.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOGGER.warn("Could not back up {}", path, e);
		}
	}
}
