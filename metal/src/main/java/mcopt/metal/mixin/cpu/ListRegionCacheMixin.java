package mcopt.metal.mixin.cpu;

import net.caffeinemc.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * -Dmcopt.cpu.lists=true: the render-list walk (one collector per walk, on the render thread, while no region is added or removed)
 * visits sections in Morton order, so consecutive sections nearly always share a region (8x4x8 sections); the region of the
 * last lookup is reused instead of hashing again. Same region objects, same lists.
 */
@Mixin(value = VisibleChunkCollector.class, remap = false)
abstract class ListRegionCacheMixin {
	@Unique private long mcopt$key = Long.MIN_VALUE;
	@Unique private RenderRegion mcopt$region;

	@Redirect(method = "visit", at = @At(value = "INVOKE",
		target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegionManager;getForChunk(III)Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion;"))
	private RenderRegion mcopt$cachedRegion(RenderRegionManager regions, int x, int y, int z) {
		if (!mcopt.metal.cpu.Cpu.listsActive) return regions.getForChunk(x, y, z);
		long key = RenderRegion.key(x >> RenderRegion.REGION_WIDTH_SH, y >> RenderRegion.REGION_HEIGHT_SH, z >> RenderRegion.REGION_LENGTH_SH);
		if (key != mcopt$key) {
			mcopt$key = key;
			mcopt$region = regions.getForChunk(x, y, z);
		}
		return mcopt$region;
	}
}
