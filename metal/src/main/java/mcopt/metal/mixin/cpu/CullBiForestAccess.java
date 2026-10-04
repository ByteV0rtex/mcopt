package mcopt.metal.mixin.cpu;

import net.caffeinemc.mods.sodium.client.render.chunk.tree.BaseBiForest;
import net.caffeinemc.mods.sodium.client.render.chunk.tree.Tree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = BaseBiForest.class, remap = false)
public interface CullBiForestAccess {
	@Accessor("mainTree")
	Tree mcopt$main();

	@Accessor("secondaryTree")
	Tree mcopt$secondary();
}
