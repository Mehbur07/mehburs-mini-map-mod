package com.mehbur.minimap.gametest;

import com.mehbur.minimap.input.BigMapScreen;
import com.mehbur.minimap.input.MinimapKeys;
import com.mehbur.minimap.render.MapLayout;
import com.mehbur.minimap.waypoint.Navigation;
import com.mehbur.minimap.waypoint.Waypoint;
import com.mehbur.minimap.waypoint.WaypointStore;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.TextColor;
import org.lwjgl.glfw.GLFW;

/** Saves locations and follows them with real clicks and key presses. Screenshots land in build/run/clientGameTest/screenshots. */
public class MapNavigationGameTest implements FabricClientGameTest {
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String WAYPOINT = "screen.mehburs_minimap.waypoint.";
	private static final int GROUND = -60;
	private static final int SETTLE_TICKS = 30;
	private static final int SHORT_TICKS = 5;
	private static final int SLOT = 2;
	/** Text closer to the button's edges than this looks clipped. */
	private static final int LABEL_PADDING = 8;
	private static final int NAV_KEY = GLFW.GLFW_KEY_Y;
	private static final int OTHER_KEY = GLFW.GLFW_KEY_H;
	private static final int DEBUG_ONLY_KEY = GLFW.GLFW_KEY_H;
	/** The small map sits in the top-left corner, 8 units from the screen's edges and 80 wide. */
	private static final int MINI_LEFT = 8;
	private static final int EDGE_TOLERANCE = 8;
	/** Closer than this the location is on the small map at the default zoom. */
	private static final int NEAR = 15;
	private static final int FAR = 200;

	@Override
	public void runTest(ClientGameTestContext context) {
		TestWorldSave save;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			save = world.getWorldSave();
			TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamerule spawn_mobs false");
			// The Nether part stands below the world's floor, which would otherwise kill the player.
			server.runCommand("gamemode creative @a");
			teleport(context, server, 0, 180);
			testSaving(context);
			testKeyAssignment(context);
			testFollowing(context, server);
			testArrival(context, server);
			testOtherDimension(context, server);
			testPanelWhileNavigating(context, server);
			testAllSlots(context, server);
		}
		try (TestSingleplayerContext reopened = save.open()) {
			context.waitTicks(SETTLE_TICKS);
			Waypoint kept = context.computeOnClient(client -> Navigation.INSTANCE.waypoint(0));
			check(kept != null && OVERWORLD.equals(kept.dimension()), "the world forgot its location: " + kept);
		}
		try (TestSingleplayerContext other = context.worldBuilder().create()) {
			context.waitTicks(SETTLE_TICKS);
			Waypoint leaked = context.computeOnClient(client -> Navigation.INSTANCE.waypoint(0));
			check(leaked == null, "another world's location showed up: " + leaked);
		}
		// A real server needs the EULA accepted, which only the development run may do on its own.
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			testDedicatedServer(context);
		}
		context.runOnClient(client -> {
			MinimapKeys.navigationKey(SLOT).setKey(InputConstants.UNKNOWN);
			KeyMapping.resetMapping();
			client.options.save();
		});
	}

	private static void testSaving(ClientGameTestContext context) {
		openBigMap(context);
		context.takeScreenshot("nav-panel-empty");
		checkPanelFits(context);
		check(context.computeOnClient(client -> widgetLabeled(client, WAYPOINT + "navigate_on").active == false), "an empty slot has nothing to follow");
		check(context.computeOnClient(client -> widgetLabeled(client, WAYPOINT + "delete").active == false), "an empty slot has nothing to delete");

		click(context, client -> slotButton(client, SLOT));
		click(context, client -> widgetLabeled(client, WAYPOINT + "save"));
		Waypoint saved = waypoint(context, SLOT);
		check(saved != null && OVERWORLD.equals(saved.dimension()) && saved.x() == 0 && saved.y() == GROUND && saved.z() == 0, "saved " + saved);
		context.takeScreenshot("nav-panel-saved");
		check(context.computeOnClient(client -> widgetLabeled(client, WAYPOINT + "delete").active), "a saved slot can be deleted");
	}

	private static void testKeyAssignment(ClientGameTestContext context) {
		check(context.computeOnClient(client -> MinimapKeys.navigationKey(SLOT).isUnbound()), "location keys must start unbound");

		click(context, MapNavigationGameTest::keyButton);
		context.takeScreenshot("nav-key-listening");
		// M closes the big map, except while the panel waits for a key.
		context.getInput().pressKey(GLFW.GLFW_KEY_M);
		context.waitTicks(SHORT_TICKS);
		check(isBigMapOpen(context), "the key being assigned must not close the big map");
		check(boundKey(context) == GLFW.GLFW_KEY_M, "the pressed key must be assigned, was " + boundKey(context));
		context.takeScreenshot("nav-key-conflict");

		click(context, MapNavigationGameTest::keyButton);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(SHORT_TICKS);
		check(isBigMapOpen(context), "Esc must not close the big map while a key is awaited");
		check(context.computeOnClient(client -> MinimapKeys.navigationKey(SLOT).isUnbound()), "Esc must remove the key");

		click(context, MapNavigationGameTest::keyButton);
		click(context, client -> slotButton(client, 0));
		context.getInput().pressKey(OTHER_KEY);
		context.waitTicks(SHORT_TICKS);
		check(context.computeOnClient(client -> MinimapKeys.navigationKey(0).isUnbound()), "a mouse click must cancel waiting for a key");
		click(context, client -> slotButton(client, SLOT));

		assignKey(context, GLFW.GLFW_KEY_G);
		check(keyButtonIsRed(context), "G is also the quick actions key, so it must show as taken");
		assignKey(context, DEBUG_ONLY_KEY);
		check(!keyButtonIsRed(context), "H only does something with F3 held, so it is free");
		assignKey(context, NAV_KEY);
		check(!keyButtonIsRed(context), "nothing else uses this key");
		context.takeScreenshot("nav-key-assigned");
		Path options = context.computeOnClient(client -> client.gameDirectory.toPath().resolve("options.txt"));
		String line = "key_key.mehburs_minimap.navigate_" + (SLOT + 1) + ":key.keyboard.y";
		try (Stream<String> lines = Files.lines(options)) {
			check(lines.anyMatch(line::equals), "the key must be saved in the options");
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
		closeBigMap(context);
	}

	private static void assignKey(ClientGameTestContext context, int key) {
		click(context, MapNavigationGameTest::keyButton);
		context.getInput().pressKey(key);
		context.waitTicks(SHORT_TICKS);
		check(boundKey(context) == key, "key " + key + " must be assigned, was " + boundKey(context));
	}

	private static boolean keyButtonIsRed(ClientGameTestContext context) {
		return context.computeOnClient(client -> TextColor.fromLegacyFormat(ChatFormatting.RED).equals(keyButton(client).getMessage().getStyle().getColor()));
	}

	private static void testFollowing(ClientGameTestContext context, TestServerContext server) {
		check(activeSlot(context) == WaypointStore.NO_SLOT, "saving must not start a navigation");
		teleport(context, server, FAR, 180);
		check(!drawn(context).line(), "without a navigation nothing must point anywhere");

		pressNavKey(context);
		check(activeSlot(context) == SLOT, "the key must start the navigation");
		context.waitTicks(SHORT_TICKS);
		context.takeScreenshot("nav-far-facing-north");
		Navigation.Drawn drawn = drawn(context);
		check(drawn.line() && !drawn.target(), "far away the line must leave the map, was " + drawn);
		check(Math.abs(drawn.x() - MINI_LEFT) <= EDGE_TOLERANCE, "the location is west, which is left when facing north, was " + drawn);

		context.getInput().pressKey(GLFW.GLFW_KEY_M);
		context.waitTicks(SETTLE_TICKS);
		context.takeScreenshot("nav-far-big-map");
		check(drawn(context).line(), "the big map must point there too");
		pressNavKey(context);
		check(activeSlot(context) == WaypointStore.NO_SLOT, "the key must also work on the big map");
		pressNavKey(context);
		check(activeSlot(context) == SLOT, "and start it again there");
		closeBigMap(context);

		teleport(context, server, NEAR, 180);
		drawn = drawn(context);
		check(drawn.line() && drawn.target(), "close by the location itself must show, was " + drawn);
		context.takeScreenshot("nav-near");

		pressNavKey(context);
		check(activeSlot(context) == WaypointStore.NO_SLOT, "the key must stop the navigation");
		context.waitTicks(SHORT_TICKS);
		check(!drawn(context).line(), "a stopped navigation must not draw");
		context.takeScreenshot("nav-stopped");
	}

	private static void testArrival(ClientGameTestContext context, TestServerContext server) {
		pressNavKey(context);
		check(activeSlot(context) == SLOT, "the key must start it again");
		teleport(context, server, 0, 180);
		check(activeSlot(context) == WaypointStore.NO_SLOT, "arriving must end the navigation");
		check(waypoint(context, SLOT) != null, "arriving must keep the location");
		context.takeScreenshot("nav-arrived");
	}

	private static void testOtherDimension(ClientGameTestContext context, TestServerContext server) {
		teleport(context, server, FAR, 180);
		pressNavKey(context);
		server.runCommand("execute in minecraft:the_nether run tp @a 0.5 70 0.5 180 0");
		context.waitTicks(SETTLE_TICKS);
		check(activeSlot(context) == SLOT, "another dimension must not end the navigation");
		check(!drawn(context).line(), "another dimension's location has no direction here");
		context.takeScreenshot("nav-other-dimension");
		// The same numbers in the Nether are not the location, so standing on them is not arriving.
		server.runCommand("execute in minecraft:the_nether run tp @a 0.5 " + GROUND + " 0.5 180 0");
		context.waitTicks(SHORT_TICKS);
		check(activeSlot(context) == SLOT, "the same coordinates in another dimension are not an arrival");
		server.runCommand("execute in minecraft:overworld run tp @a " + FAR + " " + GROUND + " 0 180 0");
		context.waitTicks(SETTLE_TICKS);
		check(drawn(context).line(), "back home the line must return");
	}

	/** The panel follows a navigation that ends while it is open. */
	private static void testPanelWhileNavigating(ClientGameTestContext context, TestServerContext server) {
		openBigMap(context);
		context.takeScreenshot("nav-panel-navigating");
		checkPanelFits(context);
		widgetLabeledOrFail(context, WAYPOINT + "navigate_off");
		server.runCommand("tp @a 0 " + GROUND + " 0 180 0");
		context.waitTicks(SETTLE_TICKS);
		widgetLabeledOrFail(context, WAYPOINT + "navigate_on");
		click(context, client -> widgetLabeled(client, WAYPOINT + "delete"));
		check(waypoint(context, SLOT) == null, "the delete button must empty the slot");
		click(context, client -> widgetLabeled(client, WAYPOINT + "save"));
		closeBigMap(context);
	}

	private static void testAllSlots(ClientGameTestContext context, TestServerContext server) {
		openBigMap(context);
		for (int slot = 0; slot < WaypointStore.SLOTS; slot++) {
			int x = slot * 10 - 12345;
			server.runCommand("tp @a " + x + " " + GROUND + " -12345 180 0");
			context.waitTicks(SHORT_TICKS);
			int selected = slot;
			click(context, client -> slotButton(client, selected));
			click(context, client -> saveButton(client, selected));
			Waypoint saved = waypoint(context, slot);
			check(saved != null && saved.x() == x, "slot " + slot + " saved " + saved);
		}
		context.takeScreenshot("nav-panel-full");
		checkPanelFits(context);
		boolean noEighth = context.computeOnClient(client -> client.gui.screen().children().stream().noneMatch(child -> child instanceof AbstractWidget widget && widget.getMessage().getString().contains("8:")));
		check(noEighth, "there must be seven slots");
		closeBigMap(context);
		context.runOnClient(client -> client.gui.setScreen(null));
	}

	/** On a real server the locations live in the config folder, and coordinates must stay hidden when the server hides them. */
	private static void testDedicatedServer(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			server.runCommand("gamerule spawn_mobs false");
			try (var connection = server.connect()) {
				context.waitTicks(SETTLE_TICKS);
				check(waypoint(context, 0) == null, "a server starts without locations");
				openBigMap(context);
				click(context, client -> widgetLabeled(client, WAYPOINT + "save"));
				check(waypoint(context, 0) != null, "saving must work on a server");
				boolean written;
				Path folder = FabricLoader.getInstance().getConfigDir().resolve("mehburs_minimap").resolve("waypoints");
				try (Stream<Path> files = Files.list(folder)) {
					written = files.findAny().isPresent();
				} catch (java.io.IOException e) {
					throw new AssertionError(e);
				}
				check(written, "the server's locations must be written under the config folder");
				closeBigMap(context);

				server.runCommand("gamerule reduced_debug_info true");
				context.waitTicks(SETTLE_TICKS);
				openBigMap(context);
				context.takeScreenshot("nav-server-debug-hidden");
				String saved = Language.getInstance().getOrDefault(WAYPOINT + "saved");
				String label = context.computeOnClient(client -> slotButton(client, 0).getMessage().getString());
				check(label.contains(saved), "the panel showed " + label + " with coordinates hidden");
				click(context, client -> slotButton(client, 0));
				click(context, client -> widgetLabeled(client, WAYPOINT + "delete"));
				check(waypoint(context, 0) == null, "deleting must work on a server");
				closeBigMap(context);
			}
		}
	}

	private static void checkPanelFits(ClientGameTestContext context) {
		context.runOnClient(client -> {
			int mapRight = client.getWindow().getGuiScaledWidth() - MapLayout.big(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), 0).x();
			int bottom = client.getWindow().getGuiScaledHeight();
			for (GuiEventListener child : client.gui.screen().children()) {
				if (child instanceof AbstractWidget widget) {
					check(widget.getY() + widget.getHeight() <= bottom, widget.getMessage().getString() + " leaves the screen");
					check(client.font.width(widget.getMessage()) + LABEL_PADDING <= widget.getWidth(), widget.getMessage().getString() + " does not fit its button");
					if (widget.getX() > client.getWindow().getGuiScaledWidth() / 2) {
						check(widget.getX() > mapRight, widget.getMessage().getString() + " covers the map");
					}
				}
			}
		});
	}

	private static AbstractWidget slotButton(Minecraft client, int slot) {
		String start = (slot + 1) + ": ";
		for (GuiEventListener child : client.gui.screen().children()) {
			if (child instanceof AbstractWidget widget) {
				String label = widget.getMessage().getString();
				if (label.startsWith(start) || label.startsWith("> " + start)) {
					return widget;
				}
			}
		}
		throw new AssertionError("No button for slot " + slot);
	}

	private static AbstractWidget saveButton(Minecraft client, int slot) {
		return widgetLabeled(client, WAYPOINT + (Navigation.INSTANCE.waypoint(slot) == null ? "save" : "overwrite"));
	}

	private static AbstractWidget keyButton(Minecraft client) {
		String key = Language.getInstance().getOrDefault(WAYPOINT + "key");
		String start = key.substring(0, key.indexOf('%'));
		for (GuiEventListener child : client.gui.screen().children()) {
			if (child instanceof AbstractWidget widget && widget.getMessage().getString().startsWith(start)) {
				return widget;
			}
		}
		throw new AssertionError("No key button");
	}

	private static AbstractWidget widgetLabeled(Minecraft client, String key) {
		String label = Language.getInstance().getOrDefault(key);
		for (GuiEventListener child : client.gui.screen().children()) {
			if (child instanceof AbstractWidget widget && label.equals(widget.getMessage().getString())) {
				return widget;
			}
		}
		throw new AssertionError("No button reads " + label);
	}

	private static void widgetLabeledOrFail(ClientGameTestContext context, String key) {
		context.runOnClient(client -> widgetLabeled(client, key));
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
		context.waitTicks(SHORT_TICKS);
	}

	private static void openBigMap(ClientGameTestContext context) {
		context.getInput().pressKey(GLFW.GLFW_KEY_M);
		context.waitTicks(SETTLE_TICKS);
		check(isBigMapOpen(context), "M must open the big map");
	}

	private static void closeBigMap(ClientGameTestContext context) {
		context.getInput().pressKey(GLFW.GLFW_KEY_M);
		context.waitTicks(SETTLE_TICKS);
		check(!isBigMapOpen(context), "M must close the big map");
	}

	private static boolean isBigMapOpen(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.gui.screen() instanceof BigMapScreen);
	}

	private static void pressNavKey(ClientGameTestContext context) {
		context.getInput().pressKey(NAV_KEY);
		context.waitTicks(SHORT_TICKS);
	}

	private static void teleport(ClientGameTestContext context, TestServerContext server, int x, int yaw) {
		server.runCommand("tp @a " + x + " " + GROUND + " 0 " + yaw + " 0");
		context.waitTicks(SETTLE_TICKS);
	}

	private static Waypoint waypoint(ClientGameTestContext context, int slot) {
		return context.computeOnClient(client -> Navigation.INSTANCE.waypoint(slot));
	}

	private static int activeSlot(ClientGameTestContext context) {
		return context.computeOnClient(client -> Navigation.INSTANCE.activeSlot());
	}

	private static Navigation.Drawn drawn(ClientGameTestContext context) {
		return context.computeOnClient(client -> Navigation.INSTANCE.drawn());
	}

	private static int boundKey(ClientGameTestContext context) {
		return context.computeOnClient(client -> InputConstants.getKey(MinimapKeys.navigationKey(SLOT).saveString()).getValue());
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
