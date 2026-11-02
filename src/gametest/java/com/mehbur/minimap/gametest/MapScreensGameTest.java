package com.mehbur.minimap.gametest;

import com.mehbur.minimap.MehbursMiniMapClient;
import com.mehbur.minimap.config.MinimapConfig;
import com.mehbur.minimap.render.MapLayout;
import com.mojang.blaze3d.platform.NativeImage;
import com.terraformersmc.modmenu.ModMenu;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import me.shedaniel.clothconfig2.api.AbstractConfigEntry;
import me.shedaniel.clothconfig2.gui.AbstractConfigScreen;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.gui.ClothConfigTabButton;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import me.shedaniel.clothconfig2.gui.entries.SelectionListEntry;
import me.shedaniel.clothconfig2.gui.entries.TextFieldListEntry;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.lwjgl.glfw.GLFW;

/** Uses the settings as a player does, with real clicks and typing, in Turkish. Screenshots land in build/run/clientGameTest/screenshots. */
public class MapScreensGameTest implements FabricClientGameTest {
	private static final String MOD_ID = "mehburs_minimap";
	private static final String KEY = "config.mehburs_minimap.";
	private static final String TURKISH = "tr_tr";
	private static final int SETTLE_TICKS = 10;
	private static final int TAB_COUNT = 5;
	private static final int COLORS_TAB = 3;
	private static final int NEW_COLOR = 0xFF00FF;
	private static final int COLOR_TEXT_LENGTH = 7;
	private static final int SLIDER_TOLERANCE = 40;
	private static final String FPS_BUTTON = "screen.mehburs_minimap.big_map.fps";
	private static final String COORDINATES_BUTTON = "screen.mehburs_minimap.big_map.coordinates";
	private static final int BIG_MAP_SETTLE_TICKS = 20;
	private static final int LABEL_PADDING = 4;
	private static final int HOTBAR_HALF_WIDTH = 91;
	private static final int STRIP_TOP = 11;
	private static final int STRIP_BOTTOM = 2;
	private static final int COLOR_TOLERANCE = 8;
	private static final double MOSTLY_DIFFERENT = 0.5;
	private static final double NEARLY_SAME = 0.02;
	private static final int WIDTH = 854;
	private static final int HEIGHT = 480;
	private static final int NARROW_WIDTH = 640;
	private static final int NARROW_HEIGHT = 480;

	@Override
	public void runTest(ClientGameTestContext context) {
		String language = context.computeOnClient(client -> client.options.languageCode);
		setLanguage(context, TURKISH);
		String mapTab = context.computeOnClient(client -> Language.getInstance().getOrDefault(KEY + "category.map"));
		check("Harita".equals(mapTab), "the game must speak Turkish here, the map tab reads " + mapTab);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamerule spawn_mobs false");
			context.waitTicks(SETTLE_TICKS);
			testControls(context);
			testEveryTab(context);
			testMouseChanges(context);
			testBigMap(context);
		}
		setLanguage(context, language);
	}

	/** The inventory bar must leave with the big map open, and the two buttons there must work with real clicks. */
	private static void testBigMap(ClientGameTestContext context) {
		Path withBar = context.takeScreenshot("screens-hud-with-bar");
		context.runOnClient(client -> client.gui.hud.toggle());
		context.waitTicks(SETTLE_TICKS);
		Path noBar = context.takeScreenshot("screens-hud-without-bar");
		context.runOnClient(client -> client.gui.hud.toggle());
		int[] strip = context.computeOnClient(MapScreensGameTest::hotbarStrip);
		check(differingShare(withBar, noBar, strip) > MOSTLY_DIFFERENT, "the test cannot see the inventory bar");

		context.getInput().pressKey(GLFW.GLFW_KEY_M);
		context.waitTicks(BIG_MAP_SETTLE_TICKS);
		Path open = context.takeScreenshot("screens-big-map");
		check(differingShare(open, noBar, strip) < NEARLY_SAME, "the inventory bar still shows under the big map");

		checkLineButtons(context);
		click(context, client -> lineButton(client, FPS_BUTTON));
		context.waitTicks(SETTLE_TICKS);
		check(context.computeOnClient(client -> !MehbursMiniMapClient.config().showFps && MehbursMiniMapClient.config().showCoordinates), "the FPS button must turn off only the FPS line");
		context.takeScreenshot("screens-big-map-no-fps");
		click(context, client -> lineButton(client, COORDINATES_BUTTON));
		context.waitTicks(SETTLE_TICKS);
		check(context.computeOnClient(client -> !MehbursMiniMapClient.config().showCoordinates), "the coordinates button must turn off its line");
		context.takeScreenshot("screens-big-map-no-lines");
		checkLineButtons(context);

		context.getInput().pressKey(GLFW.GLFW_KEY_M);
		context.waitTicks(BIG_MAP_SETTLE_TICKS);
		MinimapConfig saved = MinimapConfig.load(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json"));
		check(!saved.showFps && !saved.showCoordinates, "closing the big map must save its buttons");
		Path closed = context.takeScreenshot("screens-big-map-closed");
		check(differingShare(closed, noBar, strip) > MOSTLY_DIFFERENT, "the inventory bar must come back with the big map closed");

		context.getInput().resizeWindow(NARROW_WIDTH, NARROW_HEIGHT);
		context.waitTicks(SETTLE_TICKS);
		context.getInput().pressKey(GLFW.GLFW_KEY_M);
		context.waitTicks(BIG_MAP_SETTLE_TICKS);
		context.takeScreenshot("screens-big-map-narrow");
		context.getInput().pressKey(GLFW.GLFW_KEY_M);
		context.getInput().resizeWindow(WIDTH, HEIGHT);
		context.waitTicks(SETTLE_TICKS);

		context.runOnClient(client -> client.options.reducedDebugInfo().set(true));
		context.getInput().pressKey(GLFW.GLFW_KEY_M);
		context.waitTicks(SETTLE_TICKS);
		context.runOnClient(client -> check(client.gui.screen().children().size() == 1, "only the FPS button makes sense with coordinates hidden"));
		context.getInput().pressKey(GLFW.GLFW_KEY_M);
		context.waitTicks(SETTLE_TICKS);
		context.runOnClient(client -> {
			client.options.reducedDebugInfo().set(false);
			MapConfigGameTest.restoreDefaults();
		});
	}

	/** The map is at its widest when both lines are off, and "KAPALI" is the longer label. */
	private static void checkLineButtons(ClientGameTestContext context) {
		context.runOnClient(client -> {
			int mapLeft = MapLayout.big(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), 0).x();
			for (String key : List.of(FPS_BUTTON, COORDINATES_BUTTON)) {
				AbstractWidget button = lineButton(client, key);
				check(button.getX() + button.getWidth() < mapLeft, key + " covers the map");
				check(client.font.width(button.getMessage()) + LABEL_PADDING <= button.getWidth(), button.getMessage().getString() + " does not fit its button");
			}
		});
	}

	/** {x from, y from, x to, y to} in pixels: the part of the bar that no map covers, and left of where the hand is drawn. */
	private static int[] hotbarStrip(Minecraft client) {
		int scale = client.getWindow().getGuiScale();
		int centerX = client.getWindow().getGuiScaledWidth() / 2;
		int bottom = client.getWindow().getGuiScaledHeight();
		return new int[] {(centerX - HOTBAR_HALF_WIDTH) * scale, (bottom - STRIP_TOP) * scale, (centerX - 1) * scale, (bottom - STRIP_BOTTOM) * scale};
	}

	private static double differingShare(Path first, Path second, int[] strip) {
		try (InputStream firstStream = Files.newInputStream(first);
			InputStream secondStream = Files.newInputStream(second);
			NativeImage firstImage = NativeImage.read(firstStream);
			NativeImage secondImage = NativeImage.read(secondStream)) {
			int differing = 0;
			int total = 0;
			for (int y = strip[1]; y < strip[3]; y++) {
				for (int x = strip[0]; x < strip[2]; x++) {
					total++;
					if (!sameColor(firstImage.getPixel(x, y), secondImage.getPixel(x, y))) {
						differing++;
					}
				}
			}
			return (double) differing / total;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** The sun moves between screenshots, so a small change in brightness does not count. */
	private static boolean sameColor(int first, int second) {
		for (int shift = 0; shift < Integer.SIZE; shift += Byte.SIZE) {
			if (Math.abs((first >>> shift & 0xFF) - (second >>> shift & 0xFF)) > COLOR_TOLERANCE) {
				return false;
			}
		}
		return true;
	}

	private static AbstractWidget lineButton(Minecraft client, String key) {
		String label = Language.getInstance().getOrDefault(key);
		for (GuiEventListener child : client.gui.screen().children()) {
			if (child instanceof AbstractWidget widget && widget.getMessage().getString().startsWith(label)) {
				return widget;
			}
		}
		throw new AssertionError("No button starts with " + label);
	}

	private static void setLanguage(ClientGameTestContext context, String language) {
		context.runOnClient(client -> {
			client.options.languageCode = language;
			client.getLanguageManager().setSelected(language);
			client.reloadResourcePacks();
		});
		context.waitTicks(SETTLE_TICKS);
		context.waitFor(client -> client.gui.overlay() == null);
	}

	/** The mod's keys sit at the end of the list, and K is meant to show as a conflict. */
	private static void testControls(ClientGameTestContext context) {
		context.runOnClient(client -> client.gui.setScreen(new KeyBindsScreen(null, client.options)));
		context.waitTicks(SETTLE_TICKS);
		context.runOnClient(client -> {
			KeyBindsList list = MapConfigGameTest.field(KeyBindsScreen.class, client.gui.screen(), "keyBindsList");
			list.setScrollAmount(list.maxScrollAmount());
		});
		context.waitTicks(SETTLE_TICKS);
		context.takeScreenshot("screens-controls");
		context.runOnClient(client -> client.gui.setScreen(null));
		context.waitTicks(SETTLE_TICKS);
	}

	private static void testEveryTab(ClientGameTestContext context) {
		openSettings(context);
		for (int tab = 0; tab < TAB_COUNT; tab++) {
			clickTab(context, tab);
			context.takeScreenshot("screens-tab-" + tab);
		}
		context.runOnClient(client -> client.gui.setScreen(null));
		context.waitTicks(SETTLE_TICKS);
	}

	private static void testMouseChanges(ClientGameTestContext context) {
		MinimapConfig before = context.computeOnClient(client -> copyOf(MehbursMiniMapClient.config()));
		MinimapConfig.Corner nextCorner = MinimapConfig.Corner.values()[(before.corner.ordinal() + 1) % MinimapConfig.Corner.values().length];
		openSettings(context);

		click(context, client -> MapConfigGameTest.field(BooleanListEntry.class, entry(client, "north_locked"), "buttonWidget"));
		click(context, client -> MapConfigGameTest.field(SelectionListEntry.class, entry(client, "corner"), "buttonWidget"));
		click(context, client -> MapConfigGameTest.field(IntegerSliderEntry.class, entry(client, "map_size"), "sliderWidget"));
		context.takeScreenshot("screens-map-edited");
		boolean unsaved = context.computeOnClient(client -> MehbursMiniMapClient.config().northLocked == before.northLocked);
		check(unsaved, "a click must not change the map before saving");

		clickTab(context, COLORS_TAB);
		click(context, client -> MapConfigGameTest.field(TextFieldListEntry.class, entry(client, "player_dot_color"), "textFieldWidget"));
		context.getInput().pressKey(GLFW.GLFW_KEY_END);
		for (int i = 0; i < COLOR_TEXT_LENGTH; i++) {
			context.getInput().pressKey(GLFW.GLFW_KEY_BACKSPACE);
		}
		context.getInput().typeChars(String.format("#%06X", NEW_COLOR));
		context.waitTicks(SETTLE_TICKS);
		context.takeScreenshot("screens-color-typed");

		click(context, MapScreensGameTest::saveButton);
		context.waitTicks(SETTLE_TICKS);
		check(context.computeOnClient(client -> client.gui.screen() == null), "saving must close the settings");
		context.takeScreenshot("screens-after-save");
		context.runOnClient(client -> {
			MinimapConfig config = MehbursMiniMapClient.config();
			check(config.northLocked != before.northLocked, "the click on north lock was lost");
			check(config.corner == nextCorner, "the click on the corner gave " + config.corner);
			int middle = (MinimapConfig.MIN_MAP_SIZE + MinimapConfig.MAX_MAP_SIZE) / 2;
			check(Math.abs(config.mapSize - middle) <= SLIDER_TOLERANCE, "a click in the slider's middle gave " + config.mapSize);
			check(config.playerDotColor == NEW_COLOR, "typing the color gave " + Integer.toHexString(config.playerDotColor));
			MapConfigGameTest.restoreDefaults();
		});
	}

	private static MinimapConfig copyOf(MinimapConfig config) {
		MinimapConfig copy = new MinimapConfig();
		copy.northLocked = config.northLocked;
		copy.corner = config.corner;
		return copy;
	}

	private static void openSettings(ClientGameTestContext context) {
		context.runOnClient(client -> client.gui.setScreen(ModMenu.getConfigScreen(MOD_ID, null)));
		context.waitTicks(SETTLE_TICKS);
	}

	private static void clickTab(ClientGameTestContext context, int tab) {
		click(context, client -> {
			List<ClothConfigTabButton> tabs = MapConfigGameTest.field(ClothConfigScreen.class, client.gui.screen(), "tabButtons");
			return tabs.get(tab);
		});
		context.waitTicks(SETTLE_TICKS);
		int selected = context.computeOnClient(client -> ((AbstractConfigScreen) client.gui.screen()).selectedCategoryIndex);
		check(selected == tab, "clicking tab " + tab + " selected " + selected);
	}

	private static AbstractConfigEntry<?> entry(Minecraft client, String key) {
		AbstractConfigScreen screen = (AbstractConfigScreen) client.gui.screen();
		for (List<AbstractConfigEntry<?>> category : screen.getCategorizedEntries().values()) {
			for (AbstractConfigEntry<?> entry : category) {
				if (entry.getFieldName().getContents() instanceof TranslatableContents contents && contents.getKey().equals(KEY + key)) {
					return entry;
				}
			}
		}
		throw new AssertionError("No setting named " + key);
	}

	private static AbstractWidget saveButton(Minecraft client) {
		String label = Language.getInstance().getOrDefault("text.cloth-config.save_and_done");
		for (GuiEventListener child : client.gui.screen().children()) {
			if (child instanceof AbstractWidget widget && label.equals(widget.getMessage().getString())) {
				return widget;
			}
		}
		throw new AssertionError("No button reads " + label);
	}

	private static void click(ClientGameTestContext context, Function<Minecraft, AbstractWidget> finder) {
		double[] position = context.computeOnClient(client -> {
			AbstractWidget widget = finder.apply(client);
			int scale = client.getWindow().getGuiScale();
			return new double[] {(widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale};
		});
		context.getInput().setCursorPos(position[0], position[1]);
		context.waitTicks(1);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
