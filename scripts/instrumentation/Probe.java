package my.github.MrxSiN.pixellauncherevolved.core;

import android.os.Debug;
import android.util.Log;

import java.util.Arrays;
import java.util.function.Predicate;

/** Instrumentation build only: times each drawer predicate call on the main thread. */
public final class Probe {

    private static final int BATCH = 2000;
    private static final long[] NANOS = new long[BATCH];
    private static final int[] ALLOCS = new int[BATCH];
    private static int count;
    private static boolean counting;

    private Probe() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Predicate<Object> wrap(Predicate inner) {
        if (!counting) {
            Debug.startAllocCounting();
            counting = true;
        }
        return info -> {
            int a0 = Debug.getThreadAllocCount();
            long t0 = System.nanoTime();
            boolean result = inner.test(info);
            long t1 = System.nanoTime();
            int a1 = Debug.getThreadAllocCount();
            NANOS[count] = t1 - t0;
            ALLOCS[count] = a1 - a0;
            if (++count == BATCH) flush();
            return result;
        };
    }

    private static void flush() {
        long[] sorted = NANOS.clone();
        Arrays.sort(sorted);
        long allocs = 0;
        for (int a : ALLOCS) allocs += a;
        Log.i("PLEProbe", "drawer n=" + BATCH
                + " p50=" + sorted[BATCH / 2]
                + " p95=" + sorted[BATCH * 95 / 100]
                + " p99=" + sorted[BATCH * 99 / 100]
                + " mean=" + (Arrays.stream(sorted).sum() / BATCH)
                + " allocs_per_call=" + ((double) allocs / BATCH));
        count = 0;
    }
}
