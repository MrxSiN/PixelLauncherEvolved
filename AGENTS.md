# AGENTS.md

## Scope

These instructions apply recursively to `MrxSiN/PixelLauncherEvolved` unless a deeper `AGENTS.md` makes a rule stricter.

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

**Kotlin / Java / machine-generated Java → R8 → DEX → ART**

Allowed runtime representations:

- Kotlin (the existing implementation language; the owner chose to stay in Kotlin);
- Java, including small helpers where Kotlin's output measurably costs more (`core/Invoke.java`);
- machine-generated specialized Java;
- reproducible generated Smali/direct DEX only when measurements beat generated Java + R8/D8.

Forbidden runtime implementation:

- C/C++;
- Rust;
- Zig;
- Go;
- JNI helper libraries;
- WebAssembly;
- scripting runtimes;
- any other runtime language.

Do not add native code. The one authorized exception is DexKit's `libdexkit.so`, loaded only
on a host-symbol miss or while recording fingerprints (`core/HostDex.java`), never on a hot path.

Porting the runtime between Kotlin and Java needs explicit owner authorization and a measured reason.

Build-time Gradle Kotlin DSL, shell, Python and generator tooling may remain.

## New-code language rule (owner decision, 2026-11-11)

- Existing Kotlin/Java stays as it is.
- Kotlin is kept. New code is written in XAX where XAX can reach the target. That includes code that
  replaces removed or rewritten Java, shell or Python. Use the latest XAX and XAX-MCP checkouts.
  (Brainfuck was dropped on 2026-11-12.)
- Where XAX reaches, as of 2026-11-12: standalone executables for Windows x86-64 (build-host tooling)
  and Linux AArch64 (on-device root helpers), and separate DEX/APK/libxposed artifacts. There is no
  route yet for XAX to link into this Kotlin + R8 module or call the launcher's classes in-process.
- If a change needs new code where XAX cannot run, such as libxposed hooks or reflection into
  launcher/SystemUI classes, stop. Report what is blocked and ask the owner. Never fall back to
  Kotlin/Java silently.
- Exceptions the owner granted are listed here.
  - 2026-11-11: Kotlin/Java for the CP4A fix (`MinSpans`, the `HostDex` recorder and self-test, the
    compatibility report key, and latest-first contracts and lookups).
  - 2026-11-12: Kotlin for the Modes dump source (CP4A `contextual_mode`), for routing direct
    reflection through `Reflect`/`Host` so DexKit covers it, and for the Layout Recovery page redesign.
  - 2026-11-12: Kotlin for the At a Glance / Smartspacer integration (launcher hooks for At a Glance
    placement on home screens and Mode pages, and its settings page).
  - 2026-11-13: Kotlin for opening the module's full-screen pages (At a Glance, Reorder home screens,
    Assign home screens to Modes and each Mode's page, App labels) as real Pixel Launcher Settings
    screens (`PageHost`), which fixed their exit transitions.
  - 2026-11-13: Kotlin for the update check: the module app holds the Internet permission and sends one
    `HEAD` to the GitHub `releases/latest` redirect when the launcher asks, once per launcher start; the
    launcher shows a dialog for a newer release. On by default, with a switch in settings.
- Every other rule in this file still applies to XAX code: behavior parity, failure isolation,
  hot-path invariants and measurement.

## Engineering principles

- SOLID: single responsibility, open/closed, Liskov substitution, interface segregation, dependency
  inversion.
- DRY, KISS, YAGNI, Law of Demeter, separation of concerns, Boy Scout rule.
- No hardcoding anywhere in the project. Resolve host classes, members and values dynamically,
  through `Reflect`/`Host` with DexKit (`core/HostDex.java`) as the fallback, or from data files.
  Never use version-specific literals in feature logic.

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
- primary contracts in `LauncherContracts` and the first lookup in code are the latest Android and
  launcher build's names; every earlier build is an alternative/fallback. On the latest build the
  Compatibility & diagnostics report shows no fallback.

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

<!-- ecosystem-policy v1 (2026-11-13): identical in every MrxSiN first-party repository. Change it everywhere in one coordinated set of PRs, never in one repository alone. -->
## Ecosystem rules (v1)

Shared by MrxSiN's first-party repositories. Any other rule in this file that is stricter or more specific to this repository wins.

- **Who owns what.** XAX owns the semantic graph, verifier, compiler, ABIs, backends, targets and artifacts. BBC owns the Brainfuck frontend, BFX and Brainfuck-specific optimization. XAX-MCP owns MCP transport, tool schemas, sandbox and permissions. Dependencies point one way: consumers -> XAX-MCP / BBC -> XAX.
- **New production logic** is XAX, or Brainfuck + XAX, wherever the toolchains reach the target; this repository's own language rules say where that is. Existing code in other languages is tracked migration debt, not permission to add more. Declarative config, assets, fixtures and licenses are not code to convert.
- **Upstream first.** When a toolchain lacks something this repository needs: reproduce it on the toolchain's latest `main`, search its issues and PRs, then fix a small bounded gap with a PR in the owning repository, or open (or link) an issue for a large one and mark only the affected work blocked-upstream. No consumer-side shims, private toolchain forks or local toolchain patches.
- **Pins.** Build against exact toolchain commits, recorded in one machine-readable place. Check the toolchains' latest commits before nontrivial work; move a pin by reviewed PR once checks pass, and write down why when an older pin stays.
- **No hardcoding** of secrets, personal or machine paths, toolchain locations, obfuscated host names or version-specific offsets. Machine-local paths belong in your own `~/.claude/CLAUDE.md` or `CLAUDE.local.md`, never in the repository. Specification constants (opcodes, ABI layouts, protocol markers, security limits) are fine when named and documented.
- **Design.** SOLID, DRY, KISS, YAGNI, Law of Demeter, Boy Scout Rule and Separation of Concerns, applied with judgment. They never override correctness, safety, verified state or evidence quality.
- **Evidence.** Report only what actually ran: EXECUTED, MEASURED, NOT RUN or BLOCKED, with the reason. Emulator, Wine or qemu runs are not device runs. Never rewrite old evidence; add new.
- **Git.** Work on a branch from the default branch and land it by pull request; the owner reviews and merges. No force-push to the default branch, no history rewrites. No AI attribution in commits, PRs or comments; commits use the repository's git identity. PR titles say what changed in plain words.
- **Security.** Report vulnerabilities privately, never in a public issue. Never print or commit tokens.
<!-- /ecosystem-policy v1 -->
