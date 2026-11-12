# Behavior parity

Oracle: `031d5e5` (v0.1.1). Candidate: the working tree with ledger entries 1–7 (entry 8 was
reverted). The full feature and hook list comes from `python scripts/inventory.py`
(`docs/inventory.json`): 23 features, 18 settings, 2 exported providers, 66 hook sites.

Evidence used below:

- **tests:** all 117 unit tests pass (114 existing, plus 3 in `HiddenAppsStoreTest`); `scripts/check-project.sh` passes.
- **device:** the candidate ran on a Pixel 8 Pro (CP3A.260905.009) during the benchmark runs in
  `PERFORMANCE.md`. The launcher did not restart during any measured workload (the script checks
  the pid). All 22 enabled or always-on features logged `Installed <id>` at start; `tablet_mode` was off.
- **reading:** changed code was reviewed line by line against the original for evaluation order,
  short-circuiting and side effects. The reasoning is in `OPTIMIZATION_LEDGER.md`.

| Feature | Changed? | Evidence |
|---|---|---|
| `app_drawer_hidden_apps` | yes (entries 1, 2) | tests (parse, cache hit, change after `hide`), reading, device: drawer rebuilt 72,000 predicate calls with the same results |
| `app_drawer_hide_apps_picker` | reflective call only (entry 2) | device: picker opened from Home settings, ticks drawn, left without confirming, nothing written, ticks gone |
| `app_drawer_search_results` | yes (entries 2, 7) | `SearchResultKindTest`, reading |
| `app_drawer_search_web_app` | no | tests |
| `compatibility_watch` | no | — |
| `focus_home_screens` | no | tests |
| `home_blur_wallpaper` | yes, intentional fix | the workspace blur ramps with the app drawer instead of snapping to the resting blur on its first frame (HOOK_NOTES, "the workspace blur follows the wallpaper's full radius"); screen recordings before, after and with the tweak off; `HomeBlurDepthTest` |
| `home_double_tap_to_sleep` | yes (entry 6, worker thread) | device: 48 double taps across both variants, every one turned the screen off and logged `screen off`; worker thread persists after use |
| `home_search_opens_drawer` | no | — |
| `home_widget_stacks` | new feature | Intentional new behavior, off by default. Off, the touch hooks return after one array length check and the bind filter shows every page (`WidgetStacks.onBind` clears the stacks), so the home screen is stock. `focus_home_screens` changed only to leave the stacks' pages out of what it binds and catalogues, and out of where a new app is placed; with no stack the lists it works on are the same. Tests: `StackGraphTest`, `LauncherContractsTest`, `SettingsBackupTest`, `FeatureRegistryCompatibilityTest`. Device: create (menu, picker, and drag onto a widget, with size adoption), Add widget, bounded swipe, list-scroll arbitration, restart, Show, Edit stack, Remove widget, Restore standalone widgets, sideways (bounded, no page turn or long press), switch off and on again PASS; TalkBack, Smart rotate and resize not run (HOOK_NOTES, Widget stacks) |
| `home_icon_pack` | new feature | Intentional new behavior, off by default (source = System). With System, every hook is the launcher's own call, a volatile read and a return, so icons are the stock icons (device: switching Pack → System restored every stock and themed icon live). Tests: `IconOverrideFormatTest`, `IconPackIndexTest`, `SettingsBackupTest`, `ContractAnalyzerTest`. Device matrix below |
| `launcher_settings` | no | device: module section present, rows opened |
| `overview_actions` | yes (entry 3) | reading. Device: row on screen in every Overview cycle, launcher stable. Hiding a button was not switched on during the runs |
| `overview_actions_motion` | yes (entry 4) | `OverviewActionsEntranceTest`, reading, device |
| `overview_bubble_button` | yes (entry 5) | reading, device |
| `overview_clear_all_in_actions` | no | — |
| `overview_hide_taskbar_all_apps` | reflective call only (entry 2) | reading |
| `overview_only` | reflective calls only (entry 2) | reading; the feature was off on the device |
| `overview_split_button` | yes (entry 5) | reading, device |
| `safe_mode` | no | — |
| `status_bar_double_tap_to_sleep` | no | — |
| `tablet_mode` | no | — |
| `taskbar_home_visibility` | no | — |
| `taskbar_only` | no | device (on during all runs) |
| `taskbar_transition` | no | — |

Intentional behavior changes: none. One difference is not observable to the user: the double tap
to sleep call now runs on a pooled thread named `PixelLauncherEvolved-sleep` instead of a new
`Thread-N`. After the first use one parked daemon thread stays in the launcher. Overlapping calls
still run in parallel, as before.

## Icon packs in Wallpaper & style (device, 2026-09-27)

- the Icon pack entry sits right after the system's Icons entry on the Home screen tab, says the
  chosen pack and shows its icon; it is absent from the Lock screen list;
- the page shows the launcher's own live home preview, System and five installed packs as tiles;
  tapping Monoic applied it, the tile morphed to selected, and the preview and the main page's
  preview both showed Monoic's icons;
- Per-app icons lists every app with the launcher-drawn icon; the choice sheet lists the pack's
  drawables with pictures (cancelled, nothing changed);
- with Pixel Lock Screen Evolved active in the same picker, its Lock screen rows (Depth effect,
  Always-on display, Compatibility & diagnostics) were all present and the list ended cleanly.

## Icon packs (device, Pixel 8 Pro, launcher 907, 2026-09-27)

Verified on device with a real pack (Simply Minimal Icons, 24,169 components, 326 calendars) and a
generated test pack (`scripts/test-icon-pack.sh`, v1 and v2):

- pack chosen: home, hotseat, drawer and search show the pack artwork live, without a restart;
  unmapped apps keep the stock (and themed) icon;
- themed icons on: pack artwork keeps its own colours, unmapped apps stay themed;
- dynamic calendar: the calendar app shows the drawable for the day of the month;
- per-app override: only that app's icon changes, in about 2 s, with no placeholder flash elsewhere;
  choosing System icon for one app restores its stock icon;
- System: every icon returns to stock live;
- pack updated (v1 → v2): the recoloured icon appears in about 1 s;
- pack uninstalled: icons fall back to stock and the selection is kept; reinstalling brings the pack back;
- launcher restarted: the pack applies from its cached index before the first icon is drawn;
- a pack contract naming bug (`String[]` written as `java.lang.String[]`) made the feature
  unavailable on 907; fixed and covered by `ContractAnalyzerTest`.

Not verified on device in this pass: work profile / Private Space icons (no work profile on the test
phone), RTL, font and display size extremes, landscape, tablet/foldable layouts, TalkBack traversal of
the new pages, 3-button navigation, Safe Mode with the feature on, and upgrade from the published
v0.1.2 APK with icon state present (there is no earlier release with icon state).

Not yet covered by an automated differential test: the Overview action-row walk (entry 3) and the
task-card button lookups (entry 5). The JVM unit tests run against stub `View`s that throw, so these
need an instrumented test or a Robolectric setup.

## Dock (device, Pixel 8 Pro, launcher 907, 2026-09-30)

New feature, `home_dock`, stock by default (Show dock on, System count): the `setInsets` hook
leaves the container VISIBLE and no count hook is installed. Verified on device:

- Home screen → Dock page drawn with the module's own rows; Show dock toggled off hid the four dock
  icons live and kept the search bar; the hidden area refused a drop and launched nothing on a tap;
  the hotseat rows in `launcher_4_by_6.db` were unchanged; hidden survived a launcher restart;
- 3 icons: the dialog named Camera; after the restart the dock showed three icons, Camera kept its
  row; System brought four back;
- Re-run 2026-09-30 in the default phone layout (no taskbar): hide live, tap-through, drop refused, drawer swipe, hidden across restart, 3 icons with the singular dialog, System back; hotseat rows unchanged throughout.
- Taskbar only on: Show dock greyed out with "Not available while the taskbar is on", and the dock
  stayed visible with the stored setting on.

- Slider (2026-09-30, phone mode): steps 3–5 on the 4x6 grid, summary "4 icons, Pixel Launcher's
  own"; 5 applied after restart with five icons; Journal pinned at slot 5; back to 4 showed "Journal
  doesn't fit in a dock of 4 and will move to your Home screen", Cancel kept 5, Move put Journal on
  page 0 and it survived the restart; Reset all tweaks at 5 moved it the same way. Launcher data and
  module settings were restored from a backup afterwards.

- Live (2026-10-01, phone mode): 7, 6, 5, 4 and 1 icons applied from the slider without the launcher
  restarting (same pid); shrunk cells and icons at 6 and 7, suggestions included; moves on 6 → 5 and
  4 → 1; Show dock toggled live. Launcher data and module settings restored from a backup afterwards.

- Hidden dock row, Default label and Move to Home screen (2026-10-01): as in HOOK_NOTES; launcher
  data and module settings restored from a backup afterwards.

Not verified: a widget reaching into the extra row, tapping and launching from a shrunk dock icon (cells were checked for no overlap, not
tapped), a folder in a dock of 6 or 7 (scaled, not seen), the "no room on Home screen" path, a hotseat folder at a moved slot, landscape/vertical dock, foldable/tablet, RTL, TalkBack, 3-button navigation, work
profile, import of a backup onto a different grid. Tests: `DockIconsTest`, `SettingsBackupTest`,
`LauncherContractsTest`, `FeatureRegistryCompatibilityTest`.

## Grid & size (device, Pixel 8 Pro, launcher 907, 2026-10-01)

New feature, `home_grid`, stock by default: at defaults the launcher logs the same `initGrid`
(`launcher_4_by_6.db`) and "Migration is not needed" as before. Verified on device, launcher data
backed up first:

- Page: Home screen → Grid & size, the launcher's preview card, Columns 3–6 and Rows 3–8 measured
  for this screen, the launcher's own counts labelled "Default".
- 4 → 5 columns live: `launcher_4_by_6.db` → `launcher_ple_5_by_6.db`, "every item arrived"; Home and
  preview showed 5 columns; widgets took their smallest spans (the launcher's own migration rule).
- Large icons and 7 rows live (5 × 7; the launcher's taller-grid rule moved items down one row);
  3 columns accepted (every widget's smallest span fits) and verified.
- All apps 6 columns live, suggestions row with 6; Compact/Relaxed spacing applied.
- 5 columns survived a cold launcher restart (no migration).
- Restore defaults: back to `launcher_4_by_6.db`, module databases deleted; the 43 rows of the
  original database matched cell for cell and span for span, and all four widget ids stayed bound.
- Cold-start way back (setting changed while the launcher was stopped): migrated back through the
  remembered source size, again identical to the original.

Not verified: the refusal dialog (no widget on this device is too wide for 3 columns), the
rollback after a lossy migration (not reproducible on purpose), Safe Mode with a custom grid,
landscape/fixed Landscape mode, Full tablet layout, foldable, RTL, TalkBack, font/display size
extremes, 3-button navigation, work profile, a hidden dock combined with a custom grid, Wallpaper &
style previews of other grids while a custom grid is set, import onto another device. Tests:
`GridSpecTest`, `SettingsBackupTest`, `CrashGuardTest`, `LauncherContractsTest`,
`FeatureRegistryCompatibilityTest`.

## App labels (device, Pixel 8 Pro, launcher 907, 2026-10-13)

New feature, `home_app_labels`, stock by default: with no label stored the feature logs "none
chosen; nothing hooked" and installs no hook, so every bind, sort and search is the launcher's own.
Verified on device:

- App labels page (moved to App drawer → Apps on 2026-10-13) with Per-app labels and Reset custom labels (greyed with none);
  the editor lists every app with icon, searchable; the dialog shows the app's own name and
  component.
- Calculator renamed "Zeta Math": the first label installed the hooks live (no restart) and the
  launcher reloaded in 0.7 s; the drawer shows it last, under Z, after YT Studio and before Zoom;
  typing "zeta" and "calcu" both find it; its node reads text "Zeta Math", description "Zeta Math,
  Calculator". Survived a launcher restart.
- Hide label in app drawer: the label is empty and the icon keeps its place; TalkBack text unchanged.
- WhatsApp (dock) renamed "WA": description "WA, WhatsApp has 1 notification". Keep Notes with Home
  label hidden: empty label, icon in place, description "Keep Notes".
- Reset custom labels: every label back, key removed, row greyed.

Not verified: work profile / Private Space / clones (none on the test phone), folder contents and
the drawer suggestions row with a renamed app, RTL, font and display size extremes, landscape,
foldable/tablet/taskbar layouts, Safe Mode with labels set, TalkBack gestures (node text read with
uiautomator only), export/import on device, desktop mode where Pixel Launcher hides Home labels
itself. Tests: `AppLabelFormatTest` (format, profiles kept apart, bounds, store publish, backup by
profile kind), `LauncherContractsTest`, `SettingsBackupTest`.

Notes: the search box's inline completion shows the shown name ("calcu Zeta Math"), because the
launcher completes from the result's label. Pixel Launcher offers no global label setting on a
phone in 907 (its `WORKSPACE_ITEMS_LABEL_HIDDEN` is set only in desktop mode, where cells are sized
without a label line), so there is no "follow" row: an app's label follows Pixel Launcher unless it
is hidden for that app, and hiding is the only per-app override.

### App labels: Home screen only and Edit Name (device, 2026-10-13)

- Long-press on a Home icon (Keep Notes): **Edit Name** right after Pause app, launcher icon and the
  launcher's own translated "Edit Name"; it opened the editor over the Home screen; "Notes" applied
  live (reload 0.36 s), description "Notes, Keep Notes". Long-press in the drawer: the same row after
  Pause app. Folders, shortcuts and widgets get no row.
- **Rename on Home screen only** on: Home still "Notes"; drawer search for "keep" lists "Keep Notes".
  Switched off and labels reset afterwards; settings file equal to the pre-test copy apart from the new
  key (false) and one Safe Mode crash timestamp from a failed first attempt (see HOOK_NOTES).

## Modes page previews (device, Pixel 8 Pro, `CP3A.261005.005`, launcher 907, 2026-10-20)

Intentional changes, owner-reported or measured:

- **Pictures:** pages are drawn by the launcher's own preview, not rebuilt from the model. Verified:
  At a Glance content, labels, system icons (pack off), dock and search bar match the live home.
  Pictures are redrawn on every opening (previously only after a workspace bind), so widget content
  is current; the previous picture stays until replaced.
- **Wallpaper:** read from the wallpaper file through Wallpaper & style, not photographed off the
  display. Before: a gradient (no capture was ever kept) and a 160 ms blank home six seconds after
  every return home. After: the display's crop (bridge picture 1258 × 1400 against a 2017 × 2245
  crop, r 0.991), blurred once at home's radius; no blank frame after the opening animation. Each page
  shows its own section: Work page with Work on (< 1 % shift, r 0.988); a two-page ordinary home
  (`mWallpaperX` 0 / 0.333, each tile matching its own page). Live wallpapers keep the gradient.
- **Opening:** the Modes are read off the main thread; a second tap while they are read is dropped
  (before, the main thread blocked so a second tap waited behind the dialog).
- **Lifecycle:** after a launcher recreate the old activity is collected (Activities 1, was 3 after
  two recreates). Overview buttons, the Recents card buttons and the Custom grid panel behave as
  before.

Not verified: RTL (needs an RTL system language), live wallpapers beyond the gradient, a Mode with
several pages, work profile pages.

## Earlier launcher generation (issue 1, 2026-11-03)

Oracle: `c793e9e` (v0.1.6 and one fix). Candidate: the working tree with the
earlier-generation fallbacks (HOOK_NOTES, "Two launcher generations").

- **Pixel 8 Pro (`CP3A.261005.005`, launcher 907), release build:** 307 / 307 contracts resolved,
  0 by fallback, 0 failed; all 29 features logged `Installed <id>`, no warnings; the owner's Taskbar
  only stayed on (`isTaskbarPresent=true`); the Overview row hooked `updateForGroupedTask`, the
  primary. Every lookup tries the `CP3A` name first, so on this launcher no fallback is reached.
- **Intentional changes, all on launchers missing members:** Overview only and Full tablet layout
  are shown unavailable where they used to be reported compatible and fail to install; a layout mode
  whose feature failed is never read as on (its switch is kept); a Grid & size part or the dock's
  icon count that fails halfway takes its hooks back. On a launcher of the earlier layout, the dock,
  its icon count, All apps columns, Overview actions, Overview only, Taskbar only, the search bar
  and spacing install through that layout's own members. None of that was run on such a launcher.
- **Tests:** `LauncherGenerationsTest` (three fixtures), `DeviceProfilesTest`,
  `TabletModeFeatureTest`, new `HookRecorderTest` and `ContractAnalyzerTest` cases; 250 unit tests pass.

## At a Glance placement (2026-11-12, intentional change)

- Before: At a Glance is the launcher's in-memory `SmartspaceItemInfo` on screen 0. A Mode whose
  home screens hide screen 0 took At a Glance away with it.
- Now: with Modes on and screen 0 hidden, a copy shows on the Mode's first page when its top row
  (the item's span) is free. With **Show on every home screen** on, a copy shows on every other
  shown page whose top row is free. Off (default) and no Mode on: unchanged, nothing is added.
- Copies are bound only; ids are negative (`GlancePlacement.copyId`) so no database row or resource
  id can match. The database and the shared model are never written.
- Device, Pixel 8 Pro, `CP4A.261105.040.A1`, launcher 907 (test Mode: Do Not Disturb given page 7,
  whose top row is free; prefs restored byte for byte afterwards, md5 `b467d97c…`):
  - DND on, main only: one page (7) with a copy (view id `#fffefff9`) showing the same weather card
    and pager dots; tap opened Google Weather, long-press showed Dismiss / Settings / About this content.
  - DND on, **Also show regular home screens** + every home screen: page 0 with the launcher's own
    item (`app:id/search_container_workspace`) and page 7 with a copy.
  - DND off: page 0 only, the launcher's own item, no copy left behind.
  - Per-Mode `focus_glance_MANUAL_RULE=all` with the Home settings switch off: page 0 with the
    original and page 7 with a copy.
  - Home settings' own At a Glance switch, Turn off: on this build the item stays and every view,
    copies included, changed live from the weather card to the date alone. Turn on brought the
    weather back. So copies receive content updates.
  - Test trap: editing the prefs file right after `force-stop` races the home activity's automatic
    restart, which then runs on the old values. Write the file first, then `force-stop`.

### With Smartspacer (device, 2026-11-12)

- Smartspacer 1.11.5 from its GitHub release, Enhanced Mode through Shizuku (already running as
  root), Native Smartspace on (SystemUI restarts once; the phone was unlocked by hand).
- Targets: Smartspacer's built-in At a Glance (Smartspace). Complications: Health Connect add-on,
  Distance, whose own setup screen ran and returned to Smartspacer.
- Home: the launcher's own At a Glance showed the date with "1203m" from the add-on. With Do Not
  Disturb given page 7, the copy on page 7 showed the same. No module change was needed: copies are
  the launcher's own view, fed by the same Smartspace session Native Smartspace replaces.
- Google's weather card did not come through Smartspacer on `CP4A.261105.040.A1`: neither its
  At a Glance (Smartspace) target, nor its At a Glance (Widget) target, nor a restart of Smartspacer
  brought it back. Both targets are Smartspacer's; this module only draws what the system Smartspace
  session hands the launcher.
- Turning Native Smartspace off put the system service back on Android System Intelligence, but the
  weather stayed away until ASI itself was restarted (`am force-stop com.google.android.as`, then
  the launcher). ASI not answering may also be why Smartspacer's At a Glance targets were empty.
- Smartspacer's process dying drops Native Smartspace: At a Glance went blank on home until it was
  re-enabled (which restarts SystemUI). Smartspacer posts a "Re-enable Native Smartspace" notification.
- Not tested: the Tasker add-on (needs a Tasker profile), RemoteViews targets, work profile.

### Native At a Glance content (device, 2026-11-13)

- Next alarm extra: "7:30 AM" beside the weather; set to Mornings after midnight it disappeared.
- Battery level extra: "47%" with the battery icon beside the weather.
- Torch card: added as its own page while the torch was on; once the merge skipped a card whose
  feature type the system already sent, only Pixel's own "Flashlight on" card showed.
- Greeting ("Good night") and Clock ("12:52 AM" with a clock icon) cards: pages 2 and 3.
- Calendar sources: greyed out with "Unavailable", as Pixel Launcher lacks `READ_CALENDAR`.
- Afterwards every `glance_source_*` and `glance_when_*` key was removed again, which is stock.
- Not tested on device: Date card, Date and Time extras, While music plays / While charging.
