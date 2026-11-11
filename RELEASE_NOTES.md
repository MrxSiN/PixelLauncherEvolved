# Pixel Launcher Evolved v0.1.7

Tested on a Pixel 8 Pro running the November 2026 Android 17 update, build `CP4A.261105.040.A1`,
Pixel Launcher `907`, with Vector 2.2. Every launcher member the module stands on resolves (309 of
309, none by fallback). Your settings carry over untouched.

### Fixed

- **Grid & size and Widget stacks are back after the November update.** The update kept Pixel
  Launcher's version number but moved where a widget's smallest size is kept, so both were switched
  off as unavailable.
- **Widget stacks and the home screen search bar's long press** find the launcher's renamed fields
  again.
- **Compatibility & diagnostics names the latest launcher first.** The November build's members are
  the ones checked; the October build's are the fallbacks, marked with ⚠ only on that build.
- **The compatibility report is written again after a system update**, even when Pixel Launcher's
  version number stays the same.
- **Pixel 11 Pro and other phones with the earlier launcher layout** ([issue 1](https://github.com/MrxSiN/PixelLauncherEvolved/issues/1)):
  the dock, All apps columns, Overview actions, Overview only, Taskbar only, the search bar and
  spacing fall back to that layout's names. Grid columns and rows, icon size, full tablet layout and
  widget stacks stay unavailable there. Not tested on a Pixel 11 Pro.
- A layout mode or a Grid & size part that fails to start no longer stays half on.
