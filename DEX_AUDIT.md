# DEX audit

Final release DEX of the candidate (working tree on `031d5e5`, ledger entries 1–7). Produced with:

```bash
unzip -o app/build/outputs/apk/release/*.apk classes.dex -d out
dexdump -d out/classes.dex > out/dex.txt
python scripts/dex-audit.py out/dex.txt app/build/outputs/mapping/release/mapping.txt \
  'HiddenAppsFeature.shown$lambda' 'SharedPreferencesHiddenAppsStore.hidden' \
  'OverviewActionsFeature.walk' 'OverviewActionsFeature.apply' 'OverviewActionsFeature.watchBeforeDraw$lambda' \
  'OverviewActionsEntrance.applyTo' 'OverviewActionsEntrance.settle' 'ShownWatcher.onPreDraw' \
  'CardArrival.onFullscreenProgress' 'TaskCardButtonDecorator.findButton' 'TaskCardButtonDecorator.follow' \
  'TaskCardButtonDecorator.onFullscreenProgress' 'TaskCardButtonDecorator.onTaskViewLaidOut' \
  'watcher$1.onPreDraw' 'DoubleTap.isSecond' 'DoubleTapToSleepFeature.install$lambda' \
  'HiddenSearchResults.hides' 'SearchTargets.read'
```

The script finds each final method that R8 inlined one of these source methods into, via the R8
mapping. It then lists every `new-instance`, `new-array`, `filled-new-array`, boxing `valueOf`,
`Arrays.copyOf`, iterator, `StringBuilder`, `getIdentifier`, reflection lookup or call, `Log` call
and `monitor-enter` in that method. R8 merges lambdas into shared classes with a `switch`, so a merged
method also lists sites from the unrelated branches. Those are marked "other branch" below.

No method in the module uses JNI. The module has no native code.

## Frame paths (run before every launcher frame while the view is attached)

| Source method | Final method | Sites on the path taken each frame |
|---|---|---|
| `OverviewActionsFeature` pre-draw lambda | `OverviewActionsFeature$$ExternalSyntheticLambda1.onPreDraw` | none |
| `OverviewActionsFeature.apply` | `OverviewActionsFeature.e` | `getIdentifier` ×2, only on the first call (ids cached after, `UNRESOLVED` guard) |
| `OverviewActionsFeature.walk` | `OverviewActionsFeature.f` | `Integer.valueOf` in `putIfAbsent`, only on a frame where a button is being hidden |
| `OverviewActionsMotionFeature.ShownWatcher.onPreDraw` | `…$ShownWatcher.onPreDraw` | `getIdentifier` in `findButtons`, only until the button row is found; `ValueAnimator`, `float[]`, `LinearInterpolator` and listener, once per clock-driven arrival (not per frame) |
| `OverviewActionsEntrance.applyTo` / `settle` | `OverviewActionsEntrance.a` / `.b` | none |
| `TaskCardButtonDecorator` watcher `onPreDraw` (+ `findButton`) | `…$watch$watcher$1.onPreDraw`, `TaskCardButtonDecorator.b` | none |
| `TaskCardButtonDecorator.follow` / `chipOf` | `TaskCardButtonDecorator.c` | `getIdentifier`, only on the first call per decorator |
| `TaskCardButtonDecorator.onFullscreenProgress` | merged into `TaskCardHooksKt$$ExternalSyntheticLambda0.c` | `Float.valueOf` only when the stored progress changes (primitive compare first) |
| `CardArrival.onFullscreenProgress` | merged into `OverviewActionsMotionFeature$$ExternalSyntheticLambda0.c` | none on this branch (`new-instance Lx7` is the other branch, a `ShownWatcher` built at inflate) |

The hook argument list, its boxed `Float` and the libxposed chain object belong to the framework's
dispatch. The module cannot avoid them while the hook is installed. Entry 8 tried removing one hook
for most of the time and measured no gain.

## Layout paths (per task card layout)

`TaskCardButtonDecorator.onTaskViewLaidOut` → `place` → `TaskViewGeometry.thumbnailBounds` (merged
into `TaskCardHooksKt$$ExternalSyntheticLambda0.c`) still has these sites on every card layout:

- `monitor-enter` on the method cache (uncontended; only the main thread calls it);
- `new Rect()` for the measured bounds;
- `filled-new-array` with one `Object` to pass that `Rect` to the reflective `getThumbnailBounds`;
- `Method.invoke`.

Left in place. A layout probe measured about 11 calls per Overview cycle, averaging about 30 µs and
16.4 allocations each (≈0.2% of the cycle's main-thread CPU). The `Rect` and argument array are 2 of
those allocations; the rest are the button's own measure and layout (see `OPTIMIZATION_LEDGER.md`).

## Per-item paths

| Source method | Final method | Sites |
|---|---|---|
| Hidden-apps drawer predicate (`HiddenAppsFeature.shown` lambda) | `HiddenAppsFeature$$ExternalSyntheticLambda2.test` | `Method.invoke` with the shared `Invoke.NONE` array (inlined; no `new-array` or `copyOf`); `Boolean.valueOf` from `runCatching` (cached `TRUE`/`FALSE`, no allocation); `Log.w` only on failure |
| `SharedPreferencesHiddenAppsStore.hidden` | `SharedPreferencesHiddenAppsStore.a` | the `split`/`filter`/set allocations and the new `Parsed` holder, only when the stored text changed; none on a hit (72,000 measured calls: 0 allocations each) |
| `HiddenSearchResults.hides`, `SearchTargets.read` | merged into `AppDrawerSearchFeature.e` | per result batch: the kinds array and the kept `ArrayList`; per result: one `SearchResult`, three `Method.invoke` with `Invoke.NONE`, and `Field.get`. The field is resolved once per result class (`getDeclaredFields` runs only on a cache miss). No iterator per result |

## Touch paths

| Source method | Final method | Sites |
|---|---|---|
| `DoubleTap.isSecond` | `DoubleTap.a` | none |
| Workspace `onTouch` hook body | `DoubleTapToSleepFeature$$ExternalSyntheticLambda0.c` | `Field.getInt` (cached `Field`); `Intent` and runnable only on a completed double tap; `Log.w` only on failure |

## Reflection

`Invoke.noArgs` is inlined everywhere it is used and passes the static empty array, so no
zero-argument reflective call allocates. All `getDeclaredMethod`/`getMethod`/`getDeclaredField`
lookups in the audited methods sit on cold paths: install, first call, or a cache miss.
