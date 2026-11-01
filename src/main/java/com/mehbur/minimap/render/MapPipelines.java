package com.mehbur.minimap.render;

import com.mehbur.minimap.MehbursMiniMapClient;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.resources.Identifier;

final class MapPipelines {
	static final String CAMERA_UNIFORM = "MapCamera";
	static final String ATLAS_SAMPLER = "Sampler0";

	static final RenderPipeline TERRAIN = RenderPipeline.builder()
		.withLocation(id("pipeline/map_terrain"))
		.withVertexShader(id("core/map_terrain"))
		.withFragmentShader(id("core/map_terrain"))
		.withBindGroupLayout(BindGroupLayout.builder()
			.withUniform(CAMERA_UNIFORM, UniformType.UNIFORM_BUFFER)
			.withSampler(ATLAS_SAMPLER)
			.build())
		.withColorTargetState(ColorTargetState.DEFAULT)
		.withDepthStencilState(DepthStencilState.DEFAULT)
		.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
		.withPrimitiveTopology(PrimitiveTopology.QUADS)
		.build();

	private MapPipelines() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MehbursMiniMapClient.MOD_ID, path);
	}
}
