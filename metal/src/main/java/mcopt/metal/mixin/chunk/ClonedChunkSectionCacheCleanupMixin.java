package mcopt.metal.mixin.chunk;

import it.unimi.dsi.fastutil.longs.Long2ReferenceLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import mcopt.metal.chunk.ChunkOpt;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSection;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSectionCache;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * C1 (-Dmcopt.chunk.clonesCleanup=true|verify|ab): the clone cache's per-frame cleanup removes the snapshots unused for 5 s by
 * testing every cached entry (render thread, every frame), so its cost grows with the cache (clones=4096).
 *
 * <p>The cache map is linked in last-use order (every acquire moves its entry to the end) and every acquire stamps its entry
 * with the cache's clock, which only cleanup advances, from the monotonic System.nanoTime. So the stamps never decrease from the
 * first entry to the last, the expiry test {@code time > stamp + 5 s} holds for a prefix of the list, and the walk can stop at
 * the first entry that has not expired: the same entries are removed through the same iterator in the same order as the full
 * walk, and the walk costs what it evicts plus one test.
 *
 * <ul>
 * <li>{@code verify}: after each cleanup, a full walk counts entries left that the full test would have removed (must stay 0).</li>
 * <li>{@code ab}: each frame runs either the original cleanup or this one (a random pick per frame) and times it, so both arms
 *     see the same scene, cache and JIT state (in-process A/B).</li>
 * </ul>
 */
@Mixin(value = ClonedChunkSectionCache.class, remap = false)
abstract class ClonedChunkSectionCacheCleanupMixin {
	@Shadow @Final private static long MAX_CACHE_DURATION;
	@Shadow @Final private Long2ReferenceLinkedOpenHashMap<ClonedChunkSection> positionToEntry;
	@Shadow private long time;

	@Unique private long mcopt$pick = 0x9E3779B97F4A7C15L;
	@Unique private long mcopt$t0;
	@Unique private int mcopt$size0;

	@Shadow
	private static long getMonotonicTimeSource() {
		throw new AssertionError();
	}

	@Inject(method = "cleanup", at = @At("HEAD"), cancellable = true)
	private void mcopt$cleanup(CallbackInfo ci) {
		if (ChunkOpt.CLONES_CLEANUP_AB) {
			long x = this.mcopt$pick;
			x ^= x << 13;
			x ^= x >>> 7;
			x ^= x << 17;
			this.mcopt$pick = x;
			if ((x & 1) == 0) {   // the original walk this frame, timed at RETURN
				this.mcopt$size0 = this.positionToEntry.size();
				this.mcopt$t0 = System.nanoTime();
				return;
			}
		}
		long t0 = ChunkOpt.CLONES_CLEANUP_AB ? System.nanoTime() : 0;
		int size0 = this.positionToEntry.size();
		this.time = getMonotonicTimeSource();
		long now = this.time;
		ObjectIterator<ClonedChunkSection> it = this.positionToEntry.values().iterator();
		while (it.hasNext()) {
			if (now > it.next().getLastUsedTimestamp() + MAX_CACHE_DURATION) {
				it.remove();
			} else {
				break;
			}
		}
		if (ChunkOpt.CLONES_CLEANUP_AB) {
			long ns = System.nanoTime() - t0;
			mcopt$record("prefix", ns, size0, size0 - this.positionToEntry.size());
		}
		if (ChunkOpt.CLONES_CLEANUP_VERIFY) {
			long left = 0;
			for (ClonedChunkSection s : this.positionToEntry.values()) {
				if (now > s.getLastUsedTimestamp() + MAX_CACHE_DURATION) left++;
			}
			ChunkOpt.count("clones.cleanup.verify.calls");
			ChunkOpt.count("clones.cleanup.verify.removed", size0 - this.positionToEntry.size());
			ChunkOpt.count(left == 0 ? "clones.cleanup.verify.same" : "clones.cleanup.verify.EXPIRED_LEFT", left == 0 ? 1 : left);
		}
		ci.cancel();
	}

	@Inject(method = "cleanup", at = @At("RETURN"))
	private void mcopt$timeOriginal(CallbackInfo ci) {
		if (!ChunkOpt.CLONES_CLEANUP_AB) return;
		long ns = System.nanoTime() - this.mcopt$t0;
		mcopt$record("original", ns, this.mcopt$size0, this.mcopt$size0 - this.positionToEntry.size());
	}

	@Unique
	private static void mcopt$record(String arm, long ns, int size, int removed) {
		String k = "clones.cleanup." + arm;
		ChunkOpt.count(k + ".calls");
		ChunkOpt.count(k + ".ns", ns);
		ChunkOpt.count(k + ".entries", size);
		ChunkOpt.count(k + ".removed", removed);
	}
}
