# Performance

## Run 2026-09-26: hot-path pass vs v0.1.1

- **Baseline:** `031d5e5` release APK (v0.1.1), SHA-256 `f758c368…4e54356`.
- **Candidate:** working tree on top of `031d5e5` with the changes in `OPTIMIZATION_LEDGER.md`
  (entries 1–6), release build, same signing key.
- **Device:** Pixel 8 Pro (husky), `google/husky/husky:17/CP3A.260905.009/16091614:user/release-keys`,
  SDK 37, rooted, LSPosed. Pixel Launcher unchanged between runs. Refresh rate left at the device
  setting (peak Infinity, min 0). Battery, not charging; adb over Wi-Fi. Thermal status 0 in every block.
- **Module settings during the run:** bubble and split task-card buttons on, Overview action-row hiding
  off, no hidden apps, taskbar-only on, blur on, double tap to sleep on.

### Method

```bash
bash scripts/bench-device.sh baseline.apk candidate.apk out 4 60
python scripts/bench-summary.py out/results.csv
```

Four rounds. In each round both variants are installed in turn (odd rounds baseline first, even
rounds candidate first), the launcher is force-stopped and restarted, warmed up with 5 unmeasured
cycles of each workload, then measured:

- **overview:** 60 × (`KEYCODE_APP_SWITCH` from home, 1.5 s, `KEYCODE_HOME`, 1.0 s) with 8 recent tasks.
- **drawer:** 60 × (`pm suspend` / `pm unsuspend` of Calculator, 0.7 s each), which makes the launcher
  rebuild its app list through the drawer predicate.

Per workload: launcher main-thread CPU (`/proc/<pid>/task/<pid>/schedstat`), ART cumulative bytes
allocated, GC count and GC time (SIGQUIT dump, taken before and after; bytes allocated is only printed
to the nearest MB), and `dumpsys gfxinfo` for the launcher (reset before each workload). The release
APKs themselves carry no instrumentation.

### Results (n = 4 per variant; samples in round order)

## drawer
| metric | variant | n | samples | median | IQR | MAD | change |
|---|---|---|---|---|---|---|---|
| alloc_mb | baseline | 4 | 12 16 15 15 | 15 | 14.25..15.25 | 0.5 |  |
| alloc_mb | candidate | 4 | 12 11 16 13 | 12.5 | 11.75..13.75 | 1 | -16.7% |
| gc_count | baseline | 4 | 2 2 2 2 | 2 | 2..2 | 0 |  |
| gc_count | candidate | 4 | 2 2 2 2 | 2 | 2..2 | 0 | +0.0% |
| gc_ms | baseline | 4 | 94 71 110 92 | 93 | 86.75..98 | 9 |  |
| gc_ms | candidate | 4 | 98 89 92 75 | 90.5 | 85.5..93.5 | 4.5 | -2.7% |
| main_cpu_ns | baseline | 4 | 9.92594e+08 1.25062e+09 5.88109e+08 6.20759e+08 | 8.06677e+08 | 6.12597e+08..1.0571e+09 | 2.02243e+08 |  |
| main_cpu_ns | candidate | 4 | 9.41283e+08 9.8769e+08 1.29494e+09 1.07991e+09 | 1.0338e+09 | 9.76088e+08..1.13367e+09 | 6.93151e+07 | +28.2% |
| janky | baseline | 4 | 0 0 1 0 | 0 | 0..0.25 | 0 |  |
| janky | candidate | 4 | 0 0 4 0 | 0 | 0..1 | 0 |  |

## overview
| metric | variant | n | samples | median | IQR | MAD | change |
|---|---|---|---|---|---|---|---|
| alloc_mb | baseline | 4 | 181 180 179 179 | 179.5 | 179..180.25 | 0.5 |  |
| alloc_mb | candidate | 4 | 169 167 168 168 | 168 | 167.75..168.25 | 0.5 | -6.4% |
| gc_count | baseline | 4 | 31 30 31 31 | 31 | 30.75..31 | 0 |  |
| gc_count | candidate | 4 | 32 30 31 30 | 30.5 | 30..31.25 | 0.5 | -1.6% |
| gc_ms | baseline | 4 | 1418.76 1404.87 1277.55 1322.51 | 1363.69 | 1311.27..1408.34 | 48.125 |  |
| gc_ms | candidate | 4 | 1428.82 1371.56 1421.03 1242.87 | 1396.3 | 1339.39..1422.98 | 28.63 | +2.4% |
| main_cpu_ns | baseline | 4 | 1.96014e+10 1.97168e+10 1.97603e+10 2.00928e+10 | 1.97386e+10 | 1.9688e+10..1.98435e+10 | 7.94517e+07 |  |
| main_cpu_ns | candidate | 4 | 1.05715e+10 1.09075e+10 1.0751e+10 1.10099e+10 | 1.08292e+10 | 1.07061e+10..1.09331e+10 | 1.29454e+08 | -45.1% |
| janky | baseline | 4 | 52 63 56 47 | 54 | 50.75..57.75 | 4.5 |  |
| janky | candidate | 4 | 13 14 16 16 | 15 | 13.75..16 | 1 | -72.2% |
| p50_ms | baseline | 4 | 5 5 5 5 | 5 | 5..5 | 0 |  |
| p50_ms | candidate | 4 | 5 5 5 5 | 5 | 5..5 | 0 | +0.0% |
| p90_ms | baseline | 4 | 9 8 9 7 | 8.5 | 7.75..9 | 0.5 |  |
| p90_ms | candidate | 4 | 7 7 8 7 | 7 | 7..7.25 | 0 | -17.6% |
| p95_ms | baseline | 4 | 9 9 9 9 | 9 | 9..9 | 0 |  |
| p95_ms | candidate | 4 | 8 9 9 9 | 9 | 8.75..9 | 0 | +0.0% |
| p99_ms | baseline | 4 | 15 15 15 14 | 15 | 14.75..15 | 0 |  |
| p99_ms | candidate | 4 | 11 11 11 11 | 11 | 11..11 | 0 | -26.7% |

### Interpretation

- **Overview: clear improvement.** Launcher main-thread CPU across 60 open/close cycles dropped from a
  median 19.74 s to 10.83 s (−45.1%, about 150 ms less per cycle). The ranges do not overlap:
  baseline 19.60–20.09 s, candidate 10.57–11.01 s. Janky frames fell from 54 to 15 (−72%), and p90
  and p99 frame time dropped from 8.5/15 ms to 7/11 ms. p50 and p95 did not change. Allocation fell
  about 11.5 MB per 60 cycles (−6.4%, MB resolution); GC count and GC time did not move measurably.
  The likely main cause is entry 5: the baseline looked up a resource name (`getIdentifier("icon")`)
  for every card, for each of the two card buttons, on every launcher frame. This run does not break
  the gain down per change.
- **Drawer: inconclusive.** Main-thread CPU is dominated by the launcher's own package-change work and
  varies with how many frames happen to be drawn (0–150 per run). The medians (baseline 0.81 s,
  candidate 1.03 s) sit inside each other's spread, and allocation differences are at the 1 MB
  resolution limit. A per-item measurement of the predicate needs a trace (Perfetto/simpleperf); this
  workload cannot show it.
- **Not measured:** double-tap-to-sleep latency and thread count, the Overview action-row pre-draw with
  a button hidden (that setting was off), search-result filtering.

### Device state after the run

The v0.1.1 baseline APK is the last one installed (the device had v0.1.0 before). Stay-awake is
restored to off, and Calculator is unsuspended.

## Run 2026-09-26: drawer predicate, instrumentation build

The drawer workload above cannot resolve a single predicate call, so this run uses a separate
**instrumentation build** of each variant. It is never used for release numbers.

- **Probe:** `scripts/instrumentation/Probe.java`, copied into `core/` of both trees. The only source
  change is wrapping the predicate at its one call site in `HiddenAppsFeature`:
  `Probe.wrap(shown(theirs, store, packageOf, context))`. The wrapper times each call with
  `System.nanoTime()`, counts that thread's allocations with `Debug.getThreadAllocCount()` (alloc
  counting on), and logs p50/p95/p99/mean and allocations per call for every 2,000 calls. Baseline
  and candidate carry the same instrumentation, and it adds the same overhead to both.
- **Trees:** baseline `031d5e5` and the candidate working tree, each plus the probe, release build,
  same signing key.
- **Run:** `scripts/bench-probe.sh probe-results.txt 4 60`. 4 rounds in alternating order; each block
  installs one variant, restarts the launcher, and makes 60 suspend/unsuspend cycles of Calculator.
  Device and settings as above (no hidden apps, so every call reads and checks an empty set).
- **Samples:** 36 batches of 2,000 calls per variant (72,000 calls each).

Per-call figures in µs: the median across batches of each batch statistic, with the IQR across batches.

| statistic | baseline | candidate | change |
|---|---|---|---|
| p50 | 6.00 (4.11–7.69) | 3.44 (2.96–4.96) | −42.7% |
| p95 | 11.94 (11.38–13.82) | 7.77 (7.08–9.28) | −34.9% |
| p99 | 56.82 (52.53–62.50) | 13.73 (10.27–17.08) | −75.8% |
| mean | 7.48 (6.62–8.87) | 5.06 (4.36–5.66) | −32.4% |
| allocations / call | 4.0 (every batch) | 0.0 (every batch) | −100% |

Batch p50 by round, in ns (the raw per-batch lines are in the run output):

```
1 base 8341 8789 7609 7243 4557 8504 7121 4517 8138
1 cand 3784 4435 4842 2930 3051 5737 3011 5738 3336
2 base 5208 7447 6388 8301 7690 3988 7609 7691 3865
2 cand 3418 3458 5981 5859 3377 6836 5086 2970 6714
3 base 5412 4639 3906 3866 3947 3906 3906 3784 3784
3 cand 3296 3337 2849 2808 2767 2767 2767 2808 2767
4 base 6551 9724 8626 4435 4476 8422 7325 4150 5615
4 cand 4476 4028 3296 4883 2523 4924 4598 5574 5534
```

The remaining ~3.4 µs per call is the launcher's own predicate, the reflective
`getTargetPackage()` call, the preference lock and lookup, and the set lookup. The p99 tail drops the
most, which fits the removal of the per-call parsing allocations (a `split` list, a filtered list, an
iterator and a set). These are instrumentation numbers; they show relative change, not release latency.

## Run 2026-09-26/27: on-demand tick hook (rejected)

A/B between the entry 1–7 build (labelled `baseline`) and the same build with the hide-apps tick
hook installed only while choosing (labelled `candidate`). Command:
`WORKLOADS="home overview" bash scripts/bench-device.sh prev.apk next.apk out 4 40`. The `home`
workload per cycle: swipe to the next home page and back, swipe up to the drawer, fling it, then Home.
Thermal status reached 1 in the last round. Same device and settings as above.

## home
| metric | variant | n | samples | median | IQR | MAD | change |
|---|---|---|---|---|---|---|---|
| alloc_mb | baseline | 4 | 93 80 81 79 | 80.5 | 79.75..84 | 1 |  |
| alloc_mb | candidate | 4 | 89 87 80 80 | 83.5 | 80..87.5 | 3.5 | +3.7% |
| gc_count | baseline | 4 | 10 9 9 8 | 9 | 8.75..9.25 | 0.5 |  |
| gc_count | candidate | 4 | 11 9 9 8 | 9 | 8.75..9.5 | 0.5 | +0.0% |
| gc_ms | baseline | 4 | 721.93 956.804 1041.66 993.877 | 975.341 | 898.085..1005.82 | 42.428 |  |
| gc_ms | candidate | 4 | 798.403 1387.79 940.022 804.191 | 872.107 | 802.744..1051.96 | 70.8095 | -10.6% |
| main_cpu_ns | baseline | 4 | 1.29332e+10 1.51621e+10 1.48498e+10 1.53752e+10 | 1.5006e+10 | 1.43707e+10..1.52154e+10 | 2.62675e+08 |  |
| main_cpu_ns | candidate | 4 | 1.33832e+10 1.47575e+10 1.51242e+10 1.53718e+10 | 1.49408e+10 | 1.44139e+10..1.51861e+10 | 3.07117e+08 | -0.4% |
| janky | baseline | 4 | 71 118 115 122 | 116.5 | 104..119 | 3.5 |  |
| janky | candidate | 4 | 62 145 132 121 | 126.5 | 106.25..135.25 | 12 | +8.6% |
| p50_ms | baseline | 4 | 5 5 5 5 | 5 | 5..5 | 0 |  |
| p50_ms | candidate | 4 | 5 5 5 5 | 5 | 5..5 | 0 | +0.0% |
| p90_ms | baseline | 4 | 6 6 6 7 | 6 | 6..6.25 | 0 |  |
| p90_ms | candidate | 4 | 7 6 7 7 | 7 | 6.75..7 | 0 | +16.7% |
| p95_ms | baseline | 4 | 7 7 7 8 | 7 | 7..7.25 | 0 |  |
| p95_ms | candidate | 4 | 8 7 8 8 | 8 | 7.75..8 | 0 | +14.3% |
| p99_ms | baseline | 4 | 10 14 14 14 | 14 | 13..14 | 0 |  |
| p99_ms | candidate | 4 | 11 16 15 15 | 15 | 14..15.25 | 0.5 | +7.1% |

## overview
| metric | variant | n | samples | median | IQR | MAD | change |
|---|---|---|---|---|---|---|---|
| alloc_mb | baseline | 4 | 116 112 111 110 | 111.5 | 110.75..113 | 1 |  |
| alloc_mb | candidate | 4 | 115 110 111 110 | 110.5 | 110..112 | 0.5 | -0.9% |
| gc_count | baseline | 4 | 21 21 20 19 | 20.5 | 19.75..21 | 0.5 |  |
| gc_count | candidate | 4 | 20 20 20 20 | 20 | 20..20 | 0 | -2.4% |
| gc_ms | baseline | 4 | 976 1536 1482 1405 | 1443.5 | 1297.75..1495.5 | 65.5 |  |
| gc_ms | candidate | 4 | 904 1508 1532 1487 | 1497.5 | 1341.25..1514 | 22.5 | +3.7% |
| main_cpu_ns | baseline | 4 | 7.24629e+09 9.73816e+09 9.73364e+09 1.03399e+10 | 9.7359e+09 | 9.1118e+09..9.88859e+09 | 3.03129e+08 |  |
| main_cpu_ns | candidate | 4 | 7.47103e+09 9.73529e+09 9.96115e+09 1.00503e+10 | 9.84822e+09 | 9.16922e+09..9.98344e+09 | 1.57506e+08 | +1.2% |
| janky | baseline | 4 | 13 39 41 53 | 40 | 32.5..44 | 7 |  |
| janky | candidate | 4 | 13 39 41 47 | 40 | 32.5..42.5 | 4 | +0.0% |
| p50_ms | baseline | 4 | 5 5 5 5 | 5 | 5..5 | 0 |  |
| p50_ms | candidate | 4 | 5 5 5 5 | 5 | 5..5 | 0 | +0.0% |
| p90_ms | baseline | 4 | 7 9 9 9 | 9 | 8.5..9 | 0 |  |
| p90_ms | candidate | 4 | 8 9 8 7 | 8 | 7.75..8.25 | 0.5 | -11.1% |
| p95_ms | baseline | 4 | 9 9 9 9 | 9 | 9..9 | 0 |  |
| p95_ms | candidate | 4 | 9 9 9 9 | 9 | 9..9 | 0 | +0.0% |
| p99_ms | baseline | 4 | 11 15 14 15 | 14.5 | 13.25..15 | 0.5 |  |
| p99_ms | candidate | 4 | 11 14 15 14 | 14 | 13.25..14.25 | 0.5 | -3.4% |

No metric moves beyond run-to-run spread, so the change was reverted (ledger entry 8). Overview
numbers from this run are not comparable to the first run: fewer cycles, a warmer device and a lower
battery.

## Run 2026-09-27: drawer search filter, instrumentation builds

Probe: `scripts/instrumentation/SearchProbe.java`, placed in `core/` of each tree. It is called
around the one `keep(...)` call in `AppDrawerSearchFeature`'s `setSearchResults` hook, timing each
call on the main thread with thread allocation counting on. It logs p50/p95/p99/mean,
allocations per call and results per call for every 100 calls. Workload
(`scripts/bench-search-probe.sh`): open the drawer, focus the search field, then per cycle type a
three-letter query (`cal`, `set`, `pho`, … rotating) with `input text`, wait 1.2 s, delete it,
wait 0.6 s. Settings as on the device: Play Store and Search in Apps results hidden, web results
shown, no hidden apps. The phone was charging during these runs. Summaries come from
`scripts/bench-probe-summary.py`. Batch p50 values are in µs below. The raw lines are in the run
output (`search-results.txt`, `search-cd.txt`, `search-ce.txt`).

| comparison | rounds × queries | batches | p50 | mean | allocations / call | result |
|---|---|---|---|---|---|---|
| baseline `031d5e5` → entries 1–7 | 4 × 60 | 11 / 12 | 126.3 → 110.9 µs (−12.2%, IQRs overlap) | −8.5% | 76.6 → 51.0 (−33.4%) | allocations: kept |
| entries 1–7 → + MethodHandle result type + lazy reads (entries 9+10) | 6 × 80 | 21 / 23 | 101.0 → 346.6 µs (+243%) | +229% | 51.2 → 304.1 (+494%) | rejected |
| entries 1–7 → + lazy reads only (entry 10) | 6 × 80 | 22 / 22 | 101.2 → 97.9 µs (−3.2%, IQRs overlap) | −10.7% | 50.7 → 49.2 (−3.0%) | rejected (noise) |

Batch p50 (µs) for the rejected MethodHandle build (d) against entries 1–7 (c), by round:

```
1 c 130.7 101.0  94.2  92.8     1 d 366.1 370.4 328.0 306.1
2 c 133.9 102.2  97.3           2 d 382.4 335.4 310.8 324.4
3 c 147.3  95.9  93.4  92.9     3 d 422.3 346.6 357.8
4 c 136.2 100.5  96.7           4 d 392.8 352.7 332.6 313.9
5 c 143.3  99.2  94.5           5 d 385.9 380.0 333.4 314.5
6 c 132.5 106.1 107.4 104.9     6 d 442.8 350.7 332.4 303.8
```

At ~100 µs for about 23 results per keystroke, the filter costs about 4 µs per result. At this
point most of that was three reflective getter calls and one `Field.get` per result. The next run
below replaces the getter calls with direct calls.

## Run 2026-09-27: double tap to sleep, instrumentation builds

Probe: in `ScreenOff.off` of both trees, `System.nanoTime()` is read on the gesture thread before
the work is handed off, again as the worker starts, and again after `ContentResolver.call` returns.
The worker logs `sleep handoff=<ns> call=<ns> thread=<name>`. Run with
`scripts/bench-sleep-probe.sh sleep-results.txt 2 12`: 2 rounds in alternating order, 12 double
taps per block on an empty home-screen spot. The script waits until the phone is awake, unlocked
and showing the launcher, and the owner unlocked it after every tap. No tap was missed (48/48).

Handoff in ms, by block (first value = first tap in that launcher process):

```
1 b 1.62 1.10 0.90 1.33 1.28 1.07 1.42 1.39 2.75 1.37 1.69 0.71
2 b 1.63 1.54 1.65 1.90 1.03 1.49 0.88 2.96 1.03 1.20 1.20 1.00
1 c 2.77 0.18 0.27 0.32 0.19 0.18 0.18 0.21 0.16 0.08 0.24 0.29
2 c 2.45 0.21 0.28 0.17 0.22 0.30 0.25 0.21 0.25 0.22 0.33 0.28
```

| | baseline (new `Thread` per tap) | candidate (pooled worker) | change |
|---|---|---|---|
| handoff, taps 2–12 (n = 22) | median 1.30 ms, IQR 1.04–1.53 | median 0.22 ms, IQR 0.18–0.28 | −83% |
| handoff, first tap in process | 1.62, 1.63 ms | 2.77, 2.45 ms | +~1 ms once |
| provider call (n = 24) | median 223.4 ms, IQR 219.6–231.9 | median 224.8 ms, IQR 217.8–232.4 | none |

The measured candidate let its thread exit after 60 s idle, which the gesture's normal spacing would
hit. The shipped version keeps one core thread, so the taps 2–12 row is its steady state. On device
the thread was still parked 75 s after a tap. End to end, the provider's root call dominates: the
saving is about 1 ms of about 225 ms.

## Run 2026-09-27: direct `SearchTarget` calls, instrumentation builds

Same probe and workload as the search runs above. A = v0.1.2 (`bc3163c`) + `SearchProbe`, B = A +
ledger entry 11 + `SearchProbe`, both release builds with the same signing key. Command:
`A=a B=b bash scripts/bench-search-probe.sh search-ab.txt 6 80`, then
`python scripts/bench-probe-summary.py search-ab.txt a b`. Phone on battery (74%), thermal status 0,
settings as above (Play Store and Search in Apps results hidden).

| statistic (per filter call) | A (reflection) | B (direct) | change |
|---|---|---|---|
| p50, µs | 100.2 (IQR 96.9–114.2) | 82.4 (78.5–104.4) | −17.8% |
| mean, µs | 115.2 (108.4–133.2) | 97.6 (94.3–114.4) | −15.3% |
| p95, µs | 202.4 (172.0–222.5) | 181.8 (157.8–202.9) | −10.2% |
| p99, µs | 472.7 (374.9–813.2) | 453.3 (343.6–554.1) | −4.1% |
| allocations | 52.3 (51.9–52.8) | 34.4 (34.2–34.7) | −34.3% |
| results | 23.7 | 23.8 | |

n = 19 batches of 100 calls per variant. Batch p50 by round, in µs (the first batch of each block
includes warm-up):

```
1 a 111.5  93.5 107.7         1 b 107.5  83.0  75.9
2 a 121.6  98.6  98.5         2 b 103.8  82.8  80.2
3 a 116.9  92.1 100.2         3 b 104.9  82.4  79.9  78.7
4 a 122.7  97.8 100.6  89.1   4 b 106.2  89.7  77.8
5 a 119.0  97.5  96.2         5 b 105.3  77.7  76.7
6 a 120.8  94.9 101.7         6 b 105.1  81.1  78.2
```

B beats A at every batch position in every round. Removing the three reflective calls per result
saves about 18 µs and 18 allocations per keystroke. The allocations that remain are the result
batch's own list and the one `SearchResult` per result.

## Run 2026-09-27: module install at launcher start, instrumentation builds

`scripts/instrumentation/start-probe.patch` logs `System.nanoTime()` around each phase of
`installLauncher` and around each feature install (contract analysis forced first so it is timed on
its own). Run with `bash scripts/bench-start-probe.sh start-ab.txt 6 6` (start-a/start-b APKs in the
current directory): 6 rounds in alternating order, 6 launcher restarts (`am force-stop` then Home)
per block, first restart after each install dropped. Phone on battery, thermal 0.

Baseline (`831da0a`), ms, n = 30:

| phase | median | IQR |
|---|---|---|
| total (`installLauncher`) | 50.12 | 48.99–51.29 |
| features (registry loop) | 43.55 | 42.36–44.81 |
| contract analysis | 6.25 | 6.05–6.51 |
| focus_home_screens | 5.75 | 5.52–6.06 |
| crash guard | 3.89 | 3.84–4.03 |
| launcher_settings | 3.84 | 3.73–3.93 |
| taskbar_home_visibility | 3.27 | 3.19–3.34 |
| status_bar_double_tap_to_sleep | 2.90 | 2.07–3.58 |
| taskbar_transition | 2.29 | 2.25–2.37 |
| home_blur_wallpaper | 2.11 | 2.03–2.20 |
| taskbar_only | 1.86 | 1.79–1.90 |
| restarter | 1.45 | 1.40–1.52 |
| overview_bubble_button | 1.39 | 1.34–1.47 |
| app_drawer_hide_apps_picker | 1.35 | 1.32–1.41 |
| settings migration | 1.14 | 1.10–1.23 |
| every other feature | ≤ 1.06 each | |

Candidate B (ledger entries 12 + 13): total 51.45 ms (+2.7%, IQRs overlap). Analysis −0.53 ms in
every round, blur install +0.45 ms in every round (ledger entry 12). Per-round total medians (a/b):
51.3/51.4, 48.7/50.7, 50.2/51.2, 49.8/51.5, 50.9/51.7, 49.0/51.6.

## Run 2026-09-27: one hook per method (rejected)

`WORKLOADS=overview bash scripts/bench-device.sh rel-a.apk rel-b.apk fuse 4 60`, A = `831da0a`,
B = A + ledger entry 13, release builds.

| metric | A samples | B samples | median change |
|---|---|---|---|
| main_cpu_ns | 11.24 11.57 11.68 11.46 s | 11.09 11.35 11.47 11.59 s | −0.9% |
| janky | 12 14 21 17 | 17 13 11 12 | −19.4% |
| p99_ms | 10 10 11 11 | 10 10 10 10 | −4.8% |
| alloc_mb | 156 154 154 155 | 153 155 154 154 | −0.3% |
| gc_ms | 1449 1648 1450 1512 | 1409 1455 1434 1465 | −2.5% |

Every metric's ranges overlap. Rejected.

## Run 2026-09-27: icon pack index at launcher start

Pixel 8 Pro, launcher 907, release build, Simply Minimal Icons (24,169 components, 326 calendars)
chosen, cached index present. Measured with `SystemClock.elapsedRealtime()` around the start-path
read and publish (the log line `Icons: <pack> published from its index in N ms`, launcher main
thread). Each sample is a fresh launcher process (`am force-stop`, then HOME).

| Variant | Samples (ms) | Median |
|---|---|---|
| `DataInputStream` over `FileInputStream`, one `readLong`/`readInt` per value | 97, 98, 100, 101, 98 | 98 |
| one `read` into a `byte[]`, `ByteBuffer.asLongBuffer().get(long[])` | 10, 9, 8, 7, 9, 9 | 9 |

n = 5 and 6, run one variant after the other rather than interleaved; the difference (about 10x) is
far outside the spread. The rest of the 9 ms is the package manager calls for the pack's version and
resources.

Compiling the index (first use of a pack, or after it updates) took 1,014 ms on the `ple-icons`
worker for the same pack (n = 1).

With the source set to System, the three icon hooks are the launcher's own call, one volatile read
and a return. Their cost was not measured separately; `getStateForApp` and `getIcon` run once per app
per model load or icon miss, not per frame.

## Run 2026-09-27: icon pack apply, end to end

Pixel 8 Pro, launcher 907. Logged by the module: the request, the published source, and the
completion of `LauncherModel.forceReload`'s `CompletionStage`.

| Path | Published after | Reloaded after |
|---|---|---|
| Home settings dialog, Simply Minimal (index cached) | 7 ms | 426 ms |
| Wallpaper & style tile, Monoic (index cached) | 4 ms | 532 ms |

A screen recording of the first case shows the home screen appearing (after the settings activity
closes) already with the new icons. The case the report measured over a second was a pack chosen
for the first time: its index compile (1,014 ms for 24,169 components) ran before the reload. Packs
are now indexed when installed or updated and when the picker lists them.

## Run 2026-09-27: app drawer open/close with Blur wallpaper

`adb shell screenrecord`, one swipe up and one swipe down, frames read with OpenCV. With the tweak
on, before the fix, the workspace was fully blurred on the first frame of the swipe (Laplacian
variance of the top region 59 to 0.2 in one frame) and snapped sharp on the last frame of closing.
With the tweak off, and with it on after the fix, the workspace blur ramps over the first 5 to 8
frames of opening and the last 5 to 8 of closing.

## Run 2026-10-01: Grid & size, single samples

No hook on a frame, layout, touch, draw or scroll path (see `DEX_AUDIT.md`), so no frame benchmark
was run. From launcher log timestamps, one sample each: `migrateGrid` start to the module's item
check done 40 ms (4x6 → 5x6, 43 rows); the preview redraw is scheduled 1.2 s after a change. Not a
statistical result.
