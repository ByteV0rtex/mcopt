package mcopt.metal.mixin.qos;

import mcopt.metal.Qos;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** -Dmcopt.qos.cull: Sodium's async cull thread, by wrapping the runnable its thread runs. */
@Mixin(value = RenderSectionManager.class, remap = false)
abstract class CullQosMixin {
	@ModifyVariable(method = "makeAsyncCullThread", at = @At("HEAD"), argsOnly = true)
	private static Runnable mcopt$qos(Runnable runnable) {
		return () -> {
			Qos.self("cull");
			runnable.run();
		};
	}
}
