#version 330
#extension GL_ARB_separate_shader_objects : require

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#include <minecraft:fog.glsl>
#include <minecraft:sample_lightmap.glsl>
#endif

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
layout(location = 3) in ivec2 UV2;
#endif

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
uniform sampler2D Sampler2;
layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
#endif

layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;
layout(location = 4) flat out int fxId;
layout(location = 5) out vec2 screenUv;

const vec2 CORNERS[4] = vec2[](vec2(-1.0, 1.0), vec2(-1.0, -1.0), vec2(1.0, -1.0), vec2(1.0, 1.0));

const int HUD_COUNT = 44;
const int HUD_FILL = 3;
const int HUD_TEXT = 37;
const int HUD_ANCHOR[HUD_COUNT] = int[](0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 1, 1, 1);
const vec4 HUD_BOX[HUD_COUNT] = vec4[](
    vec4(-94.0, 4.0, 188.0, 34.0),
    vec4(-86.0, 11.0, 20.0, 20.0),
    vec4(-68.0, 26.0, 7.0, 9.0),
    vec4(-57.0, 27.0, 104.0, 3.0),
    vec4(56.0, 14.0, 9.0, 13.0),
    vec4(64.0, 14.0, 4.0, 13.0),
    vec4(67.0, 14.0, 9.0, 13.0),
    vec4(75.0, 14.0, 9.0, 13.0),
    vec4(-102.0, -39.0, 96.0, 78.0),
    vec4(-94.0, -31.0, 24.0, 24.0),
    vec4(-94.0, 26.0, 4.0, 7.0),
    vec4(-90.0, 26.0, 4.0, 7.0),
    vec4(-86.0, 26.0, 4.0, 7.0),
    vec4(-82.0, 26.0, 4.0, 7.0),
    vec4(-78.0, 26.0, 4.0, 7.0),
    vec4(-74.0, 26.0, 4.0, 7.0),
    vec4(-70.0, 26.0, 4.0, 7.0),
    vec4(-66.0, 26.0, 4.0, 7.0),
    vec4(-62.0, 26.0, 4.0, 7.0),
    vec4(-58.0, 26.0, 4.0, 7.0),
    vec4(-54.0, 26.0, 4.0, 7.0),
    vec4(-50.0, 26.0, 4.0, 7.0),
    vec4(-46.0, 26.0, 4.0, 7.0),
    vec4(-42.0, 26.0, 4.0, 7.0),
    vec4(-38.0, 26.0, 4.0, 7.0),
    vec4(-34.0, 26.0, 4.0, 7.0),
    vec4(-30.0, 26.0, 4.0, 7.0),
    vec4(-26.0, 26.0, 4.0, 7.0),
    vec4(-22.0, 26.0, 4.0, 7.0),
    vec4(-18.0, 26.0, 4.0, 7.0),
    vec4(-63.0, -19.0, 13.0, 13.0),
    vec4(-50.0, -19.0, 13.0, 13.0),
    vec4(-37.0, -19.0, 13.0, 13.0),
    vec4(-24.0, -19.0, 13.0, 13.0),
    vec4(-102.0, 43.0, 96.0, 26.0),
    vec4(-30.0, 49.0, 9.0, 13.0),
    vec4(-22.0, 49.0, 9.0, 13.0),
    vec4(-58.0, 9.0, 10.0, 14.0),
    vec4(-58.0, 10.0, 9.0, 12.0),
    vec4(-54.0, -34.0, 9.0, 12.0),
    vec4(-96.0, 2.0, 9.0, 12.0),
    vec4(-96.0, 12.0, 7.0, 12.0),
    vec4(-96.0, 45.0, 9.0, 12.0),
    vec4(-96.0, 54.0, 7.0, 12.0));

void main() {
    vec4 clip = ProjMat * ModelViewMat * vec4(Position, 1.0);
    int fx = 0;
    vec2 uv = vec2(0.0);
    ivec3 rgb = ivec3(round(Color.rgb * 255.0));
    if (rgb.r >= 78 && rgb.r <= 81 && rgb.g >= 100 && rgb.g < 100 + HUD_COUNT) {
        // A custom HUD glyph: move its quad to its element's box, in GUI pixels. The red channel
        // picks the size: 78 follows the window, 79 full, 80 three quarters, 81 half. The plates are drawn small
        // enough to stay at full size (the only one that keeps the pixel text whole) down to a 416 x 234 GUI.
        int e = rgb.g - 100;
        vec2 gui = vec2(2.0 / ProjMat[0][0], -2.0 / ProjMat[1][1]);
        float fit = min(gui.x / 640.0, gui.y / 360.0);
        float f = rgb.r == 79 ? 1.0 : rgb.r == 80 ? 0.75 : rgb.r == 81 ? 0.5 : (fit >= 0.65 ? 1.0 : fit >= 0.45 ? 0.75 : 0.5);
        vec2 anchor = HUD_ANCHOR[e] == 0 ? vec2(floor(gui.x * 0.5), 0.0) : vec2(gui.x, floor(gui.y * 0.5));
        // The role panel (right side) is drawn a little smaller than the top plate.
        if (HUD_ANCHOR[e] == 1) f *= 1.0;
        vec4 box = HUD_BOX[e];
        // A character of a line of text (elements from HUD_TEXT on): the blue channel is how far right of the
        // line's origin its cell starts.
        bool isText = e >= HUD_TEXT;
        if (isText) box.x += float(rgb.b);
        float w = e == HUD_FILL ? max(1.0, floor(box.z * float(rgb.b) / 255.0 + 0.5)) : box.z;
        int k = gl_VertexIndex % 4;
        vec2 corner = vec2((k == 2 || k == 3) ? w : 0.0, (k == 1 || k == 2) ? box.w : 0.0);
        // Text rounds its origin and its size apart, so every cell of a line keeps the same size at 0.75 and 0.5.
        vec2 px = anchor + (isText ? floor(box.xy * f + 0.5) + floor(corner * f + 0.5) : floor((box.xy + corner) * f + 0.5));
        clip = ProjMat * ModelViewMat * vec4(px, Position.z, 1.0);
        fx = 10 + e;
    } else if (rgb.r == 78 && rgb.g == 92 && rgb.b >= 1 && rgb.b <= 4) {
        fx = rgb.b;
        vec2 corner = CORNERS[gl_VertexIndex % 4];
        uv = corner * 0.5 + 0.5;
        clip = vec4(corner * clip.w, clip.z, clip.w);
    } else if (rgb.r >= 19 && rgb.r <= 20 && ((rgb.g == 23 && rgb.b <= 1) || (rgb.g >= 25 && rgb.g <= 36))) {
        clip = vec4(2.0, 2.0, 2.0, 1.0);
    }
    gl_Position = clip;
    fxId = fx;
    screenUv = uv;

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
#else
    vertexColor = Color;
#endif
    texCoord0 = UV0;
}
