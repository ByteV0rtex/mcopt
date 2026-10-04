// This file is copied or adapted from Sodium (https://github.com/CaffeineMC/sodium), Copyright JellySquid
// (jellysquid3) and contributors. Unlike the rest of mcopt, it is licensed under the PolyForm Shield License 1.0.0
// (LICENSES/PolyForm-Shield-1.0.0.md, https://polyformproject.org/licenses/shield/1.0.0), not Apache-2.0.
// See NOTICE.
//
// Sodium's block_layer_opaque.fsh reading the lean outputs of sodium_terrain.vs.metal. The loader prepends
// "#define ALPHA_CUTOUT x" for the cutout and translucent passes, like Sodium's own shader defines.
#include <metal_stdlib>
using namespace metal;

struct u_Globals {
    float4x4 u_ProjectionMatrix;
    float4x4 u_ModelViewMatrix;
    float4 u_FogColor;
    float2 u_EnvironmentFog;
    float2 u_RenderFog;
    float2 u_TexelSize;
    float2 u_TexCoordShrink;
    float u_FadePeriodInv;
    uint u_UseRGSS;
};

struct main0_out { float4 fragColor [[color(0)]]; };

struct main0_in {
    half4 v_Color [[user(locn0)]];
    float2 v_TexCoord [[user(locn1)]];
    half2 v_Fog [[user(locn2)]]; // environmental (with 1 - the chunk's fade while it fades in), render distance
};

static float4 sampleNearest(texture2d<float> source, sampler s, float2 uv, float2 pixelSize, float2 du, float2 dv, float2 texelScreenSize)
{
    float2 uvTexelCoords = uv / pixelSize;
    float2 texelCenter = round(uvTexelCoords) - float2(0.5);
    float2 texelOffset = uvTexelCoords - texelCenter;
    texelOffset = (((texelOffset - float2(0.5)) * pixelSize) / texelScreenSize) + float2(0.5);
    texelOffset = fast::clamp(texelOffset, float2(0.0), float2(1.0));
    return source.sample(s, (texelCenter + texelOffset) * pixelSize, gradient2d(du, dv));
}

constant float2 RGSS[4] = { float2(0.125, 0.375), float2(-0.125, -0.375), float2(0.375, -0.125), float2(-0.375, 0.125) };

fragment main0_out main0(main0_in in [[stage_in]], constant u_Globals& g [[buffer(0)]], texture2d<float> u_BlockTex [[texture(3)]], sampler u_BlockTexSmplr [[sampler(3)]])
{
    float2 uv = in.v_TexCoord;
    float2 pixelSize = g.u_TexelSize;
    float2 du = dfdx(uv);
    float2 dv = dfdy(uv);
    float2 texelScreenSize = sqrt(du * du + dv * dv);
    float4 color = sampleNearest(u_BlockTex, u_BlockTexSmplr, uv, pixelSize, du, dv, texelScreenSize);
    if (g.u_UseRGSS != 0u) {
        float minPixelSize = fast::min(pixelSize.x, pixelSize.y);
        float blendFactor = smoothstep(minPixelSize, minPixelSize * 2.0, fast::max(texelScreenSize.x, texelScreenSize.y));
        float duLength = length(du), dvLength = length(dv);
        float mip = fast::max(0.0, log2(sqrt(fast::min(duLength, dvLength) * fast::max(duLength, dvLength)) / minPixelSize));
        float4 rgss = float4(0.0);
        for (int i = 0; i < 4; i++) rgss += u_BlockTex.sample(u_BlockTexSmplr, uv + RGSS[i] * pixelSize, level(mip));
        color = mix(color, rgss * 0.25, float4(blendFactor));
    }
    color *= float4(in.v_Color);
#ifdef ALPHA_CUTOUT
    if (color.w < ALPHA_CUTOUT) discard_fragment();
#endif
    float2 fog = saturate(float2(in.v_Fog));
    float fogValue = fast::max(fog.x, fog.y);
    main0_out out;
    out.fragColor = float4(mix(color.xyz, g.u_FogColor.xyz, float3(fogValue * g.u_FogColor.w)), color.w);
    return out;
}
