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
- **Dock (new state):** keys in the same file: `home_dock_hidden` (bool, default false),
  `home_dock_icons` (int, 0 = System), `home_dock_move_to_home` (bool, default true, backed up as
  `dockMovesToHome`) and `home_dock_kept` (int, device-local: dock slots still holding apps a lower
  count hid; not backed up and not reset). A hidden dock adds one Home screen row
  (`InvariantDeviceProfile.numRows`); the launcher's recorded grid (`DeviceGridState`) is kept on its
  own rows, so no grid migration runs. **Removing or disabling the module with the dock hidden
  deletes Home screen items in the extra row at the next load**, as it does dock apps past the grid's
  count; show the dock first. A missing key reads as its default, so an upgrade changes
  nothing. A count above the grid's raises `numDatabaseHotseatIcons` while the module runs (the
  launcher logs "Migration is not needed"; nothing is rewritten). Lowering the count moves the
  pinned apps past it to the Home screen through the launcher's `ModelWriter`, the only launcher
  database write this feature makes. **Removing or disabling the module with a count above the
  grid's deletes dock apps past the grid's count** (`LoaderCursor`, at the next load); settings
  warns about this when such a count is chosen. Reset moves them first. Backup names `hideDock` and `dockIcons` are optional; a count the target grid
  cannot take is stored as written and read as System there.
- **Providers:** `.focus` and `.screenlock` authorities, their method names and result keys are unchanged.
- **Upgrade path:** tested on device by `adb install -r` over the installed v0.1.0 and then back and forth
  between v0.1.1 and the candidate eight times. Afterwards the settings file still held the same 24
  entries, and the values checked were unchanged.
- **Version:** `versionCode` 12 / `versionName` 0.1.1 are unchanged in the working tree. A release of
  this pass needs the usual bump.
- **Grid & size (new state):** int keys in the same file: `home_grid_columns`, `home_grid_rows`,
  `app_drawer_columns` (0 = the launcher's own), `home_grid_icon_size` (percentage, 100 = own; read
  snapped to 85/100/115/130), `home_grid_spacing_x`, `home_grid_spacing_y` (-1/0/1). Logical values
  only, no pixels and no workspace ids. A missing key reads as its default, so an upgrade changes
  nothing. A custom column/row count gives the launcher a database named `launcher_ple_<c>_by_<r>.db`
  (`ple_` keeps it apart from Google's `launcher_<c>_by_<r>.db`; the launcher's backup agent copies
  it like its own). The launcher's own migration fills it from the previous database, which is never
  written to; returning to the launcher's grid migrates back (unchanged items keep their original
  cells and spans) and the module's databases are deleted once that migration is checked. The
  launcher only migrates between Google's sizes, so the module lets its own sizes through
  (`GridMigrationOption`). **Removing or disabling the module with a custom column/row count leaves
  the launcher unable to migrate back**: it loads the custom database under its own grid and drops
  what lies outside; Restore defaults first (settings says so). Backup: optional object `grid`
  (`columns`, `rows`, `iconSize`, `spacingX`, `spacingY`, `appDrawerColumns`), still format
  `version` 1; older files read with every value at default, older module versions ignore it. An
  import re-fits counts to the importing screen and falls back to the launcher's own when a count
  does not fit or a Home screen widget could not fit the imported grid. Reset puts every key back
  through the same live path. Safe Mode stores the values it reset as `key=value` entries in
  `safe_mode_disabled`.
