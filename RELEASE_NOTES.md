# Pixel Launcher Evolved v1.0.0

First release. Replaces the standalone
[BubblesLauncher](https://github.com/MrxSiN/BubblesLauncher) module, whose bubble
button ships here as one feature among several.

- **Settings app.** Material 3 Expressive, coloured from your wallpaper. It says
  up front whether a framework has accepted the module, and refuses to store
  settings when none has.
- **Changes apply straight away.** Every tweak here reaches the launcher that is
  already running, and switching one off restores what it changed. A **Restart
  launcher** button is there for the times a hooked process needs a clean slate.
- **Bubble button on Recents cards.** A Material 3 medium FAB in the
  bottom-right of each task card. Tapping it puts Overview away — back to the app
  you came from, or to home — and reopens the card's app as a floating bubble
  through the platform's own `SystemUiProxy.showAppBubble` path.
- **Hide Screenshot, Select, or Clear all** from the Recents action row,
  individually.
- **Clear all in the task menu.** The launcher's own menu offers Clear, which
  dismisses one task; this adds the sweep, so Clear all stays reachable with the
  action row button hidden.

Built against the modern libxposed API 102; requires a framework implementing API
101 or newer, such as Vector v2.2.

Verified on a Pixel 8 Pro running Android 17 (`CP2A.260805.005`) with Vector v2.2.
