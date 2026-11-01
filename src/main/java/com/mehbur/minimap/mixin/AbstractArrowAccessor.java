package com.mehbur.minimap.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The server tells clients when an arrow sticks in a block, but the game keeps that state protected. */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
	@Invoker("isInGround")
	boolean mehburs_minimap$isInGround();
}
