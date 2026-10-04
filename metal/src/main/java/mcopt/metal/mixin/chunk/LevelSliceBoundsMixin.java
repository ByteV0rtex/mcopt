/*
 * This file is copied or adapted from Sodium (https://github.com/CaffeineMC/sodium), Copyright JellySquid
 * (jellysquid3) and contributors. Unlike the rest of mcopt, it is licensed under the PolyForm Shield License 1.0.0
 * (LICENSES/PolyForm-Shield-1.0.0.md, https://polyformproject.org/licenses/shield/1.0.0), not Apache-2.0.
 * See NOTICE.
 */
package mcopt.metal.mixin.chunk;

import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.caffeinemc.mods.sodium.client.world.cloned.ChunkRenderContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * C1 (-Dmcopt.chunk.mesh=bounds): LevelSlice.getBlockState, the mesher's hottest read, checks the task volume through the mutable
 * BoundingBox (six field loads, six branches; BoundingBox.isInside was 8.7% of mesher CPU on the Neo). The same box, captured
 * relative to the slice origin when the task's data is copied, is checked as one sign test over six differences. Same predicate
 * (inclusive bounds; coordinates are far inside int range), same array read.
 */
@Mixin(value = LevelSlice.class, remap = false)
abstract class LevelSliceBoundsMixin {
	@Unique private static final BlockState MCOPT_AIR = Blocks.AIR.defaultBlockState();
	@Shadow @Final private BlockState[][] blockArrays;
	@Shadow private int originBlockX;
	@Shadow private int originBlockY;
	@Shadow private int originBlockZ;
	@Shadow private BoundingBox volume;
	@Unique private int mcopt$minX, mcopt$maxX, mcopt$minY, mcopt$maxY, mcopt$minZ, mcopt$maxZ;

	@Inject(method = "copyData", at = @At("TAIL"))
	private void mcopt$relativeVolume(ChunkRenderContext context, CallbackInfo ci) {
		BoundingBox v = this.volume;
		this.mcopt$minX = v.minX() - this.originBlockX;
		this.mcopt$maxX = v.maxX() - this.originBlockX;
		this.mcopt$minY = v.minY() - this.originBlockY;
		this.mcopt$maxY = v.maxY() - this.originBlockY;
		this.mcopt$minZ = v.minZ() - this.originBlockZ;
		this.mcopt$maxZ = v.maxZ() - this.originBlockZ;
	}

	/**
	 * @author mcopt
	 * @reason one-branch volume check (opt-in); same result as volume.isInside then the same array read.
	 */
	@Overwrite
	public BlockState getBlockState(int blockX, int blockY, int blockZ) {
		int x = blockX - this.originBlockX, y = blockY - this.originBlockY, z = blockZ - this.originBlockZ;
		if ((x - this.mcopt$minX | this.mcopt$maxX - x | y - this.mcopt$minY | this.mcopt$maxY - y | z - this.mcopt$minZ | this.mcopt$maxZ - z) < 0) {
			return MCOPT_AIR;
		}
		return this.blockArrays[LevelSlice.getLocalSectionIndex(x >> 4, y >> 4, z >> 4)][LevelSlice.getLocalBlockIndex(x & 15, y & 15, z & 15)];
	}
}
