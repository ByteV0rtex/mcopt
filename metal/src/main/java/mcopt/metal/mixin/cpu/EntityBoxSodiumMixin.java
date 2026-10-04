package mcopt.metal.mixin.cpu;

import mcopt.metal.cpu.EntityBox;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** -Dmcopt.cpu.entityBox: records the culling box Sodium computes (see EntityBox). */
@Mixin(value = SodiumWorldRenderer.class, remap = false)
abstract class EntityBoxSodiumMixin {
	@Inject(method = "isEntityVisible", at = @At("HEAD"))
	private void mcopt$clear(EntityRenderer<?, ?> renderer, Entity entity, float partialTicks, CallbackInfoReturnable<Boolean> cir) {
		EntityBox.clear();
	}

	@ModifyVariable(method = "isEntityVisible", at = @At("STORE"), ordinal = 0)
	private AABB mcopt$record(AABB bb, EntityRenderer<?, ?> renderer, Entity entity, float partialTicks) {
		EntityBox.put(renderer, entity, partialTicks, bb);
		return bb;
	}
}
