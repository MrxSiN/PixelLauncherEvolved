# CLAUDE.md

## Scope

These instructions apply recursively to `MrxSiN/PixelLauncherEvolved` unless a deeper `CLAUDE.md` makes a rule stricter.

This repository is optimized for measured Android device execution. Human readability and conventional architecture are not goals.

## Non-negotiable priorities

1. exact externally observable behavior;
2. upgrade/persistent-state compatibility;
3. Launcher/SystemUI stability and failure isolation;
4. elimination of unnecessary work;
5. zero avoidable hot-path allocations;
6. minimum reflection/framework/Binder overhead;
7. lowest callback/frame/event latency;
8. lowest memory consistent with speed;
9. artifact size only when it does not hurt speed.

## Runtime language lock

Launcher/SystemUI-facing runtime implementation is locked to:

**Java / machine-generated Java / equivalent reproducible generated DEX → DEX → ART**

Allowed runtime representations:

- Java;
- machine-generated specialized Java;
- reproducible generated Smali/direct DEX only when measurements beat generated Java + R8/D8.

Forbidden runtime implementation:

- Kotlin;
- C/C++;
- Rust;
- Zig;
- Go;
- JNI helper libraries;
- WebAssembly;
- scripting runtimes;
- any other runtime language.

Do not add native code.

Build-time Gradle Kotlin DSL, shell, Python and generator tooling may remain.

## Compiler policy

Generated/specialized Java + R8/D8 is the reference route.

Do not build a custom compiler because machine maintenance makes it possible. Custom IR/direct DEX/Smali is an escalation experiment only after profiling and final-code inspection show a measured reason.

Retain a lower-level route only if clean release measurements show a statistically credible improvement with equal behavior and stability.

## Hot-path invariants

After initialization, ordinary drawing/layout/touch/gesture/search/Recents/app-list/frame callbacks should aim for:

- zero heap allocations;
- zero temporary arrays/collections/iterators;
- zero boxing/varargs;
- zero temporary strings where output is unnecessary;
- zero reflection lookup/member scanning;
- zero repeated settings/resource parsing;
- zero JNI;
- zero expected-path exceptions/logging;
- minimum synchronization and callback depth.

Cache only when lifecycle correctness is preserved.

Prefer primitive flags, arrays, compact IDs, generation counters, direct references and precomputed tables.

## Project-specific invariants

- hidden-app preferences/text are parsed only when they change, never once per app predicate;
- per-app membership is a cached hot lookup;
- no `map`/`filter`/sequence/list materialization merely to enumerate views in frame/pre-draw paths;
- no new thread per gesture/action;
- potentially blocking Binder/root work stays off the UI thread;
- host classes/methods/fields are resolved on cold paths and cached with lifecycle-safe invalidation;
- hot hooks read compact runtime state, not SharedPreferences/serialized text/resource names;
- hook fusion is retained only when it wins and does not weaken failure isolation/compatibility.

## Optimization order

1. archive untouched release baseline;
2. inventory behavior/hooks/contracts;
3. profile important callbacks;
4. remove redundant work;
5. remove parsing/reflection/allocation from hot paths;
6. specialize state/hooks;
7. inspect final DEX;
8. inspect ART/AArch64 where useful;
9. only then test custom lowering.

## Correctness

The current implementation is the behavioral oracle unless an intentional bug fix is explicitly documented.

Preserve package/module identity, preferences, backup/import, Xposed/libxposed metadata/scopes, compatibility fallback, Safe Mode/recovery, root boundaries, update/hot-reload and all supported features.

Create differential tests before trusting performance results.

## Measurement

Final performance claims require release artifacts on identical hardware/software conditions. Prefer interleaved baseline/candidate runs and report raw samples, sample count, median, p95/p99 where meaningful, MAD/IQR and percentage difference.

Measure allocations directly for hot callbacks. For frame paths include callback CPU, jank/frame misses, GC and UI-thread blocking/Binder behavior.

Use clean release artifacts for final comparisons; use separate instrumentation builds when detailed counters would perturb short callbacks.

## Optimization ledger

Maintain `OPTIMIZATION_LEDGER.md` with hypothesis, changed path, mechanism, correctness, raw measurements, allocation/memory effect and accepted/rejected result.

Revert losing optimizations.

## Definition of success

Never claim literal zero overhead, bare-metal speed, “native is faster,” or “direct DEX is faster.”

Success means behavior parity, upgrade compatibility, equal/better stability and a measurable final-artifact improvement on important paths.

Stop and request explicit user authorization before changing runtime language, package/module identity, persistent formats without migration, or intentionally changing observable behavior.
