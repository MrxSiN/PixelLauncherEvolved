# Migration

Upgrading to 0.1.6 from any earlier release keeps everything: identity is unchanged, and the state
this release adds lives under new keys whose absence reads as the old behaviour.

- **Package and module identity:** `applicationId` `io.github.mrxsin.pixellauncherevolved`, namespace,
  entry class `my.github.MrxSiN.pixellauncherevolved.PixelLauncherEvolvedModule`, `module.prop`
  (`minApiVersion=101`, `targetApiVersion=102`, `staticScope=true`), `scope.list` and
  `java_init.list` are unchanged (`scripts/check-project.sh` asserts them).
- **Preferences:** the file name, location (device-protected storage, launcher data directory), every
  key and every value format are unchanged. `app_drawer_hidden_apps` is still one comma-separated,
  sorted string, and it is still read on every call. The new cache only skips re-parsing identical
  text, so a value written by an older version, a backup import or a reset is picked up as before.
- **Backup/import format:** unchanged (`SettingsBackupTest` passes).
- **Widget stacks (new state):** `home_widget_stacks` (bool, default false), `home_widget_stacks_sideways`
  (false), `home_widget_stacks_remember` (true), `home_widget_stacks_smart_rotate` (false),
  `home_widget_stacks_indicator` (int 0..2, 0) and `widget_stacks` (one text line, version 2:
  `2;shown:id@slot,id@slot|...`, launcher item ids and a time-of-day slot 0..4 each; version 1,
  `1;shown:id,id`, reads with every slot 0 and is written back as version 2 at the next change to a stack;
  anything else reads as no stacks, so a module older than this one shows each waiting widget on
  its own page). The backup carries the four switches and the dots choice as `widgetStacks`,
  `widgetStacksSideways`, `widgetStacksRemember`, `widgetStacksSmartRotate` and
  `widgetStacksIndicator`; a backup without them imports with their defaults. A
  missing key is no stacks, so an upgrade changes nothing. Item ids belong to this launcher
  database, so the stacks themselves are never exported. The launcher's database is only
  ever written through its own `ModelWriter`, the way a drag between pages writes it: every member
  stays a valid, non-overlapping widget row, a waiting member alone on its own page. Turning the
  switch off, Safe Mode, or removing the module shows those pages; turning it back on hides them
  again. A stored stack the launcher's items no longer support (a member gone, moved, or sharing its
  page) is written back without that member, and one whose widget on show is gone is taken apart;
  nothing is moved or deleted to make it fit.
- **Per-Mode choices (new state, 0.1.6):** keys suffixed with a Mode's id, each absent until a Mode
  sets it, absent meaning "use the main setting": `focus_with_ordinary_<mode>` (bool),
  `focus_hidden_apps_<mode>` and `focus_drawer_apps_<mode>` (comma-separated packages),
  `focus_search_hide_<mode>` (comma-separated search switch keys), `focus_icon_pack_<mode>` (a package
  or `:system`), `focus_blur_<mode>` (percent as text), `focus_shade_<mode>` (ARGB as hex text) and
  `focus_labels_<mode>` (a surface bit mask as text). `focus_orphan_<mode>` keeps a deleted Mode's
  pages (`screen,screen,...` then its last name) and `focus_name_<mode>` the name each Mode was last
  read with. None of these are in the settings backup, which, like Modes pages, is tied to this
  phone's Modes.
- **App labels (new state, 0.1.6):** `labels_hide_home`, `labels_hide_folders` and
  `labels_hide_drawer` (bool, default false), backed up as `hideLabelsOnHome`,
  `hideLabelsInFolders` and `hideLabelsInDrawer`.
- **Double-tap action (removed, 0.1.6):** the choice added during this cycle was taken out before
  release; backups no longer write `doubleTapAction`, and a file that has it still imports (the
  name is ignored). A home double tap turns the screen off, as in 0.1.5.
- **Layout copies (new state, 0.1.6):** up to two copies of the launcher's layout database, with the
  launcher's grid prefs and the module's grid and dock settings, in the launcher's
  `no_backup/ple_layout/`. They are taken only while the layout depends on the module (a custom grid,
  a hidden dock's row, more dock icons than the grid's own) and put back only on a restore request
  from Layout recovery.
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

- **App labels (new state):** one preference key in the same file, `home_app_labels`: JSON
  `{"version":1,"apps":[{"user","package","class","name","hideHome","hideDrawer"}]}`, sorted, with
  only entries that change something. A missing key or an unreadable or newer `version` reads as no
  labels, so an upgrade changes nothing. Keyed by component and user id together, so a work, private
  or cloned copy of an app has its own entry. Nothing is written to the launcher's database or icon
  cache: names are applied when a label is bound, so removing the module shows every app's own label
  at the next bind. An uninstalled app's entry is kept (a reinstall of the same component in the same
  profile gets it back); entries for a profile that no longer exists are dropped when the editor
  opens; **Reset custom labels** and Reset all tweaks remove the key. Backup: optional array
  `appLabels` (`package`, `class`, `profile` = `personal`/`work`/`private`/`clone`, `name`,
  `hideHome`, `hideDrawer`), still format `version` 1. The user number is never written: an import
  places each entry on this device's profile of that kind and drops kinds it does not have. Older
  files read with no labels; older module versions ignore the name.

- **App labels, Home screen only (new state):** bool `home_app_labels_home_only`, default false;
  backed up as `appLabelsHomeOnly`. A missing key reads false, so an upgrade changes nothing.
- **0.1.5, Modes page previews:** the files in `<credential-encrypted files>/focus-page-previews/`
  are caches. Pictures from earlier versions (`page-*`, `wallpaper-raw-*.webp`) are deleted on the
  first start; the wallpaper is kept as `wallpaper-<key>.jpg`, where the key is the wallpaper's
  colours, and is fetched again when they change. No preference key or format changed. Wallpaper &
  style must be in this module's scope for the wallpaper (it already is for the icon pack); without
  it the pages keep a plain backdrop.

