package mcopt.metal.mixin.qos;

import mcopt.metal.Qos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** -Dmcopt.qos.mesh: Sodium's chunk builder threads. */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder$WorkerRunnable", remap = false)
abstract class MeshQosMixin {
	@Inject(method = "run", at = @At("HEAD"))
	private void mcopt$qos(CallbackInfo ci) {
		Qos.self("mesh");
	}
}
