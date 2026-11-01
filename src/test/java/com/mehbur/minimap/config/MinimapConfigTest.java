package com.mehbur.minimap.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MinimapConfigTest {
	@TempDir
	Path dir;

	private Path file() {
		return dir.resolve("config.json");
	}

	@Test
	void createsFileWithSpecDefaults() {
		MinimapConfig config = MinimapConfig.load(file());

		assertTrue(Files.exists(file()));
		assertEquals(0x00FFFF, config.playerDotColor);
		assertEquals(MinimapConfig.Corner.TOP_LEFT, config.corner);
	}

	@Test
	void changesSurviveReload() {
		MinimapConfig config = MinimapConfig.load(file());
		config.playerDotColor = 0x123456;
		config.corner = MinimapConfig.Corner.BOTTOM_LEFT;
		config.showItems = false;
		config.save();

		MinimapConfig reloaded = MinimapConfig.load(file());

		assertEquals(0x123456, reloaded.playerDotColor);
		assertEquals(MinimapConfig.Corner.BOTTOM_LEFT, reloaded.corner);
		assertFalse(reloaded.showItems);
	}

	@Test
	void colorsAreWrittenAsHex() throws IOException {
		MinimapConfig.load(file());

		assertTrue(Files.readString(file()).contains("\"playerDotColor\": \"#00FFFF\""));
	}

	@Test
	void missingFieldsKeepDefaults() throws IOException {
		Files.writeString(file(), "{\"mapSize\": 200}");

		MinimapConfig config = MinimapConfig.load(file());

		assertEquals(200, config.mapSize);
		assertEquals(0x39FF14, config.passiveColor);
	}

	@Test
	void outOfRangeValuesAreClamped() throws IOException {
		Files.writeString(file(), "{\"mapSize\": 99999, \"zoom\": 0.0, \"animationMillis\": -5, \"corner\": \"NOPE\"}");

		MinimapConfig config = MinimapConfig.load(file());

		assertEquals(MinimapConfig.MAX_MAP_SIZE, config.mapSize);
		assertEquals(MinimapConfig.MIN_ZOOM, config.zoom);
		assertEquals(0, config.animationMillis);
		assertEquals(MinimapConfig.Corner.TOP_LEFT, config.corner);
	}

	@Test
	void brokenFileIsBackedUpAndReplaced() throws IOException {
		Files.writeString(file(), "{ not json");

		MinimapConfig config = MinimapConfig.load(file());

		assertEquals(0x00FFFF, config.playerDotColor);
		assertTrue(Files.exists(dir.resolve("config.json.broken")));
		assertTrue(Files.readString(file()).contains("playerDotColor"));
	}
}
