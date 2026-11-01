#version 330

layout(std140) uniform MapCamera {
    mat4 ViewProjMat;
    float MinTextureLod;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 texel = MinTextureLod > 0.0 ? textureLod(Sampler0, texCoord0, MinTextureLod) : texture(Sampler0, texCoord0);
    vec4 color = texel * vertexColor;
#ifdef TRANSLUCENT
    fragColor = color;
#else
    // Only the top layer of leaves exists on the map, so holes show a dark tint instead of the void below.
    fragColor = color.a < 0.5 ? vec4(vertexColor.rgb * 0.6, 1.0) : vec4(color.rgb, 1.0);
#endif
}
