package mcopt.metal;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Opt-in, render-thread-only cadence recorder. No Java allocation on submit/present/retire.
 * Fixed capacity never wraps: overflow is reported rather than silently losing early phases.
 * Native presented callbacks write separate atomic slots and never call Java.
 */
public final class GpuTimes {
	private static final int CAPACITY = 1 << 20;
	private static final long[] submitNs = new long[CAPACITY], startNs = new long[CAPACITY], endNs = new long[CAPACITY];
	private static final boolean[] presenting = new boolean[CAPACITY];
	private static final long[] calibration = calibrate();
	private static int retired;
	private static long dropped;

	private GpuTimes() {}
	static void initialize() {} // initialize storage and clock mapping before recording

	/** Bracket the native clock with nanoTime, retaining the narrowest of 64 brackets.
	 * [Java midpoint - native ns, bracket width, Java midpoint]. Snapshot repeats this
	 * so reports can test both offset and drift instead of assuming identical origins.
	 */
	private static long[] calibrate() {
		long best = Long.MAX_VALUE, offset = 0, midpoint = 0;
		for (int i = 0; i < 64; i++) {
			long a = System.nanoTime();
			long host = Math.round(Native.hostSeconds() * 1e9);
			long b = System.nanoTime();
			if (b - a < best) { best = b - a; midpoint = a + best / 2; offset = midpoint - host; }
		}
		return new long[] {offset, best, midpoint};
	}

	static void submit(long index) {
		if (index < CAPACITY) submitNs[(int) index] = System.nanoTime();
	}
	static void present(long index, long drawable) {
		if (index >= CAPACITY) return;
		presenting[(int) index] = true;
		Native.cadencePresent(drawable, (int) index);
	}
	static void retire(long index, long cmd) {
		if (index >= CAPACITY) { dropped++; return; }
		int i = (int) index;
		double start = Native.cmdGpuStart(cmd), end = Native.cmdGpuEnd(cmd);
		startNs[i] = start > 0 ? Math.round(start * 1e9) + calibration[0] : 0;
		endNs[i] = end > 0 ? Math.round(end * 1e9) + calibration[0] : 0;
		retired = i + 1;
	}

	/** Called reflectively by the bench only at report time, not at phase boundaries.
	 * Phase membership uses GPU completion time. All submits are exported separately
	 * to permit cross-boundary/GC analysis; CPU frame membership remains unchanged.
	 */
	public static Map<String, Object> snapshot(Map<String, Map<String, Object>> phases) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("signal", "command-buffer GPU completion; handler timestamps are callback delivery, not scanout");
		out.put("clockCalibrationStart", calibration);
		out.put("clockCalibrationEnd", calibrate());
		out.put("capacity", CAPACITY);
		out.put("dropped", dropped);
		out.put("inFlight", Integer.getInteger("mcopt.metal.inFlight", 2));
		out.put("submitIndex", java.util.stream.LongStream.range(0, retired).toArray());
		out.put("submitNs", Arrays.copyOf(submitNs, retired));
		out.put("gpuStartNs", Arrays.copyOf(startNs, retired));
		out.put("gpuEndNs", Arrays.copyOf(endNs, retired));
		out.put("presenting", Arrays.copyOf(presenting, retired));
		long[] handlers = new long[retired], presented = new long[retired];
		for (int i = 0; i < retired; i++) {
			long h = Native.cadenceHandler(i), p = Native.cadencePresented(i);
			handlers[i] = h == 0 ? 0 : h + calibration[0];
			presented[i] = p == 0 ? 0 : p + calibration[0];
		}
		out.put("presentedHandlerNs", handlers);
		out.put("presentedTimeNs", presented);
		for (Map<String, Object> phase : phases.values()) {
			long[] range = (long[]) phase.get("nanoRange");
			if (range == null) continue;
			int count = 0;
			for (int i = 0; i < retired; i++)
				if (endNs[i] >= range[0] && endNs[i] < range[1]) count++;
			long[] indices = new long[count], submits = new long[count], starts = new long[count], allEnds = new long[count];
			long[] callback = new long[count], scanout = new long[count], ends = new long[count];
			boolean[] presents = new boolean[count];
			int n = 0, row = 0;
			for (int i = 0; i < retired; i++) {
				if (endNs[i] < range[0] || endNs[i] >= range[1]) continue;
				indices[row] = i;
				submits[row] = submitNs[i];
				starts[row] = startNs[i];
				allEnds[row] = endNs[i];
				presents[row] = presenting[i];
				callback[row] = handlers[i];
				scanout[row++] = presented[i];
				if (presenting[i]) ends[n++] = endNs[i];
			}
			Map<String, Object> gpuFrames = new LinkedHashMap<>();
			gpuFrames.put("submitIndex", indices);
			gpuFrames.put("submitNs", submits);
			gpuFrames.put("gpuStartNs", starts);
			gpuFrames.put("gpuEndNs", allEnds);
			gpuFrames.put("presenting", presents);
			gpuFrames.put("presentedHandlerNs", callback);
			gpuFrames.put("presentedTimeNs", scanout);
			phase.put("gpuFrames", gpuFrames);
			phase.put("gpuEndNs", Arrays.copyOf(ends, n)); // presenting submits only
			phase.put("gpuPresentingFrames", n);
			putStats(phase, "gpu", Arrays.copyOf(ends, n));
			putStats(phase, "gpuAll", allEnds);

		}
		return out;
	}

	private static void putStats(Map<String, Object> phase, String prefix, long[] ends) {
		if (ends.length < 2) return;
		long[] intervals = new long[ends.length - 1];
		for (int i = 1; i < ends.length; i++) intervals[i - 1] = ends[i] - ends[i - 1];
		phase.put(prefix + "AvgFps", intervals.length * 1e9 / (ends[ends.length - 1] - ends[0]));
		Arrays.sort(intervals);
		phase.put(prefix + "Low1Fps", low(intervals, .01));
		phase.put(prefix + "Low01Fps", low(intervals, .001));
	}

	private static double low(long[] sorted, double fraction) {
		int n = Math.max(1, (int) (sorted.length * fraction));
		long sum = 0;
		for (int i = sorted.length - n; i < sorted.length; i++) sum += sorted[i];
		return n * 1e9 / sum;
	}
}
