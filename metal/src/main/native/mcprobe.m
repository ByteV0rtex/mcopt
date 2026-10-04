// Portions of this file are copied or adapted from Sodium (https://github.com/CaffeineMC/sodium), Copyright
// JellySquid (jellysquid3) and contributors. Those portions are licensed under the PolyForm Shield License 1.0.0
// (LICENSES/PolyForm-Shield-1.0.0.md, https://polyformproject.org/licenses/shield/1.0.0); the rest of the
// file is Apache-2.0 like the rest of mcopt. See NOTICE.
//
// Debug-only visibility probe (-Dmcopt.metal.probe=N, see MetalProbe.java): replays one frame's Sodium terrain quads
// into an id buffer and reports which quads reach a pixel. Answers "how much of the drawn geometry could culling skip".
#import "mcmetal.h"
#include <string.h>

static NSString *probeSource = @
	"#include <metal_stdlib>\n"
	"using namespace metal;\n"
	"struct Globals { float4x4 proj; float4x4 modelView; float4 fogColor; float2 envFog; float2 renderFog; float2 texelSize; float2 texCoordShrink;\n"
	"                float fadePeriodInv; uint useRGSS; };\n"
	"struct Draw { packed_float3 origin; uint quadBase; uint quads; uint kind; };\n"  // kind: 0 solid, 1 cutout, 2 translucent
	"struct Vertex { uint posHi; uint posLo; uint color; uint tex; uint lightData; };\n"  // Sodium's 20-byte compact vertex
	"struct VOut { float4 pos [[position]]; float2 uv; half alpha; uint id [[flat]]; };\n"
	"static float4 clip(const device Vertex &v, constant Draw &d, constant Globals &g, thread float2 &uv) {\n"
	"  uint3 hi = (uint3(v.posHi) >> uint3(0, 10, 20)) & 1023u;\n"
	"  uint3 lo = (uint3(v.posLo) >> uint3(0, 10, 20)) & 1023u;\n"
	"  float3 p = float3((hi << 10) | lo) * 3.0517578125e-05 - 8.0;\n"
	"  p += float3(d.origin) + float3((uint3(v.lightData >> 24) >> uint3(5, 0, 2)) & uint3(7, 3, 7)) * 16.0;\n"
	"  uint2 t = uint2(v.tex & 0xFFFFu, v.tex >> 16);\n"
	"  uv = select(float2(-1.0), float2(1.0), (t >> 15) != 0u) * g.texCoordShrink + float2(t & 32767u) / 32768.0;\n"
	"  float4 c = (g.proj * g.modelView) * float4(p, 1.0);\n"
	"  c.y = -c.y;\n"  // the backend flips vertex Y (see MetalPipeline)
	"  return c;\n"
	"}\n"
	"constant ushort CORNERS[6] = {0, 1, 2, 2, 3, 0};\n"  // SharedQuadIndexBuffer's pattern
	"vertex VOut probe_vs(uint vid [[vertex_id]], const device Vertex *verts [[buffer(0)]], constant Draw &d [[buffer(1)]], constant Globals &g [[buffer(2)]]) {\n"
	"  uint q = vid / 6;\n"
	"  const device Vertex &v = verts[q * 4 + CORNERS[vid % 6]];\n"
	"  VOut o; o.pos = clip(v, d, g, o.uv); o.alpha = half(unpack_unorm4x8_to_float(v.color).w); o.id = d.quadBase + q + 1; return o;\n"
	"}\n"
	// The cutout test of sodium_terrain.fs.metal, with the game's own atlas sampler: which pixels a cutout quad keeps (and so
	// which it hides) must match the real draw's, mip level and RGSS included. The lightmap's alpha is always 1.
	"static float4 sampleNearest(texture2d<float> source, sampler s, float2 uv, float2 pixelSize, float2 du, float2 dv, float2 texelScreenSize) {\n"
	"  float2 uvTexelCoords = uv / pixelSize;\n"
	"  float2 texelCenter = round(uvTexelCoords) - float2(0.5);\n"
	"  float2 texelOffset = uvTexelCoords - texelCenter;\n"
	"  texelOffset = (((texelOffset - float2(0.5)) * pixelSize) / texelScreenSize) + float2(0.5);\n"
	"  texelOffset = fast::clamp(texelOffset, float2(0.0), float2(1.0));\n"
	"  return source.sample(s, (texelCenter + texelOffset) * pixelSize, gradient2d(du, dv));\n"
	"}\n"
	"constant float2 RGSS[4] = { float2(0.125, 0.375), float2(-0.125, -0.375), float2(0.375, -0.125), float2(-0.375, 0.125) };\n"
	"fragment uint probe_opaque_fs(VOut in [[stage_in]], constant Draw &d [[buffer(1)]], constant Globals &g [[buffer(2)]],\n"
	"                              texture2d<float> atlas [[texture(0)]], sampler s [[sampler(0)]]) {\n"
	"  if (d.kind != 1) return in.id;\n"
	"  float2 uv = in.uv, pixelSize = g.texelSize, du = dfdx(uv), dv = dfdy(uv);\n"
	"  float2 texelScreenSize = sqrt(du * du + dv * dv);\n"
	"  float4 color = sampleNearest(atlas, s, uv, pixelSize, du, dv, texelScreenSize);\n"
	"  if (g.useRGSS != 0u) {\n"
	"    float minPixelSize = fast::min(pixelSize.x, pixelSize.y);\n"
	"    float blendFactor = smoothstep(minPixelSize, minPixelSize * 2.0, fast::max(texelScreenSize.x, texelScreenSize.y));\n"
	"    float duLength = length(du), dvLength = length(dv);\n"
	"    float mip = fast::max(0.0, log2(sqrt(fast::min(duLength, dvLength) * fast::max(duLength, dvLength)) / minPixelSize));\n"
	"    float4 rgss = float4(0.0);\n"
	"    for (int i = 0; i < 4; i++) rgss += atlas.sample(s, uv + RGSS[i] * pixelSize, level(mip));\n"
	"    color = mix(color, rgss * 0.25, float4(blendFactor));\n"
	"  }\n"
	"  if (color.w * float(in.alpha) < 0.5) discard_fragment();\n"
	"  return in.id;\n"
	"}\n"
	"[[early_fragment_tests]] fragment void probe_translucent_fs(VOut in [[stage_in]], device atomic_uint *bits [[buffer(3)]]) {\n"
	"  uint q = in.id - 1;\n"
	"  atomic_fetch_or_explicit(&bits[q >> 5], 1u << (q & 31), memory_order_relaxed);\n"
	"}\n"
	"kernel void probe_mark(texture2d<uint, access::read> vis [[texture(0)]], device atomic_uint *bits [[buffer(3)]],\n"
	"                       device atomic_uint *pixels [[buffer(4)]], uint2 id [[thread_position_in_grid]]) {\n"
	"  if (id.x >= vis.get_width() || id.y >= vis.get_height()) return;\n"
	"  uint v = vis.read(id).r;\n"
	"  if (v == 0) return;\n"
	"  atomic_fetch_or_explicit(&bits[(v - 1) >> 5], 1u << ((v - 1) & 31), memory_order_relaxed);\n"
	"  atomic_fetch_add_explicit(pixels, 1, memory_order_relaxed);\n"
	"}\n"
	// Per quad, the first that applies: 0 off screen (all corners past one clip plane), 1 back-facing or degenerate (culled
	// pipelines only), 2 covers no pixel center (bounds test), 3 none of those: visible or occluded.
	"kernel void probe_classify(const device Vertex *verts [[buffer(0)]], constant Draw &d [[buffer(1)]], constant Globals &g [[buffer(2)]],\n"
	"                           device uchar *classes [[buffer(5)]], constant uint4 &frame [[buffer(6)]], uint q [[thread_position_in_grid]]) {\n"
	"  if (q >= d.quads) return;\n"
	"  float4 c[4]; float2 uv;\n"
	"  for (int i = 0; i < 4; i++) c[i] = clip(verts[q * 4 + i], d, g, uv);\n"
	"  bool out[6] = {true, true, true, true, true, true}; bool front = true;\n"
	"  for (int i = 0; i < 4; i++) {\n"
	"    out[0] &= c[i].x < -c[i].w; out[1] &= c[i].x > c[i].w; out[2] &= c[i].y < -c[i].w; out[3] &= c[i].y > c[i].w;\n"
	"    out[4] &= c[i].z < 0.0; out[5] &= c[i].z > c[i].w; front &= c[i].w > 0.0;\n"
	"  }\n"
	"  uchar k = 3;\n"
	"  if (out[0] || out[1] || out[2] || out[3] || out[4] || out[5]) k = 0;\n"
	"  else if (front) {\n"
	"    float2 s[4]; float area = 0.0;\n"
	"    for (int i = 0; i < 4; i++) s[i] = (c[i].xy / c[i].w * 0.5 + 0.5) * float2(frame.xy);\n"
	"    for (int i = 0; i < 4; i++) area += s[i].x * s[(i + 1) & 3].y - s[(i + 1) & 3].x * s[i].y;\n"
	"    float2 lo = min(min(s[0], s[1]), min(s[2], s[3])), hi = max(max(s[0], s[1]), max(s[2], s[3]));\n"
	"    bool covers = all(ceil(lo - 0.5) <= floor(hi - 0.5));\n"
	"    if (frame.z != 0 && (frame.w != 0 ? area >= 0.0 : area <= 0.0)) k = 1;\n"  // sign checked against the id buffer: culled quads never show
	"    else if (!covers) k = 2;\n"
	"  }\n"
	"  classes[d.quadBase + q] = k;\n"
	"}\n";

#define DRAW_BYTES 48  // per draw from Java: the MSL Draw (24 bytes), then vertex buffer, byte offset, cull flag

static id<MTLRenderPipelineState> probePipeline(Ctx *ctx, id<MTLLibrary> lib, NSString *fs, MTLPixelFormat color, NSError **e) {
	MTLRenderPipelineDescriptor *pd = [[MTLRenderPipelineDescriptor new] autorelease];
	pd.vertexFunction = [[lib newFunctionWithName:@"probe_vs"] autorelease];
	pd.fragmentFunction = [[lib newFunctionWithName:fs] autorelease];
	pd.colorAttachments[0].pixelFormat = color;
	pd.depthAttachmentPixelFormat = MTLPixelFormatDepth32Float;
	return [[ctx->device newRenderPipelineStateWithDescriptor:pd error:e] autorelease];
}

static void drawAll(id<MTLRenderCommandEncoder> r, const uint8_t *draws, int count, id<MTLBuffer> globals, uint64_t globalsOffset, int pass,
	int frontWinding) {
	[r setVertexBuffer:globals offset:globalsOffset atIndex:2];
	[r setFrontFacingWinding:frontWinding ? MTLWindingClockwise : MTLWindingCounterClockwise];
	for (int i = 0; i < count; i++) {
		const uint8_t *d = draws + i * DRAW_BYTES;
		uint32_t quads, kind;
		memcpy(&quads, d + 16, 4);
		memcpy(&kind, d + 20, 4);
		if ((kind == 2) != (pass == 1)) continue;
		id<MTLBuffer> vb;
		uint64_t offset;
		uint32_t cull;
		memcpy(&vb, d + 24, 8);
		memcpy(&offset, d + 32, 8);
		memcpy(&cull, d + 40, 4);
		[r setCullMode:cull ? MTLCullModeBack : MTLCullModeNone];
		[r setVertexBuffer:vb offset:offset atIndex:0];
		[r setVertexBytes:d length:24 atIndex:1];
		[r setFragmentBytes:d length:24 atIndex:1];
		[r drawPrimitives:MTLPrimitiveTypeTriangle vertexStart:0 vertexCount:quads * 6];
	}
}

// Fills bits (1 per quad, visible), classes (1 byte per quad, see probe_classify) and *pixels (covered pixels). 0 on success.
int mc_probe(Ctx *ctx, const uint8_t *draws, int count, uint32_t totalQuads, id<MTLBuffer> globals, uint64_t globalsOffset, id<MTLTexture> atlas,
	id<MTLSamplerState> atlasSampler, int width, int height, int depthCompare, int frontIsClockwise, uint32_t *bitsOut, uint8_t *classesOut, uint32_t *pixelsOut, char *err, int errCap) {
	@autoreleasepool {
		NSError *e = nil;
		id<MTLDevice> dev = ctx->device;
		id<MTLLibrary> lib = [[dev newLibraryWithSource:probeSource options:nil error:&e] autorelease];
		id<MTLRenderPipelineState> opaque = lib ? probePipeline(ctx, lib, @"probe_opaque_fs", MTLPixelFormatR32Uint, &e) : nil;
		id<MTLRenderPipelineState> translucent = opaque ? probePipeline(ctx, lib, @"probe_translucent_fs", MTLPixelFormatInvalid, &e) : nil;
		id<MTLComputePipelineState> mark = translucent ? [[dev newComputePipelineStateWithFunction:[[lib newFunctionWithName:@"probe_mark"] autorelease] error:&e] autorelease] : nil;
		id<MTLComputePipelineState> classify = mark ? [[dev newComputePipelineStateWithFunction:[[lib newFunctionWithName:@"probe_classify"] autorelease] error:&e] autorelease] : nil;
		if (!classify) {
			strlcpy(err, e ? e.description.UTF8String : "probe setup failed", errCap);
			return 1;
		}
		MTLTextureDescriptor *td = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatR32Uint width:width height:height mipmapped:NO];
		td.usage = MTLTextureUsageRenderTarget | MTLTextureUsageShaderRead;
		td.storageMode = MTLStorageModePrivate;
		id<MTLTexture> vis = [[dev newTextureWithDescriptor:td] autorelease];
		td.pixelFormat = MTLPixelFormatDepth32Float;
		td.usage = MTLTextureUsageRenderTarget;
		id<MTLTexture> depth = [[dev newTextureWithDescriptor:td] autorelease];
		uint32_t words = totalQuads / 32 + 1;
		id<MTLBuffer> bits = [[dev newBufferWithLength:words * 4 options:MTLResourceStorageModeShared] autorelease];
		id<MTLBuffer> pixels = [[dev newBufferWithLength:4 options:MTLResourceStorageModeShared] autorelease];
		id<MTLBuffer> classes = [[dev newBufferWithLength:totalQuads + 1 options:MTLResourceStorageModeShared] autorelease];
		memset(bits.contents, 0, words * 4);
		memset(pixels.contents, 0, 4);

		MTLDepthStencilDescriptor *dd = [[MTLDepthStencilDescriptor new] autorelease];
		dd.depthCompareFunction = (MTLCompareFunction) depthCompare;
		dd.depthWriteEnabled = YES;
		id<MTLDepthStencilState> write = [[dev newDepthStencilStateWithDescriptor:dd] autorelease];
		dd.depthWriteEnabled = NO;
		id<MTLDepthStencilState> keep = [[dev newDepthStencilStateWithDescriptor:dd] autorelease];

		id<MTLCommandBuffer> cb = [ctx->queue commandBuffer];
		MTLRenderPassDescriptor *rp = [MTLRenderPassDescriptor renderPassDescriptor];
		rp.colorAttachments[0].texture = vis;
		rp.colorAttachments[0].loadAction = MTLLoadActionClear;
		rp.colorAttachments[0].clearColor = MTLClearColorMake(0, 0, 0, 0);
		rp.colorAttachments[0].storeAction = MTLStoreActionStore;
		rp.depthAttachment.texture = depth;
		rp.depthAttachment.loadAction = MTLLoadActionClear;
		rp.depthAttachment.clearDepth = 0.0;  // reversed Z, like the game's
		rp.depthAttachment.storeAction = MTLStoreActionStore;
		id<MTLRenderCommandEncoder> r = [cb renderCommandEncoderWithDescriptor:rp];
		[r setRenderPipelineState:opaque];
		[r setDepthStencilState:write];
		[r setFragmentTexture:atlas atIndex:0];
		[r setFragmentSamplerState:atlasSampler atIndex:0];
		[r setFragmentBuffer:globals offset:globalsOffset atIndex:2];
		drawAll(r, draws, count, globals, globalsOffset, 0, frontIsClockwise);
		[r endEncoding];

		id<MTLComputeCommandEncoder> c = [cb computeCommandEncoder];
		[c setComputePipelineState:mark];
		[c setTexture:vis atIndex:0];
		[c setBuffer:bits offset:0 atIndex:3];
		[c setBuffer:pixels offset:0 atIndex:4];
		[c dispatchThreads:MTLSizeMake(width, height, 1) threadsPerThreadgroup:MTLSizeMake(8, 8, 1)];
		[c endEncoding];

		MTLRenderPassDescriptor *tp = [MTLRenderPassDescriptor renderPassDescriptor];
		tp.depthAttachment.texture = depth;
		tp.depthAttachment.loadAction = MTLLoadActionLoad;
		tp.depthAttachment.storeAction = MTLStoreActionDontCare;
		tp.renderTargetWidth = width;
		tp.renderTargetHeight = height;
		r = [cb renderCommandEncoderWithDescriptor:tp];
		[r setRenderPipelineState:translucent];
		[r setDepthStencilState:keep];
		[r setFragmentBuffer:bits offset:0 atIndex:3];
		drawAll(r, draws, count, globals, globalsOffset, 1, frontIsClockwise);
		[r endEncoding];

		c = [cb computeCommandEncoder];
		[c setComputePipelineState:classify];
		[c setBuffer:globals offset:globalsOffset atIndex:2];
		[c setBuffer:classes offset:0 atIndex:5];
		for (int i = 0; i < count; i++) {
			const uint8_t *d = draws + i * DRAW_BYTES;
			uint32_t quads, cull;
			id<MTLBuffer> vb;
			uint64_t offset;
			memcpy(&quads, d + 16, 4);
			memcpy(&vb, d + 24, 8);
			memcpy(&offset, d + 32, 8);
			memcpy(&cull, d + 40, 4);
			uint32_t frame[4] = {width, height, cull, frontIsClockwise};
			[c setBuffer:vb offset:offset atIndex:0];
			[c setBytes:d length:24 atIndex:1];
			[c setBytes:frame length:sizeof frame atIndex:6];
			[c dispatchThreads:MTLSizeMake(quads, 1, 1) threadsPerThreadgroup:MTLSizeMake(64, 1, 1)];
		}
		[c endEncoding];
		[cb commit];
		[cb waitUntilCompleted];
		if (cb.error) {
			strlcpy(err, cb.error.description.UTF8String, errCap);
			return 1;
		}
		memcpy(bitsOut, bits.contents, words * 4);
		memcpy(classesOut, classes.contents, totalQuads);
		*pixelsOut = *(uint32_t *) pixels.contents;
		return 0;
	}
}
