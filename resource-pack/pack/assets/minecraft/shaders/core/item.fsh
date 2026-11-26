#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

#ifdef GLINT
uniform sampler2D GlintSampler;
#endif

#ifndef OIT_ALPHA_ONLY
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#endif
layout(location = 2) in vec4 vertexColor;
#ifndef OIT_ALPHA_ONLY
layout(location = 3) in vec4 lightMapColor;
layout(location = 4) in vec4 overlayColor;
#endif
layout(location = 5) in vec2 texCoord0;
#ifdef GLINT
layout(location = 6) in vec2 texCoordGlint;
#endif

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

// vampyuyu: the burning texels (alpha 251)
vec3 vampyuyu_glow(vec3 c, vec2 uv) {
    float t = GameTime * 1200.0;                                   // seconds of the day cycle
    float wave = 0.5 + 0.5 * sin(t * 3.0 - (uv.x + uv.y) * 600.0); // a wave running across the sprite
    float flick = 0.5 + 0.5 * sin(t * 13.0 + uv.y * 2400.0);       // a fast shimmer
    float peak = max(max(c.r, c.g), c.b);
    vec3 hot = c * (1.18 + 0.38 * wave + 0.07 * flick);
    hot += vec3(1.0, 0.94, 0.86) * peak * peak * peak * (0.18 + 0.32 * wave);
    return hot;
}

#ifndef OIT_ALPHA_ONLY
vec4 calculateFinalColor(vec4 color) {
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;

    #ifdef GLINT
    vec4 glintColor = GlintAlpha * texture(GlintSampler, texCoordGlint);
    color.rgb += glintColor.rgb * glintColor.rgb;
    #endif

    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif

    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}
#endif

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    bool glow = abs(color.a * 255.0 - 251.0) < 0.5;
    bool halo = abs(color.a * 255.0 - 96.0) < 0.5;   // a halo shell texel (bbmodel.HALO_ALPHA)
    if (glow) {
        color.a = 1.0;
    }
    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    color *= vertexColor * ColorModulator;
    if (glow) {
        color.rgb = vampyuyu_glow(color.rgb, texCoord0);
    }
    if (halo) {   // a translucent haze round the effect that breathes: the stand-in for a bloom halo
        float t = GameTime * 1200.0;
        float breath = 0.5 + 0.5 * sin(t * 4.0 - (texCoord0.x + texCoord0.y) * 300.0);
        color.rgb = color.rgb * (1.25 + 0.3 * breath);
        color.a = 0.22 + 0.2 * breath;
    }

    #ifdef GLINT
    color.a = max(color.a, GlintAlpha);
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
