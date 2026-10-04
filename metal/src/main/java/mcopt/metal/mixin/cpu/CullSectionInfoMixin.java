package mcopt.metal.mixin.cpu;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Arrays;
import mcopt.metal.cpu.CullReuse;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A rebuilt section reports a graph change (bit 1: Sodium then marks the cull graph dirty and re-culls) when its flags changed or
 * its visibility data is a different array, and every rebuild makes a new array. The cull reads only the flags and the array's
 * contents, so a rebuild with equal flags and equal contents can't change a cull. CullReuse decides what to do with that.
 */
@Mixin(value = RenderSection.class, remap = false)
abstract class CullSectionInfoMixin {
	@Shadow @Final private RenderRegion region;
	@Shadow @Final private int sectionIndex;
	@Shadow private long[] visibilityData;

	@ModifyReturnValue(method = "setRenderState", at = @At("RETURN"))
	private int mcopt$contentEqual(int changes, @Local(ordinal = 0) int prevFlags, @Local(ordinal = 0) long[] prevVisibilityData) {
		if ((changes & 1) == 0) return changes;
		boolean equal = prevVisibilityData != null && this.region.getSectionFlags(this.sectionIndex) == prevFlags
			&& Arrays.equals(prevVisibilityData, this.visibilityData);
		return CullReuse.sectionChanged(equal) ? changes : changes & ~1;
	}
}
