package mcopt.metal.mixin.cpu;

import java.util.Map;
import mcopt.metal.cpu.CullReuse;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.async.CullTask;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.CullType;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** -Dmcopt.cpu.cullReuse / cullStats: see CullReuse. Observes Sodium's cull scheduling; cancels only passes the rule proves redundant. */
@Mixin(value = RenderSectionManager.class, remap = false)
abstract class CullReuseManagerMixin {
	@Shadow private CullTask pendingTask;
	@Shadow private boolean cameraChanged;
	@Shadow private boolean needsGraphUpdate;
	@Shadow @Final private Map<CullType, SectionTree> cullResults;
	@Shadow @Final private OcclusionCuller occlusionCuller;
	@Shadow @Final private ClientLevel level;

	@Shadow
	private float getSearchDistanceForCullType(CullType cullType, FogParameters fogParameters) {
		throw new AssertionError();
	}

	@Shadow
	private void scheduleAsyncWork(Viewport viewport, FogParameters fogParameters, boolean useOcclusionCulling) {
		throw new AssertionError();
	}

	@Shadow
	private boolean isOutOfGraph(SectionPos pos) {
		throw new AssertionError();
	}

	@Unique private final CullReuse mcopt$reuse = new CullReuse();
	@Unique private boolean mcopt$hadPending;
	@Unique private CullTask mcopt$consuming;

	@Inject(method = "prepareRender", at = @At("HEAD"))
	private void mcopt$frame(CallbackInfo ci) {
		mcopt$reuse.frame();
	}

	@Inject(method = "markGraphDirty", at = @At("HEAD"))
	private void mcopt$graphDirty(CallbackInfo ci) {
		mcopt$reuse.graphDirty();
	}

	@Inject(method = "scheduleAsyncWork", at = @At("HEAD"), cancellable = true)
	private void mcopt$beforeSchedule(Viewport viewport, FogParameters fog, boolean occlusion, CallbackInfo ci) {
		mcopt$hadPending = pendingTask != null;
		if (mcopt$hadPending) {
			mcopt$reuse.coalesced();
			return;
		}
		float regular = getSearchDistanceForCullType(CullType.REGULAR, fog), local = getSearchDistanceForCullType(CullType.LOCAL, fog);
		if (mcopt$reuse.beforeSchedule(viewport, regular, local, occlusion, cameraChanged, needsGraphUpdate, cullResults,
			level.getMinSectionY(), level.getMaxSectionY())) ci.cancel();
	}

	@Inject(method = "scheduleAsyncWork", at = @At("TAIL"))
	private void mcopt$afterSchedule(Viewport viewport, FogParameters fog, boolean occlusion, CallbackInfo ci) {
		if (!mcopt$hadPending && pendingTask != null) mcopt$reuse.scheduled(pendingTask);
	}

	@Unique private CullTask mcopt$beforeCancel;

	@Inject(method = "prepareRenderTrees", at = @At("HEAD"))
	private void mcopt$beforeCancel(Viewport viewport, FogParameters fog, boolean occlusion, CallbackInfo ci) {
		mcopt$beforeCancel = pendingTask;
	}

	/** Right after Sodium's cancelIfNotStarted: a pending pass that vanished without being consumed was cancelled. */
	@Inject(method = "prepareRenderTrees", at = @At(value = "INVOKE",
		target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;consumeCullTaskResults(Z)V"))
	private void mcopt$afterCancel(Viewport viewport, FogParameters fog, boolean occlusion, CallbackInfo ci) {
		if (mcopt$beforeCancel != null && pendingTask == null) {
			boolean lost = !cameraChanged && !needsGraphUpdate;
			mcopt$reuse.cancelled(lost);
			if (mcopt.metal.cpu.Cpu.CULL_RECOVER) needsGraphUpdate = true;
		}
		mcopt$beforeCancel = null;
	}

	@Inject(method = "consumeCullTaskResults", at = @At("HEAD"))
	private void mcopt$beforeConsume(boolean wait, CallbackInfo ci) {
		mcopt$consuming = pendingTask;
	}

	@Inject(method = "consumeCullTaskResults", at = @At("TAIL"))
	private void mcopt$afterConsume(boolean wait, CallbackInfo ci) {
		CullTask task = mcopt$consuming;
		mcopt$consuming = null;
		if (task != null && pendingTask == null) mcopt$reuse.consumed(task, cullResults, occlusionCuller);
	}

	@Inject(method = "prepareRenderTrees", at = @At("TAIL"))
	private void mcopt$settle(Viewport viewport, FogParameters fog, boolean occlusion, CallbackInfo ci) {
		if (mcopt$reuse.wantsSettle(cameraChanged, needsGraphUpdate, pendingTask == null) && !isOutOfGraph(viewport.getChunkCoord())) {
			scheduleAsyncWork(viewport, fog, occlusion);
		}
	}

	@Inject(method = "processChunkBuilds", at = @At("HEAD"))
	private void mcopt$beginBuilds(CallbackInfo ci) {
		CullReuse.beginBuilds();
	}

	@Inject(method = "processChunkBuilds", at = @At("RETURN"))
	private void mcopt$endBuilds(CallbackInfo ci) {
		CullReuse.endBuilds();
	}

	@Inject(method = "destroy", at = @At("HEAD"))
	private void mcopt$destroy(CallbackInfo ci) {
		mcopt$reuse.printAll();
	}
}
