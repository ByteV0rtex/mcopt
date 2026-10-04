package mcopt.metal.mixin.cpu;

import mcopt.metal.cpu.Ab;
import mcopt.metal.cpu.Cpu;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** -Dmcopt.cpu.lists=ab: times readRenderListFromTree with the region cache alternately off and on (see Ab). */
@Mixin(value = RenderSectionManager.class, remap = false)
abstract class ListAbMixin {
	@Unique private static final Ab mcopt$ab = new Ab("lists.readRenderListFromTree");

	@Inject(method = "readRenderListFromTree", at = @At("HEAD"))
	private void mcopt$begin(CallbackInfo ci) {
		if (Cpu.LISTS_AB) Cpu.listsActive = mcopt$ab.begin();
	}

	@Inject(method = "readRenderListFromTree", at = @At("RETURN"))
	private void mcopt$end(CallbackInfo ci) {
		if (Cpu.LISTS_AB) mcopt$ab.end();
	}
}
