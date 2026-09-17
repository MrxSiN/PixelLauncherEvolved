# Pixel Launcher Evolved v0.1.0

**Fully compatible with the latest Android release: Android 17 QPR1 (September 2026),
build `CP3A.260905.009`, Pixel Launcher `907`.** Every launcher member the module stands on
resolves — 57 of 57, no fallbacks — and every tweak is available. Tested on a Pixel 8 Pro with
Vector v2.2.

### What's new

- **Settings, reorganised** into Home screen, App drawer, Overview, Layout & taskbar and Advanced,
  each with headed groups of Material 3 Expressive cards, as Android 17 QPR1 Settings draws them.
  Switches read as what is shown; the layout modes are one radio group.
- **Organize home screens**: every page on one screen, including pages a Mode hides, arranged by
  touch and hold, then drag. It moves on Material's shared axis, with predictive back.
- **Modes home screens** and **Modes pages** (formerly Focus home screens and Focus pages).
- **Compatibility & diagnostics**, under Advanced: module, Android, build, launcher version,
  libxposed API and root, each feature's status, hook counts, every launcher contract, and a report
  to copy.
- **Per-feature compatibility gating**: a tweak whose launcher members are missing is not
  installed, and its switch says *Unavailable on Pixel Launcher …* instead of doing nothing.
- **Automatic compatibility analysis** the first time each new launcher version starts, written to
  `adb logcat -s PixelLauncherEvolved`.
- **Safe Mode**: three launcher crashes within a minute turn the experimental layout mode off
  before it loads again, and the home screen asks whether to restore it.
- **Backup & restore**, under Advanced: export settings to a JSON file, import them, or reset all
  tweaks.

### Fixed

- **Snackbar in Overview only**: Android 17 QPR1 folded `Snackbar.getDismissTimeout` into
  `Snackbar.show`, so the snackbar hook was lost. It follows `show` now.
- **Modes pages** kept a page hidden for a Mode that had been deleted in Settings; a deleted Mode's
  pages come back.
- **Tweaks load at boot**: settings live in device protected storage and load before the first
  unlock.
- **Home screen pages with Modes home screens on**: new apps no longer land on a hidden page, a
  dragged icon always has a page to land on, and an emptied page no longer stays behind.

### Install

1. Install `PixelLauncherEvolved-v0.1.0.apk`
2. Enable Pixel Launcher Evolved in Vector (static scope: Pixel Launcher)
3. Force-stop Pixel Launcher once, or reboot
4. Long press the home screen → Home settings → Pixel Launcher Evolved

### Notes

- Needs a framework with libxposed API 101+, such as Vector v2.2.
- Updating from v0.0.9 or earlier: settings are kept. After installing, the launcher restarts
  itself the next time the screen goes off.
- A layout mode change applies after **Restart Pixel Launcher**.
