/*
 * This file is copied or adapted from Sodium (https://github.com/CaffeineMC/sodium), Copyright JellySquid
 * (jellysquid3) and contributors. Unlike the rest of mcopt, it is licensed under the PolyForm Shield License 1.0.0
 * (LICENSES/PolyForm-Shield-1.0.0.md, https://polyformproject.org/licenses/shield/1.0.0), not Apache-2.0.
 * See NOTICE.
 */
package mcopt.metal.mixin.chunk;

import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import java.util.Objects;
import mcopt.metal.chunk.ChunkOpt;
import mcopt.metal.chunk.SliceArrays;
import net.caffeinemc.mods.sodium.client.services.SodiumModelDataContainer;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.caffeinemc.mods.sodium.client.world.SodiumAuxiliaryLightManager;
import net.caffeinemc.mods.sodium.client.world.cloned.ChunkRenderContext;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSection;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * C1 (-Dmcopt.chunk.mesh=air): for every mesh task Sodium's LevelSlice fills all 4096 entries of a slot with AIR for each empty
 * section in the 3x3x3 neighbourhood (8% of mesher CPU on the Neo), though the task reads at most a 2-block border of it. Here an
 * empty section's slot points at one shared AIR array (never written) instead; a slot that holds a non-empty section gets the
 * slice's own array back before Sodium's unpack writes into it. Reads (volume check, then slot[index]) see the same states.
 */
@Mixin(value = LevelSlice.class, remap = false)
abstract class LevelSliceAirMixin {
	@Shadow @Final private BlockState[][] blockArrays;
	@Shadow @Final private SodiumAuxiliaryLightManager[] auxLightManager;
	@Shadow @Final private DataLayer[][] lightArrays;
	@Shadow @Final private Int2ReferenceMap<BlockEntity>[] blockEntityArrays;
	@Shadow @Final private Int2ReferenceMap<Object>[] blockEntityRenderDataArrays;
	@Shadow @Final private SodiumModelDataContainer[] modelMapArrays;

	@Shadow
	private void unpackBlockData(BlockState[] blockArray, ChunkRenderContext context, ClonedChunkSection section) {
		throw new AssertionError();
	}

	/** The slice's own arrays, the only ones unpackBlockData may write into. */
	@Unique private BlockState[][] mcopt$own;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void mcopt$keepOwnArrays(ClientLevel level, CallbackInfo ci) {
		this.mcopt$own = this.blockArrays.clone();
	}

	/**
	 * @author mcopt
	 * @reason empty sections map to the shared AIR array instead of an AIR fill (opt-in); the rest is the original, line for line.
	 */
	@Overwrite
	private void copySectionData(ChunkRenderContext context, int sectionIndex) {
		ClonedChunkSection section = context.getSections()[sectionIndex];
		Objects.requireNonNull(section, "Chunk section must be non-null");
		if (section.getBlockData() == null) {
			this.blockArrays[sectionIndex] = SliceArrays.AIR;
			if (ChunkOpt.STATS) ChunkOpt.count("mesh.airSlots");
		} else {
			BlockState[] own = this.mcopt$own[sectionIndex];
			this.blockArrays[sectionIndex] = own;
			this.unpackBlockData(own, context, section);
		}
		this.lightArrays[sectionIndex][LightLayer.BLOCK.ordinal()] = section.getLightArray(LightLayer.BLOCK);
		this.lightArrays[sectionIndex][LightLayer.SKY.ordinal()] = section.getLightArray(LightLayer.SKY);
		this.blockEntityArrays[sectionIndex] = section.getBlockEntityMap();
		this.auxLightManager[sectionIndex] = section.getAuxLightManager();
		this.blockEntityRenderDataArrays[sectionIndex] = section.getBlockEntityRenderDataMap();
		this.modelMapArrays[sectionIndex] = section.getModelMap();
	}
}
