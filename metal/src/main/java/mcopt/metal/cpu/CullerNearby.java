package mcopt.metal.cpu;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;

/**
 * Implemented on Sodium's OcclusionCuller by a mixin: the camera's neighbour sections the last cull's graph search didn't reach.
 * Its addNearbySections adds those to every tree when they pass a (looser) frustum test, the one part of the regular and wide
 * trees that depends on the view direction. Written on the cull thread, read after the cull's future completed.
 */
public interface CullerNearby {
	RenderSection[] mcopt$nearby();

	int mcopt$nearbyCount();

	void mcopt$setNearby(RenderSection[] sections, int count);
}
