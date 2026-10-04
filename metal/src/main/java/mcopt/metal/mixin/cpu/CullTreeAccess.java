package mcopt.metal.mixin.cpu;

import net.caffeinemc.mods.sodium.client.render.chunk.tree.Tree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Tree.class, remap = false)
public interface CullTreeAccess {
	@Accessor("tree")
	long[] mcopt$bits();

	@Accessor("offsetX")
	int mcopt$offsetX();

	@Accessor("offsetY")
	int mcopt$offsetY();

	@Accessor("offsetZ")
	int mcopt$offsetZ();
}
