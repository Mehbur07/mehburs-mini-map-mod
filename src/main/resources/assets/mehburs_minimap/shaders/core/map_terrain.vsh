#version 330

layout(std140) uniform MapCamera {
    mat4 ViewProjMat;
    float MinTextureLod;
};

layout(std140) uniform MapChunk {
    vec3 ChunkOffset;
};

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    gl_Position = ViewProjMat * vec4(Position + ChunkOffset, 1.0);

    texCoord0 = UV0;
    vertexColor = Color;
}
