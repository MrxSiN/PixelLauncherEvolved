# Pixel Launcher Evolved v0.0.5

Fifth alpha. A Mode that switches itself on now reaches the home screen without
being asked twice, the page swap is sprung rather than wiped, and the wallpaper
keeps its blur on the way home.

## Fixed

- **A Mode that comes on by itself is noticed straight away.** Driving and
  Transit run without touching Do Not Disturb, so the interruption filter never
  moves for them and nothing reported the change — the pages waited until the
  launcher was next brought to the front. Switching a Mode by hand only looked
  immediate because closing the shade hands the launcher back its window focus.
  Measured on a Pixel 8 Pro running Android 17: Driving on and off, the filter
  resting at 0 throughout, and the swap landing about a second and a half after
  the Mode changed.
- **Reading the Modes no longer runs while the workspace is being built.** It is
  a call into this module's own app, answered out of a root shell, and it ran
  again on every bind — including the bind that reading had itself just asked
  for. It happens once now, on the thread that asked to look.

## Changed

- **The Focus page swap is Material 3 Expressive.** It used to cut the workspace
  to invisible, hold it blank for as long as the model took to bind, then
  uncover it from the middle over three quarters of a second on a plain ease.
  Content being replaced now fades and shrinks on an emphasized accelerating
  curve, and content arriving is sprung into place — a real spring, slightly
  underdamped, so it overshoots a little before it settles.
- **The wallpaper keeps its blur through the animation home.** The launcher
  switches its own window blurs off for the length of that animation, which on a
  blurred home screen reads as the wallpaper snapping sharp until it lands. That
  pause is no longer passed on while the tweak is on.

## Known issue

The blur change above has a cost, and it is why the approach was dropped once
before rather than because it was never tried. A back gesture follows an app's
window off the screen, which puts the launcher's own content under the
transition leash, and the blur applies to everything behind the surface it is
set on — so the icons, their labels and the search bar can blur along with the
wallpaper. Only the status bar, a window of its own, stays sharp.

Swiping up to home is not affected. If the back gesture is worse for you than
the flicker it replaces, switch **Blur wallpaper** off on the Home Screen page;
everything else in this build is independent of it.

## Upgrading from v0.0.4

Install over the top and force-stop the Pixel Launcher once. Your existing
settings and page assignments are kept.

---

Built against the modern libxposed API 102; requires a framework implementing API
101 or newer, such as Vector v2.2.

Verified on a Pixel 8 Pro running Android 17 (`CP2A.260805.005`) with Vector v2.2.
