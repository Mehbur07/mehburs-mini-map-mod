package com.mehbur.minimap.input;

import com.mehbur.minimap.MehbursMiniMapClient;
import com.mehbur.minimap.config.MinimapConfig;
import com.mehbur.minimap.entity.EntityRadar;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Frees the cursor and takes the keyboard while the big map is open, without pausing the game.
 * The map itself is drawn by the HUD, which is also what animates it open and closed around this screen.
 */
public final class BigMapScreen extends Screen {
	private static final float ZOOM_PER_SCROLL = 1.25f;
	private static final int BUTTON_MARGIN = 4;
	private static final int BUTTON_WIDTH = 98;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_SPACING = 24;

	private boolean settingsChanged;

	public BigMapScreen() {
		super(Component.translatable("screen.mehburs_minimap.big_map"));
	}

	@Override
	protected void init() {
		MinimapConfig config = MehbursMiniMapClient.config();
		addLineToggle("screen.mehburs_minimap.big_map.fps", config.showFps, BUTTON_MARGIN, value -> config.showFps = value);
		// Servers that hide debug info also hide this line, so the button would do nothing there.
		if (!minecraft.showOnlyReducedInfo()) {
			addLineToggle("screen.mehburs_minimap.big_map.coordinates", config.showCoordinates, BUTTON_MARGIN + BUTTON_SPACING, value -> config.showCoordinates = value);
		}
	}

	private void addLineToggle(String key, boolean value, int y, Consumer<Boolean> save) {
		addRenderableWidget(CycleButton.onOffBuilder(value).create(BUTTON_MARGIN, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable(key), (button, newValue) -> {
			save.accept(newValue);
			settingsChanged = true;
		}));
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
		if (MinimapKeys.TOGGLE_MAP.matches(event)) {
			onClose();
			return true;
		}
		if (minecraft.player != null && MinimapKeys.handleDeathKeys(event, minecraft.player)) {
			return true;
		}
		return super.keyPressed(event);
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
