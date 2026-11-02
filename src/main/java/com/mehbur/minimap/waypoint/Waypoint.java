package com.mehbur.minimap.waypoint;

/** A saved block position. {@code dimension} is the level's identifier, such as {@code minecraft:the_nether}. */
public record Waypoint(String dimension, int x, int y, int z) {
}
