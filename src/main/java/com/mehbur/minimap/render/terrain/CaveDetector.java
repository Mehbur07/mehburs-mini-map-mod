package com.mehbur.minimap.render.terrain;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;

/** Tells whether the surface would be useless on the map, from what the client knows around the player. */
public final class CaveDetector {
	/** Fewer solid blocks overhead are a building or an overhang, where the surface is still the useful view. */
	private static final int MIN_ROOF_BLOCKS = 3;
	/** This much sky light means the open air is only a few blocks away, like in a cave mouth. */
	private static final int OPEN_AIR_SKY_LIGHT = 12;

	private CaveDetector() {
	}

	public static boolean isUnderground(ClientLevel level, Player player) {
		int eyeY = eyeBlockY(player);
		DimensionType dimension = level.dimensionType();
		if (dimension.hasCeiling()) {
			// Above the Nether roof the map can show the roof like any other surface.
			return eyeY < level.getMinY() + dimension.logicalHeight();
		}
		BlockPos eye = BlockPos.containing(player.getX(), eyeY, player.getZ());
		if (level.getBrightness(LightLayer.SKY, eye) >= OPEN_AIR_SKY_LIGHT) {
			return false;
		}
		return hasRoof(level, eye);
	}

	public static int eyeBlockY(Player player) {
		return Mth.floor(player.getEyeY());
	}

	/** Solid blocks count but water does not, so swimming down in an ocean keeps showing the ocean. */
	static boolean isRock(BlockState state) {
		return Heightmap.Types.MOTION_BLOCKING_NO_LEAVES.isOpaque().test(state) && state.getFluidState().isEmpty();
	}

	private static boolean hasRoof(ClientLevel level, BlockPos eye) {
		// The heightmap stores the Y above the highest block.
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, eye.getX(), eye.getZ()) - 1;
		BlockPos.MutableBlockPos pos = eye.mutable();
		int found = 0;
		for (int y = eye.getY() + 1; y <= top; y++) {
			if (isRock(level.getBlockState(pos.setY(y))) && ++found == MIN_ROOF_BLOCKS) {
				return true;
			}
		}
		return false;
	}
}
