package com.mehbur.minimap.input;

import com.mehbur.minimap.MehbursMiniMapClient;
import com.mehbur.minimap.config.MinimapConfig;
import com.mehbur.minimap.entity.EntityRadar;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Frees the cursor and takes the keyboard while the big map is open, without pausing the game.
 * The map itself is drawn by the HUD, which is also what animates it open and closed around this screen.
 */
public final class BigMapScreen extends Screen {
	private static final float ZOOM_PER_SCROLL = 1.25f;

	private boolean zoomChanged;

	public BigMapScreen() {
		super(Component.translatable("screen.mehburs_minimap.big_map"));
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
		zoomChanged = true;
		return true;
	}

	@Override
	public void removed() {
		if (zoomChanged) {
			MehbursMiniMapClient.config().save();
		}
	}
}
