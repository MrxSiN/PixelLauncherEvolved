# Optimization ledger

Baseline: `031d5e5` (v0.1.1), release APK archived under `.artifacts/baseline-031d5e5/`
(SHA-256 `f758c368393300e523fb8e847e47a4f1bb7c508581a0505a6ce3d0e294e54356`, not checked in).

Build: AGP release, R8 full mode, `proguard-android-optimize.txt`. Tests: 114 existing unit tests
pass on baseline and candidate; `scripts/check-project.sh` passes on both.

DEX evidence comes from `dexdump -d` and `scripts/dex-audit.py` against the R8 mapping. Device
measurements (Pixel 8 Pro, interleaved, n = 4, raw samples) are in `PERFORMANCE.md`. They cover
entries 1–6 together as one candidate, not one change at a time.

| Artifact | Baseline | Candidate | Change |
|---|---|---|---|
| APK | 381,716 B | 380,296 B | −1,420 B (−0.37%) |
| classes.dex | 328,700 B | 327,280 B | −1,420 B (−0.43%) |

## 1. Hidden-app drawer predicate parsed the preference per app

- **Hypothesis:** `SharedPreferencesHiddenAppsStore.hidden()` ran `split` + `filter` + `toSet` on
  every predicate call, i.e. once per app on every drawer rebuild. Parsing only when the stored
  text changes removes that work.
- **Path:** `feature/apps/HiddenAppsStore.kt`.
- **Mechanism:** cache `(raw text, parsed set)` in one volatile holder; hit when the string read
  from the preference is the same instance or equal. The preference is still read on every call,
  so a write from anywhere (settings row, picker, backup import, reset) is seen on the next call
  exactly as before.
- **Correctness:** same set contents for every input; callers only read the set (`HideAppsSelection.begin`
  copies it). New `HiddenAppsStoreTest` (parse, cache hit on identical and equal text, change seen
  after `hide`).
- **DEX, per drawer item:** baseline allocated a `String[]`, an `ArrayList` from `split`, a filtered
  `ArrayList`, an iterator and a `LinkedHashSet` plus one `String` per hidden package. Candidate on a
  hit: no allocation (preference lock + map lookup + reference compare).
- **Measured (instrumentation build, with entry 2, 72,000 calls per variant):** per call p50 6.00 to
  3.44 µs (−42.7%), p95 −34.9%, p99 56.8 to 13.7 µs (−75.8%), allocations 4 to 0. The release-build
  drawer workload was too noisy to show it.
- **Result:** accepted (measured).

## 2. Reflective `getTargetPackage()` allocated an argument array per item

- **Hypothesis:** Kotlin passes `new Object[0]` to every zero-argument `Method.invoke`, and a copy
  (`Arrays.copyOf`) when an array is spread. On per-item paths that is one allocation per item.
- **Path:** new `core/Invoke.java` (`Invoke.noArgs`, shared empty array); used in the hidden-apps
  predicate, the hide-apps picker, search result reading (`SearchTargets`, three calls per result),
  taskbar icon layout width, Overview-only chip/device-profile reads, bubble target resolution.
- **Correctness:** identical call, identical exceptions (`InvocationTargetException`,
  `IllegalAccessException`) caught by the same `runCatching` blocks.
- **DEX:** hidden-apps predicate `Arrays.copyOf` / `new-array` gone (verified in `x5.test`).
- **Measured:** included in entry 1's instrumentation result (the predicate's reflective call).
- **Result:** accepted (static). Java is the locked runtime language, so this is the preferred form.

## 3. Overview action row re-derived state on every pre-draw

- **Hypothesis:** the pre-draw listener on the Overview action row (runs every launcher frame while
  the row is shown) built a `List<ActionButton>`, two `HashSet<Integer>` with boxed ids, an iterator
  and a walk lambda each frame.
- **Path:** `feature/overview/OverviewActionsFeature.kt`.
- **Mechanism:** two cached primitive resource ids (resolved once, as before via the same
  `getOrPut` rule: `NO_ID`/0 means "this launcher has none"); two booleans read from settings;
  recursive walk with primitive parameters returning the accumulated `changed` flag.
- **Correctness:** same visit order, same stop-at-button rule, same `changed` accumulation
  (`changed || visibility != original` on restore, `true` on hide), same `originalVisibility`
  bookkeeping. `isEnabled` answers the same `A || B`.
- **DEX:** per-frame `new-instance` / `Integer.valueOf` / iterator sites removed; the only remaining
  boxing is `Integer.valueOf` inside `putIfAbsent`, reached only on a frame where a button is
  actually hidden.
- **Measured:** part of the Overview result under entry 5 (the row was on screen in every cycle).
  Not measured on its own.
- **Result:** accepted (measured together with 5).

## 4. Overview button entrance allocated two lists per animation frame

- **Path:** `feature/overview/OverviewActionsEntrance.kt`.
- **Mechanism:** count visible children, then place them in one indexed pass; `settle` walks by
  index. The clock-driven animator reads `animatedFraction` instead of boxed `animatedValue`.
- **Correctness:** visibility is not changed during placement, so the two passes see the same
  children the old snapshot did. For `ofFloat(0f, 1f)` with `LinearInterpolator` the value equals the
  fraction bit-for-bit (`0 + f * (1 - 0)`).
- **DEX:** `ArrayList` + iterator allocations gone from `applyTo`/`settle`; no `Float.valueOf` per
  animator frame.
- **Measured:** part of the Overview result under entry 5 (the row was on screen in every cycle).
  Not measured on its own.
- **Result:** accepted (measured together with 5).

## 5. Task card buttons (bubble, split) resolved a resource name per card per frame

- **Hypothesis:** each card's pre-draw listener called `findButton` (built a `List` of all children)
  and `chipOf`, which constructed `LauncherResources` and called `Resources.getIdentifier("icon")`
  (a name lookup through the asset manager) — for every card, for each of two decorators, on every
  frame of the launcher window.
- **Path:** `feature/overview/card/TaskCardButtonDecorator.kt`.
- **Mechanism:** chip id resolved once per decorator; `findButton` walks children by index from the
  end; `onFullscreenProgress` stores the clamped progress only when it differs (the launcher repeats
  0 on idle cards), so it boxes only on change.
- **Correctness:** same first-from-end match on `tag` equality; same chip id; same stored progress.
- **DEX:** confirmed primitive compare then `Float.valueOf` only on the change branch.
- **Measured (with 3 and 4, Overview workload):** launcher main-thread CPU −45.1% (median 19.74 s to
  10.83 s per 60 cycles, ranges do not overlap), janky frames −72% (54 to 15), p99 frame 15 to 11 ms,
  allocation −6.4%. GC count and GC time unchanged.
- **Result:** accepted (measured).

## 6. Double tap to sleep started a new `Thread` per double tap

- **Path:** `feature/gesture/DoubleTapToSleepFeature.kt` (`ScreenOff`).
- **Mechanism:** a lazily created `ThreadPoolExecutor(1, MAX, 60 s, SynchronousQueue)` with daemon
  threads named `PixelLauncherEvolved-sleep`. The first double tap creates the one core thread and it
  stays parked; later taps are handed to it. A tap that arrives while that thread is still waiting on
  the provider gets an extra thread (as before, calls overlap), and extra threads exit after 60 s
  idle. The Binder/root call stays off the UI thread.
- **Correctness:** same provider call and same logging. Overlapping calls still run in parallel and
  reach the provider's single-flight guard.
- **Measured (instrumentation build: timestamps around the handoff and the provider call, logged per
  tap; Pixel 8 Pro; 2 interleaved rounds × 12 taps per variant; the owner unlocked after each tap;
  the candidate here used core size 0):** handoff from the gesture thread to the worker, taps 2–12
  of each block, n = 22 each: baseline median 1.30 ms (IQR 1.04–1.53, range 0.71–2.96), candidate
  median 0.22 ms (IQR 0.18–0.28, range 0.08–0.33), −83%; every candidate sample was below every
  baseline sample. First tap in a process: baseline 1.62/1.63 ms, candidate 2.77/2.45 ms (the pool
  is created then). Provider call (root sleep key) unchanged: medians 223 vs 225 ms.
- **Follow-up:** with core size 0 the thread would exit after 60 s idle, so a normal tap (minutes after
  the last) would again start a thread. Core size was raised to 1. The steady state is then the
  taps 2–12 case above, checked on device: the worker (tid 6289) was still parked 75 s after its tap.
  Cost: one parked daemon thread in the launcher once the gesture has been used.
- **Scale:** about 1 ms of a roughly 225 ms action; the root call dominates what a person sees.
- **Result:** accepted (measured).

## 7. Search result filter created an iterator per result

- **Path:** `feature/search/SearchResultKind.kt` (`HiddenSearchResults`).
- **Mechanism:** the kinds set is copied to an array once per result batch; `hides` walks it by index
  instead of calling `Set.any`, which created an iterator for each result on every keystroke.
- **Correctness:** same kinds in the same (`LinkedHashSet`) order, same short-circuit; the
  `SearchResultKindTest` cases still pass.
- **Measured (instrumentation build, with the search part of entry 2; baseline `031d5e5` vs entries
  1–7; 4 interleaved rounds × 60 typed queries, 11–12 batches of 100 filter calls, ~23 results
  each):** allocations per filter call 76.6 → 51.0 (−33.4%; IQRs 75.8–76.9 vs 50.7–51.5, no
  overlap). Time per call p50 126.3 → 110.9 µs (−12.2%), mean −8.5%, but the IQRs overlap, so the
  time gain is not established.
- **Result:** accepted (allocation reduction measured).

## 8. Hide-apps tick hook installed only while choosing — REJECTED, reverted

- **Hypothesis:** `BubbleTextView.onDraw` is hooked for the picker's ticks, so every icon draw on the
  home screen, drawer and taskbar goes through the libxposed dispatch (argument list, chain object)
  even though the hook draws nothing unless apps are being chosen. Installing the hook when choosing
  starts and unhooking it when choosing ends would remove that per-draw cost.
- **Path:** `feature/apps/HideAppsPickerFeature.kt` (`HookHandle.unhook()`, libxposed 102).
- **Correctness:** checked on device. Ticks appeared when choosing started and were gone after
  leaving; nothing was written.
- **Measured (A/B against the entry 1–7 candidate, Pixel 8 Pro, 4 interleaved rounds × 40 cycles):**
  home workload (page swipes, drawer open and fling): main-thread CPU −0.4% (medians 15.01 s vs
  14.94 s, ranges overlap), janky frames +8.6%, p99 frame 14 → 15 ms, allocation +3.7% (1 MB
  resolution). Overview: CPU +1.2%, janky frames unchanged. All within run-to-run noise. The device
  reached thermal status 1 by the last round.
- **Result:** rejected and reverted. There is no measurable gain, and it adds runtime hook
  install/unhook lifecycle risk.

## 9. `MethodHandle.invokeExact` for the search result type — REJECTED, reverted

- **Hypothesis:** reflection ladder step 2. `Method.invoke` boxes the `int` from
  `SearchTarget.getResultType()` for every result. A cached
  `MethodHandles.lookup().unreflect(m).asType((Object)int)` called through `invokeExact` from Java
  would return the primitive with no box.
- **Path:** `core/Invoke.java` (`intGetter`/`intNoArgs`), `feature/search/SearchTargets.kt`. Measured
  together with the lazy reads of entry 10.
- **Measured (instrumentation build, Pixel 8 Pro, 6 interleaved rounds × 80 typed queries, batches of
  100 filter calls, ~23.5 results each; A = entries 1–7, B = A + this + entry 10):** per call p50
  100.95 → 346.64 µs (+243%), mean +229%, allocations 51.2 → 304.1 per call (+494%). In every batch
  B was slower than A.
- **Why:** on ART a call through an `asType`-adapted handle does not become a direct call. It goes
  through the handle's transform machinery and allocates on every call, which costs far more than the
  boxing it removes.
- **Result:** rejected and reverted. On this runtime, cached `Method.invoke` (with the shared empty
  argument array) is the fastest reflective route measured.

## 10. Search results: read layout/package only when a rule needs them, lazy copy — REJECTED, reverted

- **Hypothesis:** most results need only `getResultType()`. `getLayoutType()` matters only when
  "Search in Apps" is hidden, and `getPackageName()` only when "Play Store" is hidden or apps are
  hidden. Skipping the unneeded reflective calls and creating the kept list only at the first removal
  would cut time per filter call.
- **Correctness:** a differential unit test over every combination of settings and the captured
  results gave the same answers.
- **Measured (instrumentation build, A = entries 1–7, B = A + this, 6 interleaved rounds × 80
  queries, 22 batches each):** p50 101.2 → 97.9 µs (−3.2%, IQRs overlap), mean −10.7% (IQRs overlap),
  p99 −31% (IQRs overlap), allocations 50.7 → 49.2 per call (−3.0%).
- **Result:** rejected and reverted. The gain is noise-level, and it ties the "needs" flags to the hide
  rules, so a future rule change could silently read too little.

## 11. Search results read through direct `SearchTarget` calls instead of reflection

- **Hypothesis:** the search filter made three `Method.invoke` calls per result (`getResultType`,
  `getLayoutType`, `getPackageName`), about 70 per keystroke, and `getResultType` came back boxed.
  The class is `@SystemApi`, so the public SDK leaves it out, but the launcher process already
  reflects on it successfully. Compiling against a stub and calling the device's own class directly
  should skip the reflective dispatch.
- **Path:** new compile-only module `stubs/` (`android/app/search/SearchTarget.java`, three getter
  signatures, never packaged); `feature/search/SearchTargets.kt`.
- **Mechanism:** `SearchTargets.read` casts to `SearchTarget` after the same `isInstance` check and
  calls the getters. The three getters are still looked up by reflection at install, so a device
  without them still leaves the feature uninstalled, as before.
- **Correctness:** same values (`int` result type; `null` layout type or package read as `""`), same
  `runCatching` around the reads. `SearchTarget` is a final framework class, and the class that
  `isInstance` checks is the same boot class the stub links to. Unit tests and
  `check-project.sh` pass. On the device (release build), typing `cal` delivered three
  `com.android.vending` targets to the launcher, and none were shown (Play Store results hidden).
  No filter warnings, no hidden-API denials in logcat.
- **DEX:** `invoke-virtual Landroid/app/search/SearchTarget;.getResultType:()I` (and the two
  `String` getters) inlined into the filter; no `Method.invoke`, no `Integer.valueOf`.
- **Measured (instrumentation build, `SearchProbe`, Pixel 8 Pro, not charging, thermal 0; A = v0.1.2
  `bc3163c`, B = A + this; 6 interleaved rounds × 80 typed queries, 19 batches of 100 filter calls
  each, ~23.7 results per call):** allocations per call 52.3 → 34.4 (−34.3%; IQRs 51.9–52.8 vs
  34.2–34.7, no overlap). p50 100.2 → 82.4 µs (−17.8%), mean −15.3%, p95 −10.2%, p99 −4.1%. The
  pooled p50 IQRs overlap only because each block's first batch is slower (warm-up). Batch for
  batch at the same position in the same round, B was faster in all 19 pairs.
- **Result:** accepted (measured). This does not contradict entry 9. That entry was an
  `asType`-adapted `MethodHandle`, which is slower than reflection on ART. This is a plain
  `invoke-virtual`.

## 12. Contract analysis read each class's members once — REJECTED, reverted

- **Hypothesis:** `ContractAnalyzer.declares` called `getDeclaredMethods()`/`getDeclaredFields()` for
  every contract and every class up the hierarchy (to `Activity` and `Object` for a missing or
  inherited member). Each call builds a new array of member objects. Caching the arrays per class
  for one analysis would cut the 6.3 ms the analysis takes at every launcher start.
- **Path:** `diagnostics/ContractAnalyzer.kt`.
- **Correctness:** same arrays, so the same answers. The cache lives only as long as one analyzer.
- **Measured (startup instrumentation build, `scripts/instrumentation/start-probe.patch`; A = `831da0a`,
  B = A + this + entry 13; 6 interleaved rounds × 6 launcher restarts, first restart after each
  install dropped, n = 30 each):** analysis 6.25 → 5.72 ms (−8.5%; IQRs 6.05–6.51 vs 5.62–5.97;
  lower in every round). But `home_blur_wallpaper` install, which runs later, rose 2.11 → 2.56 ms,
  also in every round. Total install time 50.12 → 51.45 ms (+2.7%, IQRs overlap).
- **Result:** rejected and reverted. The saving moved to a later install step instead of leaving
  the startup path, and total module install time did not improve.

## 13. One hook per method for every `hookAfter` body — REJECTED, reverted

- **Hypothesis:** several features follow the same launcher methods through `FeatureContext.hookAfter`,
  and each call installs its own hook: `TaskView.setFullscreenProgress` ×3 (bubble, split, action
  row motion; per card on every Overview frame), `TaskView.onLayout` ×2, `TaskView.onFinishInflate`
  ×2, `OverviewActionsView.onFinishInflate` ×3, `Launcher.onResume` ×5. Registering one hook per
  method, with the bodies in an array, would take the call through the framework's chain once.
- **Path:** `hook/FeatureContext.kt` only.
- **Correctness:** checked the current order on device first. An instrumentation build logged each body
  with its registration number, and the bodies run last-registered first (`reg#20, 18, 16` on
  `OverviewActionsView.onFinishInflate`, `13, 10` on `TaskView.onFinishInflate`). The fused
  dispatcher kept that order, and a `runCatching` and warning per body. The one direct hook with a
  shared method name (wallpaper blur, `onResume`) is on `QuickstepLauncher`, a different method.
- **Measured (release builds, A = `831da0a`, B = A + this; `WORKLOADS=overview`, 4 interleaved
  rounds × 60 cycles, thermal 0):** main-thread CPU 11.52 → 11.41 s (−0.9%, IQRs overlap), janky
  frames 15.5 → 12.5 (IQRs overlap), p99 frame 10.5 → 10 ms, allocation and GC unchanged.
  Install time for the Overview features changed by less than 0.1 ms (entry 12 run).
- **Result:** rejected and reverted. The change is within run-to-run noise, and it adds shared
  hook state across features that were isolated before. Measured together with entry 8's finding, the
  libxposed dispatch per hooked call is not a cost that shows up on this device.

## Examined, not changed

- **Task card layout** (`TaskCardButtonDecorator.onTaskViewLaidOut` → `place` →
  `TaskViewGeometry.thumbnailBounds`). Profiled with an instrumentation probe
  (`LayoutProbe`, same shape as `SearchProbe`) over 40 Overview cycles: about 11 calls per cycle,
  8 batches of 50, batch means 24–52 µs and p50 13–27 µs, 16.4 allocations per call. That is about
  0.3 ms per cycle against about 180 ms of launcher main-thread CPU per cycle in the first benchmark
  (≈0.2%). Reusing a scratch `Rect` and argument array would remove 2 of the 16 allocations; the
  rest come from measuring and laying out the button itself. The saving is below what the release
  benchmark can resolve, so the path is left as is.
- **Settings reads in hot hooks** (`SharedPreferencesSettings.get`): `SharedPreferencesImpl.getBoolean`
  is a lock plus a `HashMap` lookup with no allocation. A generation-counted snapshot would need a
  change listener and lifecycle handling for a sub-microsecond saving that has not been measured.
  Deferred until a profile shows it.
- **Diagnostics root check thread** (`DiagnosticsRows.root`): one thread per settings page open,
  not a gesture path.
- **Focus preview/refresh executors**: already single reusable workers.
- **`StatusBarSleep` in SystemUI** calls `PowerManager.goToSleep` synchronously on the touch thread.
  Existing behavior, a single Binder call; not moved without a measurement.
- **Search result filtering** (`AppDrawerSearchFeature.keep`) still builds a `HiddenSearchResults`,
  a kinds set and a filtered list once per result batch, plus one `SearchResult` per result. That is per keystroke, not per frame; left as is.
- **Home search bar touch hooks** (`HomeSearchBarFeature`): the `onTouchEvent` hook builds one
  `Gesture` and reads the long-press field on each event. The widget host only gets `onTouchEvent`
  for the events it intercepted (the search bar, where that work is needed) or that no child
  consumed. Skipping the read for unclaimed widgets would save almost nothing, so it was left as is.
- **`applyState` hooks** (`TaskbarAllAppsButtonFeature`, `TaskbarTransitionFeature` on
  `TaskbarLauncherStateController.applyState`): two hooks on one method, but it runs about once per
  launcher state change. Covered by entry 13's result.
- **Module install at launcher start** (about 50 ms on the main thread before `Application.onCreate`
  returns, profiled in `PERFORMANCE.md`): no single step is over 13% of it. The largest are contract
  analysis (6.3 ms, entry 12) and Focus home screens (5.8 ms, class loading and about 10 hooks).
  Launcher starts are rare (boot, crash, module update), so this was not pursued further.

## 14. Icon pack index read in bulk at launcher start — ACCEPTED

- **Hypothesis:** the cached index was read one value at a time through an unbuffered
  `DataInputStream`, on the launcher's main thread during start: about 75,000 stream calls for a
  large pack.
- **Path:** `icons/IconPackIndex.java` (`read`, `write`).
- **Mechanism:** read the file into one `byte[]`, parse the header from a `ByteBuffer`, copy each
  array with `asLongBuffer().get(long[])` / `asIntBuffer().get(int[])`. The writer is wrapped in a
  `BufferedOutputStream`. The file format is unchanged (big-endian, `writeUTF` header), so existing
  index files still read.
- **Correctness:** `IconPackIndexTest` round-trips an index and refuses a stale version or another pack.
- **Measurement:** start-path publish 98 ms to 9 ms median (n = 5 / 6, `PERFORMANCE.md`).
- **Allocation/memory:** one extra `byte[]` of the file size (about 300 KB for 24,000 components),
  garbage after the read.

## 15. Icon pack compile off the model thread — ACCEPTED (responsiveness)

- **Hypothesis:** compiling a pack on `Executors.MODEL_EXECUTOR` blocks the launcher's model
  (installs, updates, the home screen load) for the whole parse.
- **Path:** `feature/icons/IconPackController.kt`.
- **Mechanism:** one daemon worker (`ple-icons`) for compiling, package-manager reads for settings
  and broadcast handling; only `forceReload` is posted to the model thread. A source is published only
  with its index, so an icon generated meanwhile is never stored under the pack's key.
- **Measurement:** compile of a 24,169-component pack: 1,014 ms on the worker (n = 1). The model
  thread no longer runs it.

## 16. Reload without clearing the icon memory cache — ACCEPTED (no placeholder flash)

- **Hypothesis:** clearing `BaseIconCache.cache` before `forceReload` is unnecessary, because the
  freshness key already marks the stale entries, and it turns every visible icon into a placeholder.
- **Path:** `feature/icons/IconReloader.kt`.
- **Result:** on device, an override changed one icon in about 2 s with every other icon unchanged on
  screen; before, every icon showed a grey placeholder for 1 to 3 s. Pack to System and pack updates
  still regenerate every affected icon.

## 17. Icon packs indexed ahead of being chosen — ACCEPTED (latency)

- **Hypothesis:** choosing a pack for the first time waited on its index compile (about a second
  for a large pack) before the launcher reload even started.
- **Path:** `feature/icons/IconPackController.kt` (`prewarm`, `onPackageChanged`),
  `feature/icons/IconPackBridge.kt` (`state`).
- **Mechanism:** compile on the `ple-icons` worker when a pack is installed or updated, and for
  every pack without an index when Wallpaper & style lists the packs. An index of an older format
  is deleted when its replacement is written.
- **Measurement:** with the index present, request to reloaded model 426 and 532 ms (n = 2,
  `PERFORMANCE.md`); without it, the compile (1,014 ms for 24,169 components) came first.
- **Cost:** one compile per pack per version, off every launcher thread; about 100 to 500 KB of
  index per pack on disk.
