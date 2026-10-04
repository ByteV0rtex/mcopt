package mcopt.metal.mixin.chunk;

import net.caffeinemc.mods.sodium.client.util.collections.BitArray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** C1 (mesh=vis): the visibility graph's opaque-block words, read by the bit-parallel sweep. */
@Mixin(value = BitArray.class, remap = false)
public interface BitArrayAccessor {
	@Accessor("words")
	long[] mcopt$words();
}
