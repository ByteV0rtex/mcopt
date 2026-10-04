package mcopt.metal.mixin.chunk;

import mcopt.metal.chunk.ChunkOpt;
import mcopt.metal.chunk.CloneCheck;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSection;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSectionCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * C1 (-Dmcopt.chunk.clones=N): Sodium's cache of cloned sections (render thread) holds 512; a full rebuild of the fixture scene
 * (4478 mesh tasks, 27 sections each) cloned ~80k sections, each position ~15 times, because the BFS-ordered tasks revisit a
 * neighbourhood after the LRU has dropped it. Clones are immutable snapshots that Sodium invalidates per position on every block
 * or light change and expires after 5 s, so a larger cache only serves more of the same still-valid snapshots.
 * -Dmcopt.chunk.clonesVerify=true checks that claim on every hit against a fresh clone (CloneCheck).
 */
@Mixin(value = ClonedChunkSectionCache.class, remap = false)
abstract class ClonedChunkSectionCacheMixin {
	@Unique private boolean mcopt$cloned;

	@Shadow
	private ClonedChunkSection clone(int x, int y, int z) {
		throw new AssertionError();
	}

	@ModifyConstant(method = "acquire", constant = @Constant(intValue = 512))
	private int mcopt$capacity(int original) {
		return ChunkOpt.CLONES;
	}

	@Inject(method = "clone", at = @At("HEAD"))
	private void mcopt$miss(int x, int y, int z, CallbackInfoReturnable<ClonedChunkSection> cir) {
		this.mcopt$cloned = true;
	}

	@Inject(method = "acquire", at = @At("HEAD"))
	private void mcopt$start(int x, int y, int z, CallbackInfoReturnable<ClonedChunkSection> cir) {
		this.mcopt$cloned = false;
	}

	@Inject(method = "acquire", at = @At("RETURN"))
	private void mcopt$verify(int x, int y, int z, CallbackInfoReturnable<ClonedChunkSection> cir) {
		if (!ChunkOpt.CLONES_VERIFY) return;
		if (this.mcopt$cloned) {
			ChunkOpt.count("clones.verify.miss");
			return;
		}
		ClonedChunkSection cached = cir.getReturnValue();
		if (cached != null) CloneCheck.compare(cached, this.clone(x, y, z));
	}
}
