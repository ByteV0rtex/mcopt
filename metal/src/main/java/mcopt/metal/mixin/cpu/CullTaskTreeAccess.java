package mcopt.metal.mixin.cpu;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.TaskCollectingTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = TaskCollectingTree.class, remap = false)
public interface CullTaskTreeAccess {
	@Accessor("pendingTasks")
	LongArrayList mcopt$pending();

	@Accessor("creationTime")
	long mcopt$creationTime();

	@Mutable
	@Accessor("creationTime")
	void mcopt$setCreationTime(long t);
}
