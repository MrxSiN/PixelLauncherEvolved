# Pixel Launcher Evolved v0.1.6

Tested on a Pixel 8 Pro running Android 17 QPR1 (October 2026), build `CP3A.261005.005`,
Pixel Launcher `907`, with Vector 2.2. Every launcher member the module stands on resolves (282 of
282). Your settings carry over untouched.

### Widget stacks

- **Home screen → Widgets → Widget stacks**: several widgets in one place. Drop a widget onto
  another and hold it a moment, or use **Create stack** in a widget's long-press menu.
- Swipe up or down to switch, or sideways if you choose, with dots beside the stack. A joining
  widget takes the stack's size where its app allows; a stack holds up to 10.
- **Edit stack** reorders, shows, configures, moves out or removes widgets. **Smart rotate widgets**
  shows the widget you set for the time of day as you come back home.
- **Restore standalone widgets** puts every stacked widget back on its own. Without the module
  nothing is lost: each waiting widget appears on a home screen of its own.

### Per-Mode choices

- Each Mode in **Assign home screens to Modes** now opens as its own page with what changes only
  while it is on: regular home screens beside its own, hidden apps, apps at the top of the drawer,
  its own icon pack, wallpaper blur, home screen tint and app labels, and which search results show.
- A Mode deleted in Settings keeps its home screens to give to another Mode.

### Also new

- **App labels**: hide app names on the Home screen, in folders or in the app drawer at once.
- **Rename** shows a live preview, and the icon can be changed from your icon pack.
- **Refresh icons** in Advanced redraws the icon pack's icons without a restart.
- **Layout recovery**, in the module's own app: puts a saved home screen layout back when Pixel
  Launcher cannot open its settings. Needs root.
- **Diagnostics** shows which features failed to start, and why.

### Fixed

- Modes are read for your own Android user only, deleted Modes no longer come back, and a slow
  read no longer holds up the first home screen.
- Home always keeps a home screen when every one is given to a Mode.
- A feature that fails to start no longer leaves half of itself running.
- Hidden apps no longer show in the drawer's suggested apps row.

### Install

1. Install the APK below.
2. Enable Pixel Launcher Evolved in your Xposed manager.
3. Grant it root, if you want the tweaks that need it.
4. Force-stop Pixel Launcher once, then long press home → Home settings → Pixel Launcher Evolved.

### Notes

- Needs Vector v2.2+ or another framework with libxposed API 101+.
- Before turning widget stacks off or removing the module, use **Restore standalone widgets** if
  you want the widgets back where the stack is.
- The double-tap action choice seen in test builds is gone: a home double tap turns the screen off.
- Not yet checked: TalkBack end to end, widget stack resizing and Smart rotate, work profile,
  landscape, RTL, other screen sizes.
