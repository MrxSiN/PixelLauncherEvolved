# Pixel Launcher Evolved v0.1.1

Tested on a Pixel 8 Pro running Android 17 QPR1 (September 2026), build `CP3A.260905.009`,
Pixel Launcher `907`, with Vector v2.2. Every launcher member the module stands on still
resolves — 57 of 57, no fallbacks.

### What's in it

- **Double tap status bar to sleep**: two taps on the status bar turn the screen off, under
  Home screen → Gestures. Needs no root — the gesture is watched inside SystemUI, which can end
  the screen itself. Taps on the clock or a chip still do what those do.
- **Both sleep gestures close the screen around your finger**: the light shrinks into the spot
  you tapped and goes out there, the reverse of the circle SystemUI opens the screen with when
  you tap the always-on display.

### Fixed

- **Double tap to sleep no longer opens the camera**: it sent the power key, and two power keys
  in quick succession are Android's own press-power-twice-for-the-camera gesture. It sends the
  sleep key now. Pressing the power button twice still opens the camera.
- **A repeated tap no longer stacks up screen offs**: a second one asked for while the first is
  still reaching the root shell is dropped, so a late key cannot catch a screen you have woken
  again.
- **The screen off no longer sweeps in from the power button's edge.**

### Changed

- **Advanced**: Backup & restore sits at the top, and Restart Pixel Launcher and Compatibility &
  diagnostics have a Launcher section of their own.

### Install

1. Install `PixelLauncherEvolved-v0.1.1.apk`
2. Enable Pixel Launcher Evolved in Vector (static scope: Pixel Launcher and SystemUI)
3. Force-stop Pixel Launcher once, or reboot
4. Long press the home screen → Home settings → Pixel Launcher Evolved

### Notes

- Needs a framework with libxposed API 101+, such as Vector v2.2.
- **SystemUI is new in the module's scope.** It is entered only to draw the screen off and to
  watch the status bar. If your framework does not apply the new scope on update, grant it in
  the manager and let SystemUI restart; without it the status bar gesture does nothing and the
  screen off keeps its old animation.
- Updating from v0.0.9 or earlier: settings are kept. After installing, the launcher restarts
  itself the next time the screen goes off.
- A layout mode change applies after **Restart Pixel Launcher**.
