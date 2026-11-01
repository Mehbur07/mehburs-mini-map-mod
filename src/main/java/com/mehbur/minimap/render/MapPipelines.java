package com.mehbur.minimap.render;

import com.mehbur.minimap.MehbursMiniMapClient;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.resources.Identifier;

final class MapPipelines {
	static final String CAMERA_UNIFORM = "MapCamera";
	static final String CHUNK_UNIFORM = "MapChunk";
	static final String ATLAS_SAMPLER = "Sampler0";

	private static final RenderPipeline.Snippet TERRAIN_SNIPPET = RenderPipeline.builder()
		.withVertexShader(id("core/map_terrain"))
		.withFragmentShader(id("core/map_terrain"))
		.withBindGroupLayout(BindGroupLayout.builder()
			.withUniform(CAMERA_UNIFORM, UniformType.UNIFORM_BUFFER)
			.withUniform(CHUNK_UNIFORM, UniformType.UNIFORM_BUFFER)
			.withSampler(ATLAS_SAMPLER)
			.build())
		.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
		.withPrimitiveTopology(PrimitiveTopology.QUADS)
		.buildSnippet();

	static final RenderPipeline OPAQUE = RenderPipeline.builder(TERRAIN_SNIPPET)
		.withLocation(id("pipeline/map_terrain"))
		.withColorTargetState(ColorTargetState.DEFAULT)
		.withDepthStencilState(DepthStencilState.DEFAULT)
		.build();

	static final RenderPipeline TRANSLUCENT = RenderPipeline.builder(TERRAIN_SNIPPET)
		.withLocation(id("pipeline/map_terrain_translucent"))
		.withShaderDefine("TRANSLUCENT")
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
		.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
		.build();

	private MapPipelines() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MehbursMiniMapClient.MOD_ID, path);
	}
}
