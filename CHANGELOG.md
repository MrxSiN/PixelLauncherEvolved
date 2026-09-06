# Changelog

## 1.0.0

Initial release, superseding the standalone BubblesLauncher module.

### Added

- Material 3 Expressive settings app with wallpaper-derived colours, reporting
  whether an Xposed framework has accepted the module.
- Preference bridge between the settings app and the hooked launcher process
  built on the libxposed service, so both sides share one preference group.
- Live settings: a feature that can react to a running launcher is installed
  whatever its setting says and reads that setting from inside its hooks, so a
  toggle takes effect on the next frame. Switching a tweak off restores the state
  the launcher itself wanted rather than leaving the change applied.
- Restart launcher button, for the cases live application cannot cover. The
  request travels on the preference channel and the module ends the launcher
  process from the inside, which needs no permission and exposes no receiver.
- Feature registry: each tweak is a `LauncherFeature`, and a failure in one is
  contained.
- Bubble button on every Overview task card, anchored to the bottom-right of the
  task thumbnail and sized as a Material 3 medium floating action button.
- Overview is put away before a bubble is raised, back onto whatever it covered:
  the app it was opened from is relaunched with `TaskView.launchWithAnimation()`,
  otherwise `RecentsView.startHome(Runnable)` runs.
- Individual hiding of the Screenshot, Select, and Clear all buttons in the
  Recents action row. Buttons are matched by resource id, and by the launcher's
  own label where the build adds one without an id.
- Clear all entry in the task menu, built from the launcher's own option layout,
  icon, and label.
- R8 shrinking for release builds, with the module entry class kept by name.
