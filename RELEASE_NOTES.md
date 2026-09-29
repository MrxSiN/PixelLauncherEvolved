# Pixel Launcher Evolved v0.1.3

Tested on a Pixel 8 Pro running Android 17 QPR1 (September 2026), build `CP3A.260905.009`,
Pixel Launcher `907`. Your settings carry over untouched.

### Icon packs, in Wallpaper & style

- **Wallpaper & style → Home screen → Icons pack**, right under the system's own Icons option.
  The page grows out of the home screen card: the real home screen on top, drawn by Pixel
  Launcher, and every installed icon pack in the picker's own option sheet under it.
- **Preview before you apply.** Tapping a pack shows your home screen with its icons; nothing
  changes until you press Apply.
- **Real Pixel icons.** Pack artwork goes through the launcher's own icon pipeline, so shape,
  shadow, work badges, dots, folders, search, predictions and the taskbar all agree. Apps the pack
  doesn't cover keep their own icon, themed or not. Calendar icons follow the date. Pick a
  different icon from the pack, or the stock one, for any single app.
- **No restart.** Changing the pack redraws only the icons that changed. Coming back home after
  a change, the new icons are brought in one after another, and a notice tells you when the
  change has landed.
- ADW, Nova, Apex, Go, Atom, Lawnchair and Teslacoil packs are supported. Backups include the
  pack and your per-app icons.

**Existing installs:** enable **Wallpaper & style** (`com.google.android.apps.wallpaper`) for
this module in your Xposed manager, then restart it.

### Fixed

- **Blur wallpaper**: Wallpaper & style's home screen preview is now blurred as your home screen
  is, and opening or closing the app drawer no longer snaps the whole home screen to a blur and back.
- **Focus home screens**: when a Mode changes while you're in an app, home no longer stays blank
  for most of a second when you go back to it.
