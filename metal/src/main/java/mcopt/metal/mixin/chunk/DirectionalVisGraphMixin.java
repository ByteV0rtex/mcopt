/*
 * This file is copied or adapted from Sodium (https://github.com/CaffeineMC/sodium), Copyright JellySquid
 * (jellysquid3) and contributors. Unlike the rest of mcopt, it is licensed under the PolyForm Shield License 1.0.0
 * (LICENSES/PolyForm-Shield-1.0.0.md, https://polyformproject.org/licenses/shield/1.0.0), not Apache-2.0.
 * See NOTICE.
 */
package mcopt.metal.mixin.chunk;

import mcopt.metal.chunk.VisSweep;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.DirectionalVisGraph;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.GraphDirection;
import net.caffeinemc.mods.sodium.client.util.collections.BitArray;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * C1 (-Dmcopt.chunk.mesh=vis): Sodium's per-section visibility graph (11% of mesher CPU on the Neo) by VisSweep's bit-parallel
 * sweep instead of 12 cell-by-cell floods over copies of the block bitset. Same VisibilitySet bits (VisSweep explains why;
 * checked against Sodium's own code on 200 000 random and structured sections: 0 differences).
 */
@Mixin(value = DirectionalVisGraph.class, remap = false)
abstract class DirectionalVisGraphMixin {
	@Shadow @Final private BitArray blocks;

	/**
	 * @author mcopt
	 * @reason bit-parallel monotone flood (opt-in); identical result.
	 */
	@Overwrite
	private VisibilitySet resolveWithDirections(int directionSet) {
		VisibilitySet visibilitySet = new VisibilitySet();
		long[] opaque = ((BitArrayAccessor) this.blocks).mcopt$words();
		int origins = ~directionSet & 63;
		for (int i = 0; i < 3; i++) {
			int origin = Integer.numberOfTrailingZeros(origins);
			origins &= ~(1 << origin);
			int reached = VisSweep.reach(opaque, directionSet, origin);
			if (reached == 0) continue;
			Direction from = GraphDirection.toEnum(origin);
			for (int d = 0; d < 6; d++) {
				if ((reached & 1 << d) != 0) visibilitySet.set(from, GraphDirection.toEnum(d), true);
			}
			visibilitySet.set(from, from, true);
		}
		return visibilitySet;
	}
}
