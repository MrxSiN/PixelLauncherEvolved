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
