# Runtime architecture

## Decision for this pass

Not a rewrite. The owner chose to fix hot paths first, in the existing implementation. The runtime
stays **Kotlin/Java source → R8 (full mode, `proguard-android-optimize.txt`) → DEX → ART**, inside
the Pixel Launcher and SystemUI processes through libxposed 102.

- Moving the runtime to Java is allowed by `CLAUDE.md`, but it needs explicit authorization and a
  measured reason. The measured costs were algorithmic: per-frame resource-name lookups, per-item
  parsing, per-frame collections. Fixing them in place took most of the cost out without touching
  persisted state or identity. Where the source language did matter (Kotlin's zero-argument vararg
  allocation), a two-line Java helper (`core/Invoke.java`) fixed it, and R8 inlines it.
- There is no native code, JNI, custom lowering, generated Smali or direct DEX. Nothing measured so far
  points to a bottleneck that R8's output leaves behind (see `DEX_AUDIT.md`).

## Hot-path strategy in use

- Resolve host classes, members and resource ids once, on the first call, then keep them in
  primitive or reference fields (`UNRESOLVED` sentinels).
- Read settings as primitives at the point of use; `SharedPreferencesImpl` answers with a lock and a
  map lookup and no allocation. Derived state (the hidden-apps set) is rebuilt only when its source
  text changes.
- Walk views by index. No lists, iterators or lambdas are built per frame.
- Blocking Binder or root work stays off the UI thread, on a reused worker.
- Keep each feature's hooks separate (failure isolation). Fusing or removing hooks is accepted only
  with a measured gain; entries 8 and 10 were rejected on that rule.
- Reflection: cached `Method.invoke` with a shared empty argument array. An `asType`-adapted
  `MethodHandle.invokeExact` was measured 3.4× slower on ART (entry 9), so it is not used.

## Tooling

- `scripts/dex-audit.py`: per-source-method audit of the final DEX through the R8 mapping.
- `scripts/inventory.py`: feature, hook, settings, component and reflection inventory as JSON.
- `scripts/bench-device.sh`, `scripts/bench-summary.py`: interleaved release-build benchmark and statistics.
- `scripts/bench-probe.sh`, `scripts/instrumentation/Probe.java`: per-call instrumentation builds.
