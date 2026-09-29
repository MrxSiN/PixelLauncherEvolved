# Migration

Nothing in this pass changes identity or stored state, so upgrading from any earlier release keeps
everything.

- **Package and module identity:** `applicationId` `io.github.mrxsin.pixellauncherevolved`, namespace,
  entry class `my.github.MrxSiN.pixellauncherevolved.PixelLauncherEvolvedModule`, `module.prop`
  (`minApiVersion=101`, `targetApiVersion=102`, `staticScope=true`), `scope.list` and
  `java_init.list` are unchanged (`scripts/check-project.sh` asserts them).
- **Preferences:** the file name, location (device-protected storage, launcher data directory), every
  key and every value format are unchanged. `app_drawer_hidden_apps` is still one comma-separated,
  sorted string, and it is still read on every call. The new cache only skips re-parsing identical
  text, so a value written by an older version, a backup import or a reset is picked up as before.
- **Backup/import format:** unchanged (`SettingsBackupTest` passes).
- **Icon packs (new state):** new preference keys in the same file: `home_icons_use_pack` (bool,
  default false = System), `home_icons_pack` (package name), `home_icons_generation` (int, only ever
  rises; part of the launcher's icon freshness key), and `home_icons_overrides` (one sorted text line,
  `user|package/class=pack:drawable;...`, an empty value meaning the stock icon). A drawable applies only while the pack it was chosen from is active. Drawables are stored
  by name, not resource id, so a pack update does not invalidate them. A missing key reads as its
  default, so upgrading from a version without icon packs changes nothing. Compiled pack indexes live
  in `<device-protected files>/ple_icons/<package>.<format>.idx`, keyed by pack version and
  `IconPackFormat.VERSION`; they are caches and are rebuilt when missing or stale. Nothing is written
  to the launcher's own database: the launcher regenerates its stored icons itself when their
  freshness key changes, and does so again when the pack is switched off or the module removed.
  Overrides for an uninstalled app are kept, so a reinstall of the same component in the same profile
  gets its icon back; Reset icon overrides and Reset all tweaks clear them.
- **Xposed scope:** `scope.list` gains `com.google.android.apps.wallpaper`, for the icon pack
  entry only. The module stays `staticScope`, but the framework does not add a new scope entry to an
  installed module by itself: enable Wallpaper & style for this module in the manager (on Vector,
  `vector-cli scope add io.github.mrxsin.pixellauncherevolved com.google.android.apps.wallpaper/0`).
  Without it the launcher still draws the chosen pack; only the place to choose one is missing.
- **Icon pack settings:** the Icons page left Home settings. The stored keys are unchanged, so a
  pack chosen there stays chosen.
- **Backup format:** still `version` 1. New optional names `useIconPack`, `iconPack` and
  `iconOverrides`; older files read with icons left to the system, and older versions ignore the
  new names.
- **Providers:** `.focus` and `.screenlock` authorities, their method names and result keys are unchanged.
- **Upgrade path:** tested on device by `adb install -r` over the installed v0.1.0 and then back and forth
  between v0.1.1 and the candidate eight times. Afterwards the settings file still held the same 24
  entries, and the values checked were unchanged.
- **Version:** `versionCode` 12 / `versionName` 0.1.1 are unchanged in the working tree. A release of
  this pass needs the usual bump.
