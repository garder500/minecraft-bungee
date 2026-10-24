#version 330
#extension GL_ARB_separate_shader_objects : require

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#include <minecraft:fog.glsl>
#endif

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#endif

layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) flat in int fxId;
layout(location = 5) in vec2 screenUv;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

vec4 werewolf_fx(int id, vec2 uv) {
    float edge = smoothstep(0.25, 0.85, length(uv - 0.5) * 1.6);
    // The effect is drawn over the HUD: keep the sidebar (right, mid-height) and the chat (bottom
    // left) readable by thinning it out there.
    vec2 st = vec2(uv.x, 1.0 - uv.y); // from the top-left corner, like the GUI
    float sidebar = smoothstep(0.62, 0.84, st.x) * smoothstep(0.04, 0.22, st.y) * (1.0 - smoothstep(0.56, 0.76, st.y));
    float chat = (1.0 - smoothstep(0.30, 0.56, st.x)) * smoothstep(0.56, 0.70, st.y) * (1.0 - smoothstep(0.84, 0.96, st.y));
    float keep = 1.0 - 0.6 * max(sidebar, chat);
    if (id == 1) { // night: deep blue grade, vignette
        return vec4(mix(vec3(0.02, 0.03, 0.12), vec3(0.01, 0.01, 0.06), edge), (0.14 + 0.46 * edge) * keep);
    }
    float k = id == 2 ? 1.0 : 0.5; // dawn: warm wash, strong then fading
    return vec4(1.0, 0.68, 0.38, k * (0.16 + 0.2 * edge) * keep);
}

vec4 calculateFinalColor(vec4 color) {
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    #endif

    #if !defined(IS_SEE_THROUGH) && !defined(IS_GUI)

    #ifdef OIT_ACCUMULATE
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif

    color = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
    #endif

    return color;
}

void main() {
    vec4 effect = fxId > 0 && fxId < 10 ? werewolf_fx(fxId, screenUv) : vec4(0.0);
    #ifdef IS_GRAYSCALE
    vec4 texColor = texture(Sampler0, texCoord0).rrrr;
    #else
    vec4 texColor = texture(Sampler0, texCoord0);
    #endif

    vec4 color = fxId >= 10 ? texColor : fxId > 0 ? effect : texColor * vertexColor * ColorModulator;

    if (color.a < 0.1 && (fxId == 0 || fxId >= 10)) {
        discard;
    }

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
