# Pixel Launcher Evolved v0.1.5

Tested on a Pixel 8 Pro running Android 17 QPR1 (October 2026), build `CP3A.261005.005`,
Pixel Launcher `907`. Every launcher member the module stands on resolves (227 of 227). Your
settings carry over untouched.

### App labels

- **Home settings → App drawer → App labels**: rename any app, or hide its label on the Home
  screen or in the app drawer, app by app. Search finds the app by either name, the drawer sorts
  it under the new one, and TalkBack still says which app it is.
- **Rename on Home screen only** keeps new names to the Home screen, dock and folders.
- **Edit Name** in an app's long-press menu opens the same editor.
- **Reset custom labels** gives every app its own name back. Labels are in the settings backup.

### Modes pages

- Pages are drawn by Pixel Launcher itself, so every icon, label, folder and widget looks exactly
  as it does on your home screen, and they are drawn again each time you open the screen, so widgets
  are up to date.
- The real wallpaper sits behind each page, and each page shows its own part of it, as your home
  screen does with that Mode on. Needs Wallpaper & style in this module's scope.

### Fixed

- The home screen no longer blinks off for a moment a few seconds after you return to it.
- Modes pages and Organize home screens open without a pause.
- Pixel Launcher no longer uses more memory each time it restarts itself after a theme or dark mode
  change.
- Wallpaper & style → Layout → Custom no longer gets slower each time it is opened.
