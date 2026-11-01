package com.mehbur.minimap.death;

/** Where the player died. {@code dimension} is the level's identifier, such as {@code minecraft:the_nether}. */
public record DeathPoint(String dimension, int x, int y, int z) {
}
