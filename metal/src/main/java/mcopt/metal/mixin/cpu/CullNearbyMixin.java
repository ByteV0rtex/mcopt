package mcopt.metal.mixin.cpu;

import mcopt.metal.cpu.CullerNearby;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.chunk.storage.SectionStorage;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records, on the cull thread, the camera's neighbour sections the graph search didn't reach (see CullReuse). Read-only. */
@Mixin(value = OcclusionCuller.class, remap = false)
abstract class CullNearbyMixin implements CullerNearby {
	@Shadow @Final private SectionStorage sections;
	@Shadow private int token;

	@Unique private final RenderSection[] mcopt$nearby = new RenderSection[26];
	@Unique private int mcopt$nearbyCount;

	@Inject(method = "addNearbySections", at = @At("HEAD"))
	private void mcopt$recordNearby(Viewport viewport, CallbackInfo ci) {
		SectionPos origin = viewport.getChunkCoord();
		int n = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) continue;
					RenderSection section = this.sections.getCurrent(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					if (section != null && section.getSearchToken() != this.token) mcopt$nearby[n++] = section;
				}
			}
		}
		for (int i = n; i < 26; i++) mcopt$nearby[i] = null;
		mcopt$nearbyCount = n;
	}

	@Override
	public RenderSection[] mcopt$nearby() {
		return mcopt$nearby;
	}

	@Override
	public void mcopt$setNearby(RenderSection[] sections, int count) {
		System.arraycopy(sections, 0, mcopt$nearby, 0, 26);
		mcopt$nearbyCount = count;
	}

	@Override
	public int mcopt$nearbyCount() {
		return mcopt$nearbyCount;
	}
}
