package mcopt.metal.chunk;

import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.Objects;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.PalettedContainerRO;

/**
 * clonesVerify: on every cache hit, the cached snapshot against a fresh clone of the same section: block states and biomes (by
 * their network form), both light layers (bytes), block entities (same positions, same objects). A difference is a stale hit,
 * a snapshot the mesher would use although the world changed and Sodium's per-position invalidation did not drop it.
 */
public final class CloneCheck {
	private CloneCheck() { }

	public static void compare(ClonedChunkSection cached, ClonedChunkSection fresh) {
		String what = null;
		if (!same(cached.getBlockData(), fresh.getBlockData())) what = "blocks";
		else if (!same(cached.getBiomeData(), fresh.getBiomeData())) what = "biomes";
		else if (!same(cached.getLightArray(LightLayer.BLOCK), fresh.getLightArray(LightLayer.BLOCK))) what = "blockLight";
		else if (!same(cached.getLightArray(LightLayer.SKY), fresh.getLightArray(LightLayer.SKY))) what = "skyLight";
		else if (!Objects.equals(cached.getBlockEntityMap(), fresh.getBlockEntityMap())) what = "blockEntities";
		ChunkOpt.count(what == null ? "clones.verify.same" : "clones.verify.STALE." + what);
	}

	private static boolean same(PalettedContainerRO<?> a, PalettedContainerRO<?> b) {
		if (a == null || b == null) return a == b;
		return Arrays.equals(bytes(a), bytes(b));
	}

	private static boolean same(DataLayer a, DataLayer b) {
		if (a == b) return true;
		if (a == null || b == null) return false;
		return Arrays.equals(a.getData(), b.getData());
	}

	private static byte[] bytes(PalettedContainerRO<?> c) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		c.write(buf);
		byte[] out = new byte[buf.readableBytes()];
		buf.readBytes(out);
		buf.release();
		return out;
	}
}
