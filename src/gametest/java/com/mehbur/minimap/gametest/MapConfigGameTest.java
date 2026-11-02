package com.mehbur.minimap.gametest;

import com.mehbur.minimap.MehbursMiniMapClient;
import com.mehbur.minimap.config.MinimapConfig;
import com.terraformersmc.modmenu.ModMenu;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import me.shedaniel.clothconfig2.api.AbstractConfigEntry;
import me.shedaniel.clothconfig2.gui.AbstractConfigScreen;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.gui.entries.ColorEntry;
import me.shedaniel.clothconfig2.gui.entries.EnumListEntry;
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Opens the settings the way Mod Menu does, changes them through the screen, and checks that the map obeys. */
public class MapConfigGameTest implements FabricClientGameTest {
	private static final String MOD_ID = "mehburs_minimap";
	private static final int SETTLE_TICKS = 20;
	private static final int EXPECTED_ENTRIES = 26;
	private static final int NEW_PLAYER_COLOR = 0xFF00FF;
	private static final int NEW_MAP_SIZE = 150;
	private static final int NEW_ZOOM_PERCENT = 200;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("tp @a 0.5 -60 0.5 180 0");
			context.waitTicks(SETTLE_TICKS);
			context.takeScreenshot("config-before");

			context.runOnClient(MapConfigGameTest::checkTheScreen);
			context.runOnClient(client -> client.gui.setScreen(ModMenu.getConfigScreen(MOD_ID, null)));
			context.waitTicks(10);
			context.takeScreenshot("config-screen");

			context.runOnClient(MapConfigGameTest::changeEverythingAndSave);
			context.waitTicks(SETTLE_TICKS);
			context.runOnClient(MapConfigGameTest::checkTheChanges);
			context.runOnClient(client -> client.gui.setScreen(null));
			context.waitTicks(SETTLE_TICKS);
			context.takeScreenshot("config-after");

			context.runOnClient(client -> restoreDefaults());
		}
	}

	/** Every setting of the spec must be on the screen with translated text. */
	private static void checkTheScreen(Minecraft client) {
		Screen screen = ModMenu.getConfigScreen(MOD_ID, null);
		check(screen instanceof AbstractConfigScreen, "Mod Menu must open the settings, got " + screen);
		client.gui.setScreen(screen);
		Map<Component, List<AbstractConfigEntry<?>>> categories = ((AbstractConfigScreen) screen).getCategorizedEntries();
		check(categories.size() == 5, "expected 5 categories, found " + categories.size());
		int entries = 0;
		for (Map.Entry<Component, List<AbstractConfigEntry<?>>> category : categories.entrySet()) {
			checkTranslated(category.getKey());
			for (AbstractConfigEntry<?> entry : category.getValue()) {
				checkTranslated(entry.getFieldName());
				entries++;
			}
		}
		check(entries == EXPECTED_ENTRIES, "expected " + EXPECTED_ENTRIES + " settings, found " + entries);
	}

	private static void checkTranslated(Component text) {
		if (text.getContents() instanceof TranslatableContents contents) {
			check(Language.getInstance().has(contents.getKey()), "missing translation for " + contents.getKey());
		}
	}

	private static void changeEverythingAndSave(Minecraft client) {
		AbstractConfigScreen screen = (AbstractConfigScreen) client.gui.screen();
		for (List<AbstractConfigEntry<?>> category : screen.getCategorizedEntries().values()) {
			for (AbstractConfigEntry<?> entry : category) {
				String name = entry.getFieldName().getString();
				change(entry, name);
			}
		}
		screen.saveAll(false);
	}

	/** Cloth Config marks its text setter deprecated, but offers no other way to fill a color field from outside. */
	@SuppressWarnings("deprecation")
	private static void change(AbstractConfigEntry<?> entry, String name) {
		if (entry instanceof BooleanListEntry toggle) {
			AtomicBoolean value = field(BooleanListEntry.class, toggle, "bool");
			value.set(!value.get());
		} else if (entry instanceof ColorEntry color) {
			color.setValue(String.format("#%06X", NEW_PLAYER_COLOR));
		} else if (entry instanceof IntegerSliderEntry slider) {
			AtomicInteger value = field(IntegerSliderEntry.class, slider, "value");
			value.set(key(slider).endsWith(".map_size") ? NEW_MAP_SIZE : NEW_ZOOM_PERCENT);
		} else if (entry instanceof EnumListEntry<?> selector) {
			setBottomRight(selector);
		} else {
			throw new AssertionError("Unknown kind of setting: " + name);
		}
	}

	private static String key(AbstractConfigEntry<?> entry) {
		return entry.getFieldName().getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
	}

	/** Cycling through the screen's own value list is what clicking the button does. */
	private static void setBottomRight(EnumListEntry<?> selector) {
		AtomicInteger index = field(selector.getClass().getSuperclass(), selector, "index");
		index.set(MinimapConfig.Corner.BOTTOM_RIGHT.ordinal());
	}

	@SuppressWarnings("unchecked")
	static <T> T field(Class<?> owner, Object instance, String name) {
		try {
			Field field = owner.getDeclaredField(name);
			field.setAccessible(true);
			return (T) field.get(instance);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Cloth Config has no field " + name, e);
		}
	}

	private static void checkTheChanges(Minecraft client) {
		MinimapConfig config = MehbursMiniMapClient.config();
		check(config.mapSize == NEW_MAP_SIZE, "map size is " + config.mapSize);
		check(config.corner == MinimapConfig.Corner.BOTTOM_RIGHT, "corner is " + config.corner);
		check(config.zoom == 2f, "zoom is " + config.zoom);
		check(config.bigMapZoom == 2f, "big map zoom is " + config.bigMapZoom);
		check(config.animationMillis == NEW_ZOOM_PERCENT, "animation is " + config.animationMillis);
		check(config.northLocked, "north lock must have turned on");
		check(!config.autoCaveMode && !config.simplifyDistantTextures && !config.showFps && !config.showCoordinates, "the map toggles must have turned off");
		check(!config.showMobs && !config.showPlayers && !config.showItems && !config.showArrows && !config.showEnderPearls, "the dot toggles must have turned off");
		check(!config.deathLineEnabled && !config.deathTriangleEnabled, "the death toggles must have turned off");
		for (int color : List.of(config.playerDotColor, config.passiveColor, config.neutralColor, config.hostileColor, config.bossColor,
			config.otherPlayerColor, config.arrowColor, config.itemColor, config.enderPearlColor)) {
			check(color == NEW_PLAYER_COLOR, "a color was saved as " + Integer.toHexString(color));
		}
		Path file = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
		try {
			String json = Files.readString(file);
			check(json.contains("\"playerDotColor\": \"#FF00FF\""), "the file holds no new color: " + json);
			check(json.contains("\"mapSize\": " + NEW_MAP_SIZE), "the file holds no new size: " + json);
			check(json.contains("\"corner\": \"BOTTOM_RIGHT\""), "the file holds no new corner: " + json);
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
	}

	static void restoreDefaults() {
		MinimapConfig config = MehbursMiniMapClient.config();
		MinimapConfig defaults = new MinimapConfig();
		config.mapSize = defaults.mapSize;
		config.corner = defaults.corner;
		config.zoom = defaults.zoom;
		config.bigMapZoom = defaults.bigMapZoom;
		config.animationMillis = defaults.animationMillis;
		config.northLocked = defaults.northLocked;
		config.autoCaveMode = defaults.autoCaveMode;
		config.simplifyDistantTextures = defaults.simplifyDistantTextures;
		config.showFps = defaults.showFps;
		config.showCoordinates = defaults.showCoordinates;
		config.showMobs = defaults.showMobs;
		config.showPlayers = defaults.showPlayers;
		config.showItems = defaults.showItems;
		config.showArrows = defaults.showArrows;
		config.showEnderPearls = defaults.showEnderPearls;
		config.deathLineEnabled = defaults.deathLineEnabled;
		config.deathTriangleEnabled = defaults.deathTriangleEnabled;
		config.playerDotColor = defaults.playerDotColor;
		config.passiveColor = defaults.passiveColor;
		config.neutralColor = defaults.neutralColor;
		config.hostileColor = defaults.hostileColor;
		config.bossColor = defaults.bossColor;
		config.otherPlayerColor = defaults.otherPlayerColor;
		config.arrowColor = defaults.arrowColor;
		config.itemColor = defaults.itemColor;
		config.enderPearlColor = defaults.enderPearlColor;
		config.save();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
