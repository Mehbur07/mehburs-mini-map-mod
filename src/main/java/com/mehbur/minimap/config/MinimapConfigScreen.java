package com.mehbur.minimap.config;

import com.mehbur.minimap.MehbursMiniMapClient;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The settings screen. Only touch this class when Cloth Config is loaded. */
public final class MinimapConfigScreen {
	private static final String KEY = "config.mehburs_minimap.";
	private static final int PERCENT = 100;
	private static final int RGB_MASK = 0xFFFFFF;

	private final MinimapConfig config = MehbursMiniMapClient.config();
	private final MinimapConfig defaults = new MinimapConfig();
	private final ConfigBuilder builder;
	private final ConfigEntryBuilder entries;
	private ConfigCategory category;

	private MinimapConfigScreen(Screen parent) {
		builder = ConfigBuilder.create().setParentScreen(parent).setTitle(text("title")).setSavingRunnable(config::save);
		entries = builder.entryBuilder();
	}

	public static Screen create(Screen parent) {
		MinimapConfigScreen screen = new MinimapConfigScreen(parent);
		screen.addMapCategory();
		screen.addBigMapCategory();
		screen.addDotsCategory();
		screen.addColorsCategory();
		screen.addDeathCategory();
		return screen.builder.build();
	}

	private void addMapCategory() {
		category = builder.getOrCreateCategory(text("category.map"));
		category.addEntry(entries.startIntSlider(text("map_size"), config.mapSize, MinimapConfig.MIN_MAP_SIZE, MinimapConfig.MAX_MAP_SIZE)
			.setDefaultValue(defaults.mapSize)
			.setTooltip(text("map_size.tooltip"))
			.setSaveConsumer(value -> config.mapSize = value)
			.build());
		category.addEntry(entries.startEnumSelector(text("corner"), MinimapConfig.Corner.class, config.corner)
			.setDefaultValue(defaults.corner)
			.setEnumNameProvider(corner -> text("corner." + corner.name().toLowerCase(Locale.ROOT)))
			.setSaveConsumer(value -> config.corner = value)
			.build());
		zoomSlider("zoom", config.zoom, defaults.zoom, value -> config.zoom = value);
		toggle("north_locked", config.northLocked, defaults.northLocked, value -> config.northLocked = value);
		toggle("auto_cave_mode", config.autoCaveMode, defaults.autoCaveMode, value -> config.autoCaveMode = value);
		toggle("simplify_distant_textures", config.simplifyDistantTextures, defaults.simplifyDistantTextures, value -> config.simplifyDistantTextures = value);
		toggle("show_fps", config.showFps, defaults.showFps, value -> config.showFps = value);
		toggle("show_coordinates", config.showCoordinates, defaults.showCoordinates, value -> config.showCoordinates = value);
	}

	private void addBigMapCategory() {
		category = builder.getOrCreateCategory(text("category.big_map"));
		zoomSlider("big_map_zoom", config.bigMapZoom, defaults.bigMapZoom, value -> config.bigMapZoom = value);
		category.addEntry(entries.startIntSlider(text("animation_millis"), config.animationMillis, 0, MinimapConfig.MAX_ANIMATION_MILLIS)
			.setDefaultValue(defaults.animationMillis)
			.setTextGetter(value -> Component.translatable(KEY + "animation_millis.value", value))
			.setSaveConsumer(value -> config.animationMillis = value)
			.build());
	}

	private void addDotsCategory() {
		category = builder.getOrCreateCategory(text("category.dots"));
		toggle("show_mobs", config.showMobs, defaults.showMobs, value -> config.showMobs = value);
		toggle("show_players", config.showPlayers, defaults.showPlayers, value -> config.showPlayers = value);
		toggle("show_items", config.showItems, defaults.showItems, value -> config.showItems = value);
		toggle("show_arrows", config.showArrows, defaults.showArrows, value -> config.showArrows = value);
		toggle("show_ender_pearls", config.showEnderPearls, defaults.showEnderPearls, value -> config.showEnderPearls = value);
	}

	private void addColorsCategory() {
		category = builder.getOrCreateCategory(text("category.colors"));
		color("player_dot_color", config.playerDotColor, defaults.playerDotColor, value -> config.playerDotColor = value);
		color("passive_color", config.passiveColor, defaults.passiveColor, value -> config.passiveColor = value);
		color("neutral_color", config.neutralColor, defaults.neutralColor, value -> config.neutralColor = value);
		color("hostile_color", config.hostileColor, defaults.hostileColor, value -> config.hostileColor = value);
		color("boss_color", config.bossColor, defaults.bossColor, value -> config.bossColor = value);
		color("other_player_color", config.otherPlayerColor, defaults.otherPlayerColor, value -> config.otherPlayerColor = value);
		color("arrow_color", config.arrowColor, defaults.arrowColor, value -> config.arrowColor = value);
		color("item_color", config.itemColor, defaults.itemColor, value -> config.itemColor = value);
		color("ender_pearl_color", config.enderPearlColor, defaults.enderPearlColor, value -> config.enderPearlColor = value);
	}

	private void addDeathCategory() {
		category = builder.getOrCreateCategory(text("category.death"));
		toggle("death_line_enabled", config.deathLineEnabled, defaults.deathLineEnabled, value -> config.deathLineEnabled = value);
		toggle("death_triangle_enabled", config.deathTriangleEnabled, defaults.deathTriangleEnabled, value -> config.deathTriangleEnabled = value);
	}

	private void toggle(String key, boolean value, boolean defaultValue, Consumer<Boolean> save) {
		category.addEntry(entries.startBooleanToggle(text(key), value)
			.setDefaultValue(defaultValue)
			.setTooltip(text(key + ".tooltip"))
			.setSaveConsumer(save)
			.build());
	}

	private void color(String key, int value, int defaultValue, IntConsumer save) {
		category.addEntry(entries.startColorField(text(key), value)
			.setDefaultValue(defaultValue)
			.setSaveConsumer(saved -> save.accept(saved & RGB_MASK))
			.build());
	}

	/** Shown in percent, because a slider moves in whole numbers and zoom is stored as a factor. */
	private void zoomSlider(String key, float value, float defaultValue, Consumer<Float> save) {
		category.addEntry(entries.startIntSlider(text(key), Math.round(value * PERCENT), Math.round(MinimapConfig.MIN_ZOOM * PERCENT), Math.round(MinimapConfig.MAX_ZOOM * PERCENT))
			.setDefaultValue(Math.round(defaultValue * PERCENT))
			.setTextGetter(percent -> Component.translatable(KEY + "zoom.value", percent))
			.setTooltip(text(key + ".tooltip"))
			.setSaveConsumer(percent -> save.accept((float) percent / PERCENT))
			.build());
	}

	private static Component text(String key) {
		return Component.translatable(KEY + key);
	}
}
