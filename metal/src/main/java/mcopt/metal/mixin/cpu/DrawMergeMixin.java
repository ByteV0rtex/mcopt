package mcopt.metal.mixin.cpu;

import mcopt.metal.cpu.Cpu;
import mcopt.metal.cpu.DrawMerge;
import net.caffeinemc.mods.sodium.client.gpu.device.batch.GLDrawBatch;
import net.caffeinemc.mods.sodium.client.gpu.device.batch.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.gpu.device.context.DrawContext;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * -Dmcopt.cpu.mergeDraws=true: before a region's multi-draw goes out, consecutive ranges that read the shared quad index buffer
 * from its start (element offset 0) and whose vertices follow each other (base[j] = base[i] + count[i] / 6 * 4) become one range
 * of the summed count: with the shared buffer's index pattern that is the same triangles in the same order, so the backend
 * issues fewer draws (each range is one Metal draw when terrain isn't recorded for occlusion). Merged ranges stay within
 * 16384 quads, the least the shared buffer ever holds. Sodium's batch itself is left as it is. Only on frames where the terrain
 * isn't recorded for the occlusion split (MetalTerrain's split choice, mirrored in Cpu.terrainSplitting), so the split's 64-quad chunks stay as they were.
 */
@Mixin(value = GLDrawBatch.class, remap = false)
abstract class DrawMergeMixin extends MultiDrawBatch {
	@Shadow @Final private long pElementPointer;
	@Shadow @Final private long pElementCount;
	@Shadow @Final private long pBaseVertex;

	@Inject(method = "draw", at = @At("HEAD"), cancellable = true)
	private void mcopt$merge(DrawContext context, CallbackInfo ci) {
		if (!Cpu.mergeActive || this.size < 2 || Cpu.terrainSplitting) return;
		DrawMerge m = DrawMerge.scratch(this.size);
		int n = m.merge(this.pElementPointer, this.pElementCount, this.pBaseVertex, this.size);
		if (n == this.size) return; // nothing merged: Sodium's own call
		context.getPass().multiDrawIndexed(MemoryUtil.memPointerBuffer(m.pointers, n), MemoryUtil.memIntBuffer(m.counts, n),
			MemoryUtil.memIntBuffer(m.bases, n), n);
		ci.cancel();
	}
}
