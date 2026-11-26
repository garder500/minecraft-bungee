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
    if (id == 4) { // asleep: nothing of the world is left
        return vec4(0.0, 0.0, 0.015, 1.0);
    }
    if (id == 1) { // night: deep blue grade, vignette
        return vec4(mix(vec3(0.02, 0.03, 0.12), vec3(0.01, 0.01, 0.06), edge), (0.14 + 0.46 * edge) * keep);
    }
    float d = length(uv - 0.5) * 1.6;
    float grain = fract(sin(dot(floor(uv * vec2(64.0, 36.0)), vec2(12.9898, 78.233))) * 43758.5453);
    if (id == 5) { // void (a boss blinded you): the world shrinks to a small hole of sight with a magenta rim
        float dark = smoothstep(0.10, 0.34, d);
        float rim = smoothstep(0.08, 0.13, d) * (1.0 - smoothstep(0.13, 0.22, d));
        return vec4(mix(vec3(0.03, 0.0, 0.06), vec3(0.95, 0.25, 0.85), rim * 0.8), max(dark * 0.96, rim * 0.55));
    }
    if (id == 6) { // frost: crystals creep in from the edges
        float f = smoothstep(0.45, 1.0, d + grain * 0.22);
        return vec4(mix(vec3(0.62, 0.84, 1.0), vec3(0.95, 0.99, 1.0), grain), f * 0.78 * keep);
    }
    if (id == 7) { // decay, withering: a sick green-violet bloom at the edges
        float f = smoothstep(0.35, 1.0, d + grain * 0.12);
        return vec4(mix(vec3(0.30, 0.78, 0.22), vec3(0.32, 0.12, 0.40), grain * 0.6), f * 0.6 * keep);
    }
    if (id == 8) { // burn: heat climbing from the bottom edge
        float f = smoothstep(0.40, 1.0, d + (1.0 - uv.y) * 0.35 + grain * 0.1);
        return vec4(mix(vec3(1.0, 0.32, 0.05), vec3(1.0, 0.75, 0.25), grain), f * 0.62 * keep);
    }
    if (id == 9) { // warped by a boss (slowed, rooted, floating, lifted): a thin magenta ring at the rim
        float f = smoothstep(0.62, 1.0, d) * (0.7 + 0.3 * grain);
        return vec4(0.88, 0.22, 0.80, f * 0.5 * keep);
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
