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

## 18. Grid & size values at parse time, not after layout — ACCEPTED (design)

- **Hypothesis:** writing grid, icon and gap values into what the launcher parses (grid option,
  responsive specs, the profile's grid spec) costs nothing per frame, whereas resizing views after
  layout would add work to every bind and leave hit targets, drag outlines and dots mismatched.
- **Path:** `feature/grid/GridFeature.kt`.
- **Mechanism:** cold hooks only; at defaults each reads one preference and returns.
- **Measurement:** none on frame paths (nothing added there); migration 40 ms, single sample
  (`PERFORMANCE.md`).

## 19. Preview drawn once, after its model has the new grid — ACCEPTED (bug fix, latency)

- **Hypothesis:** a Custom column or row change drew every preview twice. The preview profile's
  change listener rebuilt the renderer at once, and a new renderer first binds the layout its model
  still holds. With the old grid's cells that showed missing icons, a missing search bar and items
  that jumped from wrong cells to their final ones.
- **Path:** `feature/grid/GridPickerBridge.kt` (`Renderers.redraw`).
- **Mechanism:** the renderer's IDP listener is removed for `onConfigChanged`. The current renderer
  is unbound, so the reload cannot rebind it. `recreatePreviewRenderer` then runs once, when
  `LauncherModel.mLoadCompleteFuture` completes, with a 2 s fallback. A change that does not reload
  the model (spacing only) is drawn at once, as before.
- **Measurement:** 80 fps screen recordings, n = 6 changes. Before: a stale frame, then the final
  frame 340 to 560 ms later. After: one swap to the final layout. The smartspace and search bar
  still bind 1 to 2 frames after the icons, which is stock behaviour.

## 20. No launcher rebuild for counts of a grid it is not on — ACCEPTED (CPU, UI thread)

- **Hypothesis:** each Columns or Rows release ran `onConfigChanged` on the launcher's own profile
  even while the launcher was on a Google grid. That meant `reapplyUi`, a taskbar recreate and a
  model check on the main thread, all for a value only previews read.
- **Path:** `feature/grid/GridFeature.kt` (`onSharedPreferenceChanged`, `Grid.custom`).
- **Measurement:** logcat per release, before: launcher `initGrid(medium)` + taskbar recreate.
  After: preview `initGrid` only. Icon size, spacing and All apps columns still rebuild, because
  every grid reads them.

## 21. Custom grid parsed under the launcher's own grid — ACCEPTED (bug fix)

- **Hypothesis:** for the unknown name `ple_custom` the launcher picks the grid closest to the one
  shown before. In a preview that is the last tile previewed. Custom then inherited that grid's
  specs (after Large: Large's icons on a 4 × 6 grid, a zoomed and cropped preview), and System
  counts resolved to that grid's size (Custom showed 3 × 3).
- **Path:** `feature/grid/GridFeature.kt` (parse hook), `feature/grid/Grid.kt` (`stockName`).
- **Mechanism:** the parse runs under the name of the last Google grid the launcher read itself.
  System resolves against that grid's counts.
  Started on Custom (no Google grid read yet), it falls back to the launcher's pick, as before.
- **Result:** Large then Custom (4 × 6) previews at the right scale, verified on device.

## 22. Per-frame Custom tile lookup without allocation — ACCEPTED (allocation)

- **Path:** `picker/GridPicker.kt` (`custom`, `findText`), in Wallpaper & style's pre-draw listener.
- **Mechanism:** caches the Custom tile and checks its parent and text again each frame. The
  `(0 until n).any {}` walk, an `IntRange` and iterator per view per frame, is now an index loop.

## 23. Previews skipping migrations into the live database — REJECTED (data loss)

- **Hypothesis:** returning from `migrateGrid` without proceeding would keep previews from writing
  into the launcher's live database.
- **Result:** `attemptMigrateDb` empties the destination before it calls `migrateGrid`. The skip left
  `launcher_4_by_6.db` empty, the launcher loaded default favorites and released 4 widget IDs. The
  owner's layout was restored from a preview copy; the widgets must be re-added. Reverted;
  superseded by 24.

## 24. Previews in a database sandbox, migrated from the live layout — ACCEPTED (bug fix, safety)

- **Hypothesis:** previews opened the launcher's real database files. Each migrated into the
  previewed grid's file, the live one included, starting from whichever grid was previewed before,
  and with the stock memory rule. A preview could therefore differ from Apply: 6 × 6 showed 2 of
  8 icons. It could also rewrite remembered layouts, or the live one.
- **Path:** `feature/grid/PreviewSandbox.kt`, `feature/grid/GridFeature.kt` (migration hook).
- **Mechanism:** sets the launcher's own `PreviewContext.mDbDir`, which stock uses only for
  layout-file previews, to `cache/ple_previews/<preview>` on first `getDatabasePath`. Each database
  is copied in on first use with `VACUUM INTO`, a consistent snapshot even while the launcher
  writes. A preview `migrateGrid` gets `DeviceGridState(appContext)` as its source state and a
  read-only fresh snapshot of the live database (`ple_preview_source.db`) as its source database.
  The page-keeping memory rule now applies to previews too. `cleanUpObjects` deletes the folder
  when the preview ends; whatever is left over is deleted at install.
- **Result:** md5 of all 23 real `launcher*.db` files is identical before and after 6 preview
  migrations. Every preview migration logs `src = ple_preview_source.db (4,6)`. 6 × 6 shows all
  8 page-0 items. The folder is gone after W&S closes.
- **Cost:** one ~0.5 MB snapshot per database a preview opens, and one per preview migration
  (a few ms each, on the model thread of the preview, never the launcher's).

## 25. Custom tile redrawn as its counts change — ACCEPTED (bug fix)

- **Hypothesis:** the Custom tile's grid icon kept the old size until Wallpaper & style restarted.
  `DefaultShapeGridManager.gridOptions` re-reads `list_options` only from a content observer on
  `default_grid`, and the module notified `list_options`, which nothing observes.
- **Path:** `feature/grid/GridPickerBridge.kt` (`put`), `picker/GridPicker.kt` (`reveal`).
- **Mechanism:** notify `content://…grid_control/default_grid`. The picker then lists its tiles
  again and its list starts over from the first tile. For 1.5 s after a pick, the Layout panel's
  pre-draw scrolls the Custom tile back into view; outside that window it costs one field read per frame.
- **Result:** on device, Columns 4 → 5 and Rows 6 → 5 redraw the tile at once (5 × 6, 5 × 5) with
  Custom fully in view, one preview `initGrid` per change, real databases unchanged (md5).

## 26. App labels hooked only once a label exists — ACCEPTED (design)

- **Hypothesis:** label overrides touch the most frequent bind in the launcher
  (`BubbleTextView.applyIconAndLabel`) and the drawer sort; most people will set none.
- **Path:** `feature/labels/AppLabelsFeature.kt`, `labels/AppLabelStore.kt` (`onFirstLabels`).
- **Mechanism:** at install the stored labels are compiled; with none, no hook is installed and the
  store calls back the first time it publishes a non-empty set (from Home settings, same process),
  which installs the hooks live. Once installed they stay; with no labels left each returns after a
  volatile read. Lookup is a 64-bit component+user key, binary searched, no strings built per bind.
- **Result:** default path costs nothing (no hook). Drawer scroll A/B in `PERFORMANCE.md`
  (2026-10-13): p50/p90 identical, janky/p99 within noise. Script: cold launcher, toggle the
  `home_app_labels` line in the prefs file with the launcher stopped, 10+10 flings, `dumpsys gfxinfo`.
  **Caution:** inserting that line with `sed` broke the file (the JSON's `&quot;` is a sed
  replacement metacharacter) and the launcher rewrote it nearly empty; the owner's settings were
  restored from a copy. Back the file up first and write it whole (push a prepared copy) instead.
- **Not tried:** fusing the two label hooks into one around `applyIconAndLabel` that swaps
  `info.title` for the bind; rejected without measuring because it mutates the model's object, which
  the model thread may write to the database at the same moment.

## 27. App labels: Home screen only and Edit Name — ACCEPTED (feature)

- **Path:** `feature/labels/AppLabelPopup.kt`, `AppLabelsFeature.kt`, `labels/AppLabels.java`.
- **Mechanism:** Home-only is one static volatile, set whenever the store publishes, read only after a
  label lookup already hit (bind) or before any sort/search work. The menu hook runs once per
  long-press, allocates its row there, and is installed even with no label (a first label can come
  from it). No frame, bind or scroll path changed for apps without a label.

## 28. Modes page wallpaper read from its file, not off the display — ACCEPTED (bug fix, latency)

- **Hypothesis:** the wallpaper capture (launcher surface alpha 0, then `ScreenCapture`) blanks home
  and is never kept; reading the file through Wallpaper & style removes the blanking and gives every
  page its own part of the wallpaper.
- **Evidence before (2026-10-20, CP3A.261005.005, launcher 907):** 6.4 s after every return to home,
  `input_focus` logs `Focus leaving … NexusLauncherActivity,reason=NOT_VISIBLE` for 165–240 ms.
  Screenrecord: icons absent from 5827 to 5989 ms (Laplacian edge energy 25.7 against 519). Hiding the
  surface drops window focus, so `isSettled` failed after the capture and every result was dropped:
  `files/focus-page-previews/` stayed empty for 18 h and previews drew the gradient fallback. The
  hidden frame was also still blurred (correlation with the wallpaper at Gaussian σ 20: 0.993, sharp:
  0.900), so a kept capture would have been blurred twice.
- **Path:** `bridge/WallpaperBridge.kt` (new, Wallpaper & style process), `feature/focus/FocusWallpaperStore.kt`,
  `FocusPagePreview.kt` (`wallpaperOffset`, `wallpaperSection`, per-offset backdrops), `PixelLauncherEvolvedModule.kt`.
- **Mechanism:** Wallpaper & style holds `READ_WALLPAPER_INTERNAL`; the launcher holds the permission of
  its `RecentWallpapersProvider`. The module answers `ple_home_wallpaper` on that provider's `call`
  (caller must be the launcher, shell or root): `getWallpaperFile(FLAG_SYSTEM)`, hidden
  `getBitmapCrops([display], FLAG_SYSTEM, false)`, `BitmapRegionDecoder` of that crop sampled and
  scaled to 1400 px tall, JPEG 90. The hook is installed at package load, because providers are
  published before `Application.onCreate` (installed after it, the launcher's first call raced it and
  got null). The launcher asks once per wallpaper (colours key) and keeps the JPEG; a page shows one
  screen of the crop at the launcher's parallax offset (`i / (max(n, 4) − 1)`), zoomed by
  `config_wallpaperMaxScale` less home's resting depth, then blurred as before.
- **Result (device):** bridge picture 1258 × 1400 (crop 2017 × 2245, same aspect), correlation with
  the dumpsys crop 0.991; no `NOT_VISIBLE` focus loss and no icon-less frame after returning home;
  previews show the wallpaper once blurred, page 1 matching the live home, page 4 its trailing edge.
  Release APK 1 140 536 → 1 124 152 bytes. One extra Wallpaper & style process start per wallpaper
  change (off the main thread).
- **Verified later (entry 31):** a two-page ordinary home (Work's page released for the test, then put
  back; the settings file was byte-identical afterwards): live page 2 `mWallpaperX=0.333`, as
  `i / (max(n, 4) − 1)` gives; each preview tile matches its own live page (r 0.94 / 0.79, shift 0–4 px)
  and not the other (r 0.29–0.34).
- **Not verified on device:** RTL (`debug.force_rtl` without a configuration change does not mirror the
  launcher; a real test needs an RTL system language), live wallpapers (no file: previews keep the
  gradient, as before).

## 29. Modes snapshot off the main thread; pages redrawn per opening — ACCEPTED (bug fix)

- **Evidence before:** opening Modes pages read the Modes (`ProviderFocusSource.snapshot`, a provider in
  this module's frozen app that may run `su`) on the launcher main thread: `Skipped 59 frames` on the
  first opening, p99 250 ms on later ones. Pictures were redrawn only after a workspace bind, so a
  widget's content could be stale.
- **Path:** `FocusPagesDialog.readThen` (one daemon reader thread, drops a second tap while a read is
  out, skips a finished activity), `HomeScreenRows.OrganizePagesRow`, `FocusPagePreviewSource.pages`.
- **Result (device, 3 openings each):** janky 1.5–2.4 %, p99 16–25 ms while four pages redraw
  (428–672 ms each, awake). Previous pictures stay up until replaced, so nothing blinks.

## 30. Expected-path logging removed — ACCEPTED

- `TaskbarHomeVisibilityFeature` logged on nearly every pause and resume ("taking back its place",
  "giving it back") from the taskbar thread; `HomeWallpaperBlurFeature` logged every blur pause and
  hooked `WorkspaceRevealAnim`'s constructor only to feed that log. Both removed; the reveal hook too.
- A pause answered by a resume before its repair ran (Home pressed on home) now cancels the pending
  opposite repair (`removeCallbacks` of both runnables).

## 31. A Modes page sits on the wallpaper where its own home shows it — ACCEPTED (bug fix)

- **Report (owner):** with Work on, the Work page's preview showed another part of the wallpaper than home.
- **Cause:** entry 28 placed a page by its position among all pages. Home shows only the active
  Mode's pages, or the ordinary ones, so a Mode's single page sits at offset 0.
- **Fix:** `FocusPlan.shownWith(screen, order, assignments)` gives the pages shown with it (its Mode's,
  or the ordinary ones); the offset is the page's index among those, mirrored for right-to-left.
  `FocusPagePreviewSource.pages` takes the assignments from both callers.
- **Result (device, Work on):** preview tile against the live home: best horizontal shift 4 px of a
  459 px tile (< 1 %), correlation 0.988 (0.971 unshifted).

## 32. Pre-draw listeners left on the window after their view detached — ACCEPTED (leak, per-frame cost)

- **Evidence:** `meminfo` Activities 1 → 3 after two launcher recreates (`cmd uimode night no/yes`).
  Heap dump taken in-process (`Debug.dumpHprofData` from a throwaway build; the launcher is not
  debuggable), converted with `hprof-conv`, shortest strong path from GC roots: `ViewRootImpl` →
  `ViewTreeObserver.mOnPreDrawListeners` → `OverviewActionsFeature` listener and
  `OverviewActionsMotionFeature$ShownWatcher` → old `NexusOverviewActionsView` → old activity. With
  those two excluded the old activities are unreachable.
- **Fix:** each listener removes itself in `onViewDetachedFromWindow` (the window's observer is still
  reachable there) and registers again on attach: `OverviewActionsFeature`, `ShownWatcher`,
  `TaskCardButtonDecorator` (every TaskView ever made was checked per frame), `GridPicker`'s panel
  (Wallpaper & style; one more listener per opening).
- **Result (device):** two recreates with Recents opened between them: Activities 1.

## 33. First workspace bind never waits on a Modes read — ACCEPTED (P01, latency)

- **Hypothesis:** the first bind read the Modes itself when nothing had been read yet, so a slow
  provider (the module app starting, or root up to its 15 s timeout) held the workspace back.
  Separately, a window-focus read that finished before the first bind asked for a reload, which
  could cancel the launcher's first load and run it again.
- **Path:** `feature/focus/FocusHomeFeature.kt` (`FocusHome.winner`, `change`, `screens`).
- **Mechanism:** the bind plans with the last read or, with none yet, the ordinary home (Mode pages
  hidden). A read finishing before the first bind is kept for it and asks for no reload; one
  finishing after reloads only when the plan differs, i.e. a Mode with pages is on.
- **Correctness:** same home once a read has answered. Until then the home is neutral: no Mode's
  pages shown, nothing moved. `FocusHomeTest`: first bind never reads and shows the ordinary home;
  a read before the first bind is used by it with no reload; existing Mode tests read first.
- **Measurement:** Pixel 8 Pro, CP3A.261005.005, release builds `54eb5c2` vs candidate, interleaved,
  cold launcher start with the module app also stopped. Times from launcher process creation.
  - Normal provider, n = 3 each: displayed 681/777/824 vs 528/861/738 ms; first screen bound
    928/1109/1306 vs 803/1338/1195 ms. No difference beyond noise; module reloads 0 in both.
  - Module app frozen for 6 s during the start (stand-in for a slow root read), n = 2 each:
    displayed 353/432 vs 354/376 ms; first screen bound **7091/7213 vs 625/677 ms**; module-forced
    reloads 1/1 vs 0/0.
- **Allocation/memory:** none measured; the change removes a call, adds a boolean.

## 34. A Mode change rebinds the loaded model instead of reloading the database — ACCEPTED (P02, latency)

- **Hypothesis:** a Mode change only changes which pages show, yet `forceReload` threw the model
  away and read the whole workspace database again before binding.
- **Path:** `feature/focus/FocusHomeFeature.kt` (`rebuild`).
- **Mechanism:** call `LauncherModel.rebindCallbacks(String)`, which binds the in-memory
  `BgDataModel` through the same `bindWorkspace` path (and falls back to a full load itself while
  nothing is loaded or a loader is running). `forceReload` stays as the fallback when the method
  is missing.
- **Correctness:** same filter hooks run on the bind; DND mapped to page 7 showed page 7 on and
  the ordinary home off (screenshots), reveal animation still ends on `removeExtraEmptyScreenDelayed`.
- **Measurement:** Pixel 8 Pro, CP3A.261005.005, launcher 907, release builds identical except the
  method called, temporary log probes from `applyChange` to the binding-end hook, DND toggled from
  adb, interleaved 3 rounds × 2 toggles × on/off (n = 12 each).
  - forceReload: 219 179 158 220 171 153 140 170 199 154 156 223 ms; median 170.5.
  - rebindCallbacks: 30 56 24 46 26 57 25 51 27 61 27 51 ms; median 38.5 (−77%).
- **Allocation/memory:** not measured; no database cursor or new item objects per change.

## 35. Release Modes page previews when the launcher is hidden — REJECTED (P03, memory)

- **Hypothesis:** the page pictures (`FocusPageRenderer`'s `RenderBook<Bitmap>`) live for the whole
  launcher process; dropping them at `TRIM_MEMORY_UI_HIDDEN` when no preview view is attached would
  bound their lifetime to the time they can be seen.
- **Mechanism tried:** a `ComponentCallbacks2` on the launcher's app context clearing the book on
  the main thread when no listener was registered; a dropped answer found no page and was discarded.
- **Result (device, Pixel 8 Pro, CP3A.261005.005):** after a trim and reopening Home settings →
  Modes pages, a stray dark landscape surface was drawn over the first row on two reopenings; the
  same steps on the build without the change showed the dialog clean. Cause not traced (the
  launcher's preview surfaces are owned by its own renderer); the pictures are bounded by page count
  and redrawn per model generation already.
- **Decision:** reverted. Do not retry without first finding who owns the stray surface.

## 36. A Mode's label switches rebind the loaded model instead of reloading it — ACCEPTED (P02, latency)

- **Hypothesis:** a Mode that changes which surfaces show labels only changes what an icon bind
  writes, yet it went through `IconReloader`'s `forceReload`, which reads the whole database again.
- **Path:** `feature/labels/AppLabelsFeature.kt` (`ModeOverrides.onLabelsChanged`),
  `feature/focus/FocusHomeFeature.kt` (`ModeOverrides.rebindWorkspace`).
- **Mechanism:** the same `rebindCallbacks` call as entry 34 for Home, the dock and folders, then
  the drawer list's own `notifyUpdate` (`AppDrawerList.rebuild`), which also hands the predictions
  row its last list again. `forceReload` stays as the fallback when no launcher is bound yet.
- **Correctness (device, Pixel 8 Pro, CP3A.261005.005):** DND set to hide labels everywhere:
  Home, the predictions row and All apps lost their labels with DND on and had them back with it
  off; no `Icons: launcher reloaded` and no loader-completed line in the log for either change.
- **Measurement:** not measured separately; the call replaced is the one entry 34 measured
  (`forceReload` median 170.5 ms vs `rebindCallbacks` 38.5 ms to bound, n = 12).
- **Allocation/memory:** no database cursor or new item objects per change.

## 37. Drop Modes page pictures once no preview is on screen — REJECTED (P03, memory)

- **Hypothesis:** the launcher's `get_preview_bitmap` answers software bitmaps (540 × 1202, about
  2.6 MB each, one per page), kept by `RenderBook` for the launcher's life. Every opening draws the
  pages again anyway, so dropping them 2 s after the last preview view detaches should give that
  native memory back. Unlike entry 35 (trim callback), this only drops when no view listens.
- **Stray surface (entry 35):** not seen in four reopenings into a Mode's grid. SurfaceFlinger lists
  `SurfaceControlViewHost[com.google.android.apps.nexuslauncher]` layers per opening (the launcher's
  preview renderer); old ones go away, so they are released on collection, not leaked.
- **Leak check:** after an opening, `Activities` reads 2–3 until the settings activity is collected
  (15–60 s); with trims it returns to 1 in both builds. Not a leak.
- **Measurement:** Pixel 8 Pro, CP3A.261005.005, release builds HEAD (`18db845`) vs candidate,
  interleaved n = 3 each; launcher restarted, Home settings → Modes pages opened and closed, then
  waited (15 s steps with `RUNNING_CRITICAL` trims) until `AppContexts` was back to 13.
  - Native Heap PSS (KB): base 53802 54729 54121; candidate 44005 44913 44337.
  - Bitmap (malloced): base 12853 12852 12852 KB; candidate 2710 2710 2711 KB.
  - Graphics (KB): base 141520 141620 141524; candidate 151612 151456 151612.
  - TOTAL PSS (KB): base 236749 239083 238427; candidate 238632 240171 240099 (+0.7 %).
  - An earlier single-build run split the same way (2.7 vs 16 MB bitmaps) by collection timing
    alone, which is why each run waits for the contexts to be collected.
- **Decision:** reverted: the native memory moves to graphics with no total saving, and every
  reopening loses the instant pictures (each page then takes 0.53–0.63 s to draw, one after another).

## 38. Hidden apps read from preferences per app since Modes could hide apps — ACCEPTED

- **Hypothesis:** v0.1.5 → `dc3fb1c` replaced the drawer and search store with `EffectiveHiddenApps`
  (everywhere list plus the winning Mode's), whose `hidden()` called `SharedPreferences.getString`
  once per list (twice with a Mode on) and wrote a volatile field, on every app the drawer predicate
  tests and every search result. CLAUDE.md: hot hooks read compact state, not preferences.
- **Path:** `feature/apps/HiddenAppsStore.kt` (`EffectiveHiddenApps`), used by
  `HiddenAppsFeature` (drawer predicate, predictions row) and `AppDrawerSearchFeature`.
- **Mechanism:** the store is its own `OnSharedPreferenceChangeListener` (held strongly by the
  hooks; preferences keep listeners weakly) and counts writes to the everywhere key or any Mode
  key; `hidden()` is two volatile reads while neither the count nor the winning Mode changed. An
  answer worked out while a write lands keeps the count it was worked out for, so the next call
  works it out again.
- **Correctness:** `ModeOverridesTest` (new case: an unwritten change is not seen, a write through
  the editor and a Mode's own list are), all 33 test classes pass; `FakePreferences` now notifies
  listeners as the platform's does. Device (release candidate): Calculator hidden through Home
  settings → Hidden apps left the drawer and the search results at once, and came back when
  unhidden.
- **Measurement (instrumentation builds, `Probe.wrap` on the drawer predicate; Pixel 8 Pro,
  CP3A.261005.005, battery, thermal 0; base `dc3fb1c` vs candidate, 4 interleaved rounds, 40
  rebuilds each, batches of 2000 calls, ns, includes the launcher's own predicate and the probe):**
  - base p50 per batch: 2563 1790 1912 1871 1831 1872 2116 2075 2075 1872 2035 2645
    (median 1974, IQR 1871–2106, MAD 102; p95 4659, p99 6917, mean 2901).
  - candidate: 1628 1140 1587 1302 1709 1180 1627 1180 1628 1343 1587
    (median 1587, IQR 1180–1628, MAD 122; p95 3377, p99 5697, mean 1880).
  - p50 −19.6 %, mean −35.2 %, p99 −17.6 %. No Mode with hidden apps was on (the common case).
- **Allocation/memory:** 0 counted per call in both (thread allocation counter); one listener and
  one small object per store.

## 39. Drawer section bind hook read four fields per row with no section — ACCEPTED

- **Hypothesis:** `ModeDrawerSectionFeature`'s `onBindViewHolder` hook (new since v0.1.5) ran on
  every drawer row: app icons are `TextView`s, so it read `itemView`, `mApps`, `mAdapterItems` and
  the item by reflection inside `runCatching` even when no Mode heads the drawer.
- **Path:** `feature/apps/ModeDrawerSectionFeature.kt`.
- **Mechanism:** a main-thread flag set when a list is built with the section and cleared only when
  no list can carry one (no section apps or no title); the bind returns after one boolean read
  while it is clear. Other lists (a work tab) never clear it, so a section still shown keeps its
  title.
- **Correctness:** with a section, the same work runs; between a Mode going off and the rebuild it
  posts, the flag is still set and the header reads `""` as before. DEX: `proceed()` then
  `iget-boolean` and return.
- **Measurement (same builds and rounds, drawer flung down and up 15 times, batches of 500 binds,
  ns of the hook's own work after `proceed()`, probe included):**
  - base p50: 4028 4924 5127 5046 4069 5086 4558 4801 3987 5168 5005 5371 4680 4843 5005 4842
    (median 4884, IQR 4588–5076, MAD 203; p95 12227, p99 16602, mean 5878).
  - candidate: 1872 2157 2075 2523 1953 1953 2320 2360 1709 1791 2035 2198 1587 2563 2563 2563 2889
    (median 2157, IQR 1912–2543, MAD 285; p95 7731, p99 10254, mean 3035).
  - p50 −55.8 %, p99 −38.2 %. The candidate's figure is mostly the probe's own timer and counter.
- **Allocation/memory:** 0 counted in both; one `Ref.BooleanRef`.

## 40. Mode home tint boxed per scrim ask — ACCEPTED (not measured)

- **Path:** `feature/focus/FocusHomeFeature.kt` (`getWorkspaceScrimColor` hook).
- **Mechanism:** while a Mode tint is on, the colour (outside the `Integer` cache) was boxed on every
  ask; now boxed once per tint change. Main thread only. Asked per state change, not per frame, so
  not measured; it removes an allocation per ask and changes nothing else.

## 41. Layout copy at launcher start on a background-priority thread — ACCEPTED (not measured)

- **Path:** `layout/LayoutCheckpoints.kt` (`install`).
- **Mechanism:** the `VACUUM INTO` copy taken at each start while the layout depends on the module
  ran at default priority beside the first workspace load; it now runs at
  `THREAD_PRIORITY_BACKGROUND`. Same copy, same file. The owner's layout does not depend on the
  module, so this start path does not run on the test phone and was not measured.

| Artifact (release, R8) | `dc3fb1c` | Candidate | Change |
|---|---|---|---|
| APK | 1,202,324 B | 1,202,324 B | 0 (page-aligned) |
| classes.dex | 614,784 B | 614,880 B | +96 B |
