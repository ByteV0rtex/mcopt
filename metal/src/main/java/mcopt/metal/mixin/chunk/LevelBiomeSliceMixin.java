/*
 * This file is copied or adapted from Sodium (https://github.com/CaffeineMC/sodium), Copyright JellySquid
 * (jellysquid3) and contributors. Unlike the rest of mcopt, it is licensed under the PolyForm Shield License 1.0.0
 * (LICENSES/PolyForm-Shield-1.0.0.md, https://polyformproject.org/licenses/shield/1.0.0), not Apache-2.0.
 * See NOTICE.
 */
package mcopt.metal.mixin.chunk;

import mcopt.metal.chunk.ChunkOpt;
import net.caffeinemc.mods.sodium.client.world.biome.LevelBiomeSlice;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * C1 (-Dmcopt.chunk.mesh=biome): LevelBiomeSlice.calculateUniform checks, for each of the 512 inner biome cells, whether its 27
 * neighbours hold the same biome (13 824 comparisons per mesh task, ~2% of mesher CPU). When all 1728 cells of the slice hold one
 * biome (most tasks), every inner cell is uniform: the same 512 flags are set true without the comparisons. Otherwise the original
 * loop runs. Only the inner cells are written, as before.
 */
@Mixin(value = LevelBiomeSlice.class, remap = false)
abstract class LevelBiomeSliceMixin {
	@Shadow @Final private Holder<Biome>[] biomes;
	@Shadow @Final private boolean[] uniform;

	@Shadow
	private boolean hasUniformNeighbors(int cellX, int cellY, int cellZ) {
		throw new AssertionError();
	}

	@Shadow
	private static int dataArrayIndex(int cellX, int cellY, int cellZ) {
		throw new AssertionError();
	}

	/**
	 * @author mcopt
	 * @reason one-biome slices need no neighbour comparisons (opt-in); same flags.
	 */
	@Overwrite
	private void calculateUniform() {
		Biome first = this.biomes[0].value();
		boolean single = true;
		for (Holder<Biome> h : this.biomes) {
			if (h.value() != first) {
				single = false;
				break;
			}
		}
		if (ChunkOpt.STATS) ChunkOpt.count(single ? "mesh.biomeSingle" : "mesh.biomeMixed");
		for (int cellX = 2; cellX < 10; cellX++) {
			for (int cellY = 2; cellY < 10; cellY++) {
				for (int cellZ = 2; cellZ < 10; cellZ++) {
					this.uniform[dataArrayIndex(cellX, cellY, cellZ)] = single || this.hasUniformNeighbors(cellX, cellY, cellZ);
				}
			}
		}
	}
}
