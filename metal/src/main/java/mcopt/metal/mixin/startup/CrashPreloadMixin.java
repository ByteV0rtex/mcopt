package mcopt.metal.mixin.startup;

import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import net.minecraft.client.main.Main;
import net.minecraft.util.MemoryReserve;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * -Dmcopt.startup.crashPreload=async: CrashReport.preload() is MemoryReserve.allocate() plus a throw-away report that loads the
 * reporting classes and probes the hardware. The reserve stays on the main thread; the throw-away report moves to a daemon thread.
 */
@Mixin(Main.class)
abstract class CrashPreloadMixin {
	@Redirect(method = "main", at = @At(value = "INVOKE", target = "Lnet/minecraft/CrashReport;preload()V"))
	private static void mcopt$async() {
		MemoryReserve.allocate();
		Thread t = new Thread(() -> new CrashReport("Don't panic!", new Throwable()).getFriendlyReport(ReportType.CRASH), "mcopt crash-report preload");
		t.setDaemon(true);
		t.start();
	}
}
