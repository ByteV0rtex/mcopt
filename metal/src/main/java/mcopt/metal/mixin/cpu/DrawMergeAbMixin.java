package mcopt.metal.mixin.cpu;

import mcopt.metal.cpu.Ab;
import mcopt.metal.cpu.Cpu;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** -Dmcopt.cpu.mergeDraws=ab: times DefaultChunkRenderer.render with the merge alternately off and on (see Ab). */
@Mixin(value = DefaultChunkRenderer.class, remap = false)
abstract class DrawMergeAbMixin {
	@Unique private static final Ab mcopt$ab = new Ab("mergeDraws.DefaultChunkRenderer.render");

	@Inject(method = "render", at = @At("HEAD"))
	private void mcopt$begin(CallbackInfo ci) {
		if (Cpu.MERGE_AB) Cpu.mergeActive = mcopt$ab.begin();
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void mcopt$end(CallbackInfo ci) {
		if (Cpu.MERGE_AB) mcopt$ab.end();
	}
}
