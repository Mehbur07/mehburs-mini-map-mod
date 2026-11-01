package com.mehbur.minimap.entity;

import com.mehbur.minimap.config.MinimapConfig;

/** What a dot on the map stands for. Later constants are drawn on top of earlier ones. */
public enum EntityDotType {
	ITEM,
	PASSIVE,
	NEUTRAL,
	OTHER_PLAYER,
	HOSTILE,
	BOSS,
	ENDER_PEARL,
	/** Drawn as a bar along the flight direction instead of a dot. */
	ARROW;

	public int color(MinimapConfig config) {
		return switch (this) {
			case ITEM -> config.itemColor;
			case PASSIVE -> config.passiveColor;
			case NEUTRAL -> config.neutralColor;
			case OTHER_PLAYER -> config.otherPlayerColor;
			case HOSTILE -> config.hostileColor;
			case BOSS -> config.bossColor;
			case ENDER_PEARL -> config.enderPearlColor;
			case ARROW -> config.arrowColor;
		};
	}

	public boolean isEnabled(MinimapConfig config) {
		return switch (this) {
			case ITEM -> config.showItems;
			case OTHER_PLAYER -> config.showPlayers;
			case ENDER_PEARL -> config.showEnderPearls;
			case ARROW -> config.showArrows;
			case PASSIVE, NEUTRAL, HOSTILE, BOSS -> config.showMobs;
		};
	}
}
