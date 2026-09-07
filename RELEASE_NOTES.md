# Pixel Launcher Evolved v0.0.1

First alpha.

- **Settings where you already are.** Long press an empty part of the home
  screen, tap **Home settings**, and scroll to the bottom: everything this module
  adds sits under **Pixel Launcher Evolved**, styled as the launcher's own rows.
  The module's app is left to say whether a framework accepted the module, and to
  offer a shortcut into that screen.
- **Changes apply straight away.** Every Overview tweak reaches the launcher that
  is already running, and switching one off restores what it changed. A **Restart
  Launcher** row is there for the layout tweaks, which the launcher only reads at
  startup.
- **Bubble button on Recents cards.** A Material 3 medium FAB in the
  bottom-right of each task card. Tapping it puts Overview away — back to the app
  you came from, or to home — and reopens the card's app as a floating bubble
  through the platform's own `SystemUiProxy.showAppBubble` path.
- **Clear all where you want it.** Add it to the Recents action row. Hide
  Screenshot and Select individually.
- **Full tablet layout** for the launcher's large-screen layout, or **Taskbar
  Only** for the taskbar without the tablet grid, with the hotseat handoff, the
  icon count and the reveal animation corrected so the transition reads as the
  launcher's own.
- **Cleaner Overview taskbar.** Hide the app drawer button and its divider only
  while Recents is open. The icons that remain are re-centred.
- **Double tap to sleep.** Double tap empty workspace to send the power-key
  event through root. Root is granted to the module app, not Pixel Launcher.
- **Blur Wallpaper.** Switched from **Wallpaper & Style → Home screen**, under
  **Layout**. The home screen wallpaper is blurred and pushed back using the
  launcher's own blur, at half the strength it already uses behind the app
  drawer, so opening the drawer deepens it rather than stacking a second blur.

Built against the modern libxposed API 102; requires a framework implementing API
101 or newer, such as Vector v2.2.

Verified on a Pixel 8 Pro running Android 17 (`CP2A.260805.005`) with Vector v2.2.
