package com.mehbur.minimap.mixin;

import com.mehbur.minimap.render.terrain.TerrainCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
	@Inject(method = "sendBlockUpdated", at = @At("TAIL"))
	private void mehburs_minimap$onBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo info) {
		TerrainCache.onBlockChanged((ClientLevel) (Object) this, pos);
	}
}
