#!/usr/bin/env python3
"""Writes the werewolf screen-effect core shaders (one variant per vanilla shader generation).

The plugin appends a glyph of font vampyuyu:fx coloured (78, 92, id) to the action bar; the text
vertex shader stretches it over the whole screen and the fragment shader paints the effect.
id 1 = night, 2 = dawn, 3 = fading dawn. Text shadows are Color / 4: (19, 23, 0|1), collapsed.

Target: Minecraft 26.3 only (pack format 97): core/text.*, #include, explicit locations, OIT.
"""
import json, pathlib

PACK = pathlib.Path(__file__).resolve().parent.parent / "pack"

COMMON_VS = """    int fx = 0;
    vec2 uv = vec2(0.0);
    ivec3 rgb = ivec3(round(Color.rgb * 255.0));
    if (rgb.r == 78 && rgb.g == 92 && rgb.b >= 1 && rgb.b <= 3) {
        fx = rgb.b;
        vec2 corner = CORNERS[gl_VertexIndex % 4];
        uv = corner * 0.5 + 0.5;
        clip = vec4(corner * clip.w, clip.z, clip.w);
    } else if (rgb.r == 19 && rgb.g == 23 && rgb.b <= 1) {
        clip = vec4(2.0, 2.0, 2.0, 1.0);
    }
    gl_Position = clip;
    fxId = fx;
    screenUv = uv;
"""
CORNERS = "const vec2 CORNERS[4] = vec2[](vec2(-1.0, 1.0), vec2(-1.0, -1.0), vec2(1.0, -1.0), vec2(1.0, 1.0));\n"
EFFECT = """vec4 werewolf_fx(int id, vec2 uv) {
    float edge = smoothstep(0.25, 0.85, length(uv - 0.5) * 1.6);
    if (id == 1) { // night: deep blue grade, heavy vignette
        return vec4(mix(vec3(0.02, 0.03, 0.12), vec3(0.01, 0.01, 0.06), edge), 0.16 + 0.62 * edge);
    }
    float k = id == 2 ? 1.0 : 0.5; // dawn: warm wash, strong then fading
    return vec4(1.0, 0.68, 0.38, k * (0.16 + 0.2 * edge));
}
"""

# 26.3: vanilla text.* with #include, explicit locations and order-independent transparency.
VSH_263 = f"""#version 330
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

{CORNERS}
void main() {{
    vec4 clip = ProjMat * ModelViewMat * vec4(Position, 1.0);
{COMMON_VS}
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
#else
    vertexColor = Color;
#endif
    texCoord0 = UV0;
}}
"""

FSH_263 = f"""#version 330
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

{EFFECT}
vec4 calculateFinalColor(vec4 color) {{
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
}}

void main() {{
    vec4 effect = fxId > 0 ? werewolf_fx(fxId, screenUv) : vec4(0.0);
    #ifdef IS_GRAYSCALE
    vec4 texColor = texture(Sampler0, texCoord0).rrrr;
    #else
    vec4 texColor = texture(Sampler0, texCoord0);
    #endif

    vec4 color = fxId > 0 ? effect : texColor * vertexColor * ColorModulator;

    if (color.a < 0.1 && fxId == 0) {{
        discard;
    }}

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}}
"""

def main():
    out = PACK / "assets" / "minecraft" / "shaders" / "core"
    out.mkdir(parents=True, exist_ok=True)
    (out / "text.vsh").write_text(VSH_263)
    (out / "text.fsh").write_text(FSH_263)
    meta_path = PACK / "pack.mcmeta"
    meta = json.loads(meta_path.read_text())
    meta.pop("overlays", None)
    meta["pack"]["min_format"] = 97
    meta_path.write_text(json.dumps(meta, indent=2) + "\n")
    print("wrote the 26.3 text shaders")

if __name__ == "__main__":
    main()
