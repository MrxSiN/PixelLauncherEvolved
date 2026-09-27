package my.github.MrxSiN.pixellauncherevolved.core;

import android.os.Debug;
import android.util.Log;

import java.util.Arrays;

/** Instrumentation build only: times the search-result filter on the main thread. */
public final class SearchProbe {

    private static final int BATCH = 100;
    private static final long[] NANOS = new long[BATCH];
    private static final int[] ALLOCS = new int[BATCH];
    private static final int[] ITEMS = new int[BATCH];
    private static int count;
    private static boolean counting;
    private static long t0;
    private static int a0;

    private SearchProbe() {
    }

    public static void enter() {
        if (!counting) {
            Debug.startAllocCounting();
            counting = true;
        }
        a0 = Debug.getThreadAllocCount();
        t0 = System.nanoTime();
    }

    public static void exit(java.util.List<?> results) {
        long t1 = System.nanoTime();
        int a1 = Debug.getThreadAllocCount();
        NANOS[count] = t1 - t0;
        ALLOCS[count] = a1 - a0;
        ITEMS[count] = results == null ? 0 : results.size();
        if (++count == BATCH) flush();
    }

    private static void flush() {
        long[] sorted = NANOS.clone();
        Arrays.sort(sorted);
        long allocs = 0, items = 0, sum = 0;
        for (int i = 0; i < BATCH; i++) { allocs += ALLOCS[i]; items += ITEMS[i]; sum += NANOS[i]; }
        Log.i("PLEProbe", "search n=" + BATCH
                + " p50=" + sorted[BATCH / 2]
                + " p95=" + sorted[BATCH * 95 / 100]
                + " p99=" + sorted[BATCH * 99 / 100]
                + " mean=" + (sum / BATCH)
                + " allocs_per_call=" + ((double) allocs / BATCH)
                + " items_per_call=" + ((double) items / BATCH));
        count = 0;
    }
}
