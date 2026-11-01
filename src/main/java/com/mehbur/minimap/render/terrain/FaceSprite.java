package com.mehbur.minimap.render.terrain;

/** A sprite's rectangle in the block atlas. A negative {@code tintIndex} means the face has no biome tint. */
public record FaceSprite(float u0, float v0, float u1, float v1, int tintIndex) {
}
