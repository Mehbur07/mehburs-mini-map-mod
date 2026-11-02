package com.mehbur.minimap.input;

import com.mehbur.minimap.MehbursMiniMapClient;
import com.mehbur.minimap.config.MinimapConfig;
import com.mehbur.minimap.entity.EntityRadar;
import com.mehbur.minimap.waypoint.Navigation;
import com.mehbur.minimap.waypoint.Waypoint;
import com.mehbur.minimap.waypoint.WaypointStore;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Frees the cursor and takes the keyboard while the big map is open, without pausing the game.
 * The map itself is drawn by the HUD, which is also what animates it open and closed around this screen.
 */
public final class BigMapScreen extends Screen {
	private static final String WAYPOINT_KEY = "screen.mehburs_minimap.waypoint.";
	private static final float ZOOM_PER_SCROLL = 1.25f;
	private static final int BUTTON_MARGIN = 4;
	private static final int BUTTON_WIDTH = 98;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_SPACING = 24;
	/** Smaller than the buttons on the left, because all the slots and their actions must fit the shortest window. */
	private static final int PANEL_BUTTON_HEIGHT = 16;
	private static final int PANEL_SPACING = 18;
	private static final int PANEL_GROUP_GAP = 6;

	private boolean settingsChanged;
	private int selectedSlot;
	private boolean listeningForKey;
	private int shownActiveSlot;

	public BigMapScreen() {
		super(Component.translatable("screen.mehburs_minimap.big_map"));
		selectedSlot = firstSlotToShow();
	}

	/** Where the player is heading, or else where the next save would naturally go. */
	private static int firstSlotToShow() {
		int active = Navigation.INSTANCE.activeSlot();
		if (active != WaypointStore.NO_SLOT) {
			return active;
		}
		for (int slot = 0; slot < WaypointStore.SLOTS; slot++) {
			if (Navigation.INSTANCE.waypoint(slot) == null) {
				return slot;
			}
		}
		return 0;
	}

	@Override
	protected void init() {
		MinimapConfig config = MehbursMiniMapClient.config();
		addLineToggle("screen.mehburs_minimap.big_map.fps", config.showFps, BUTTON_MARGIN, value -> config.showFps = value);
		// Servers that hide debug info also hide this line, so the button would do nothing there.
		if (!minecraft.showOnlyReducedInfo()) {
			addLineToggle("screen.mehburs_minimap.big_map.coordinates", config.showCoordinates, BUTTON_MARGIN + BUTTON_SPACING, value -> config.showCoordinates = value);
		}
		addWaypointPanel();
	}

	private void addLineToggle(String key, boolean value, int y, Consumer<Boolean> save) {
		addRenderableWidget(CycleButton.onOffBuilder(value).create(BUTTON_MARGIN, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable(key), (button, newValue) -> {
			save.accept(newValue);
			settingsChanged = true;
		}));
	}

	private void addWaypointPanel() {
		shownActiveSlot = Navigation.INSTANCE.activeSlot();
		int y = BUTTON_MARGIN;
		for (int slot = 0; slot < WaypointStore.SLOTS; slot++) {
			addSlotButton(slot, y);
			y += PANEL_SPACING;
		}
		y += PANEL_GROUP_GAP;

		Waypoint selected = Navigation.INSTANCE.waypoint(selectedSlot);
		boolean navigating = shownActiveSlot == selectedSlot;
		addPanelButton(Component.translatable(WAYPOINT_KEY + (selected == null ? "save" : "overwrite")), y, true, button -> {
			Navigation.INSTANCE.save(selectedSlot, minecraft);
			rebuildWidgets();
		});
		y += PANEL_SPACING;
		addPanelButton(Component.translatable(WAYPOINT_KEY + (navigating ? "navigate_off" : "navigate_on")), y, selected != null, button -> {
			Navigation.INSTANCE.toggle(selectedSlot, minecraft.player);
			rebuildWidgets();
		});
		y += PANEL_SPACING;
		addKeyButton(y);
		y += PANEL_SPACING;
		addPanelButton(Component.translatable(WAYPOINT_KEY + "delete"), y, selected != null, button -> {
			Navigation.INSTANCE.delete(selectedSlot, minecraft.player);
			rebuildWidgets();
		});
	}

	private void addSlotButton(int slot, int y) {
		Waypoint waypoint = Navigation.INSTANCE.waypoint(slot);
		// Servers that hide debug info hide coordinates, so the labels must not bring them back.
		boolean hideCoordinates = minecraft.showOnlyReducedInfo();
		Component content = waypoint == null ? Component.translatable(WAYPOINT_KEY + "empty")
			: hideCoordinates ? Component.translatable(WAYPOINT_KEY + "saved")
			: Component.literal(waypoint.x() + ", " + waypoint.z());
		String prefix = slot == shownActiveSlot ? "> " : "";
		Component label = Component.literal(prefix).append(Component.translatable(WAYPOINT_KEY + "slot", slot + 1, content));
		if (slot == selectedSlot) {
			label = label.copy().withStyle(ChatFormatting.YELLOW);
		}
		Button button = addPanelButton(label, y, true, pressed -> {
			selectedSlot = slot;
			rebuildWidgets();
		});
		if (waypoint != null) {
			String position = hideCoordinates ? "" : waypoint.x() + ", " + waypoint.y() + ", " + waypoint.z() + "\n";
			button.setTooltip(Tooltip.create(Component.literal(position + waypoint.dimension())));
		}
	}

	private void addKeyButton(int y) {
		KeyMapping key = MinimapKeys.navigationKey(selectedSlot);
		Component label = listeningForKey ? Component.translatable(WAYPOINT_KEY + "key_listening") : Component.translatable(WAYPOINT_KEY + "key", key.getTranslatedKeyMessage());
		boolean conflict = !listeningForKey && hasConflict(key);
		Button button = addPanelButton(conflict ? label.copy().withStyle(ChatFormatting.RED) : label, y, true, pressed -> {
			listeningForKey = true;
			rebuildWidgets();
		});
		if (listeningForKey) {
			button.setTooltip(Tooltip.create(Component.translatable(WAYPOINT_KEY + "key_listening.tooltip")));
		} else if (conflict) {
			button.setTooltip(Tooltip.create(Component.translatable(WAYPOINT_KEY + "key_conflict.tooltip")));
		}
	}

	private boolean hasConflict(KeyMapping key) {
		if (key.isUnbound()) {
			return false;
		}
		for (KeyMapping other : minecraft.options.keyMappings) {
			// Debug keys only act while F3 is held, so they never compete with a plain press.
			if (other != key && other.getCategory() != KeyMapping.Category.DEBUG && key.same(other)) {
				return true;
			}
		}
		return false;
	}

	private Button addPanelButton(Component label, int y, boolean active, Button.OnPress onPress) {
		Button button = addRenderableWidget(Button.builder(label, onPress)
			.bounds(width - BUTTON_MARGIN - BUTTON_WIDTH, y, BUTTON_WIDTH, PANEL_BUTTON_HEIGHT)
			.build());
		button.active = active;
		return button;
	}

	private void finishListening(InputConstants.Key key) {
		listeningForKey = false;
		if (key != null) {
			MinimapKeys.navigationKey(selectedSlot).setKey(key);
			KeyMapping.resetMapping();
			minecraft.options.save();
		}
		rebuildWidgets();
	}

	@Override
	public void tick() {
		// Arriving ends a navigation without anyone touching this screen, and its buttons must not keep offering to stop it.
		if (Navigation.INSTANCE.activeSlot() != shownActiveSlot) {
			rebuildWidgets();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// The world and the map must stay visible, so no blur or dimming.
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		Component name = EntityRadar.INSTANCE.nameAt(mouseX, mouseY);
		if (name != null) {
			graphics.setTooltipForNextFrame(font, name, mouseX, mouseY);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (listeningForKey) {
			// Esc takes the key away, as it does in the Controls screen.
			finishListening(event.key() == InputConstants.KEY_ESCAPE ? InputConstants.UNKNOWN : InputConstants.getKey(event));
			return true;
		}
		if (MinimapKeys.TOGGLE_MAP.matches(event)) {
			onClose();
			return true;
		}
		if (minecraft.player != null && (MinimapKeys.handleDeathKeys(event, minecraft.player) || MinimapKeys.handleNavigationKeys(event, minecraft.player))) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (listeningForKey) {
			finishListening(null);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY == 0.0) {
			return false;
		}
		MinimapConfig config = MehbursMiniMapClient.config();
		// Touchpads send many small scrolls, so the step follows the amount instead of the number of events.
		float factor = (float) Math.pow(ZOOM_PER_SCROLL, scrollY);
		config.bigMapZoom = Math.clamp(config.bigMapZoom * factor, MinimapConfig.MIN_ZOOM, MinimapConfig.MAX_ZOOM);
		settingsChanged = true;
		return true;
	}

	@Override
	public void removed() {
		if (settingsChanged) {
			MehbursMiniMapClient.config().save();
		}
	}
}
