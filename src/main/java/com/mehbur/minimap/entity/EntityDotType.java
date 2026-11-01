package com.mehbur.minimap.entity;

import com.mehbur.minimap.config.MinimapConfig;

/** What a dot on the map stands for. Later constants are drawn on top of earlier ones. */
public enum EntityDotType {
	PASSIVE,
	NEUTRAL,
	OTHER_PLAYER,
	HOSTILE,
	BOSS;

	public int color(MinimapConfig config) {
		return switch (this) {
			case PASSIVE -> config.passiveColor;
			case NEUTRAL -> config.neutralColor;
			case OTHER_PLAYER -> config.otherPlayerColor;
			case HOSTILE -> config.hostileColor;
			case BOSS -> config.bossColor;
		};
	}
}
