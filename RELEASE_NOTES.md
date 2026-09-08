# Pixel Launcher Evolved v0.0.2

Second alpha. One new feature, a round of taskbar repairs, and one feature
withdrawn.

## New

- **Focus home screens.** Give a home screen page to one of the device's Modes —
  Bedtime, Driving, Sleeping, whatever you have. While that Mode is on the
  launcher shows only its pages; when it ends the ordinary pages come back and
  the Mode's own are put away. Nothing is written to the launcher's database, so
  a hidden page is hidden the way a page scrolled off the side is hidden.
- **Focus pages**, the row under the switch, opens a two-step chooser: pick a
  Mode, then pick its pages from live previews of the home screens themselves.
  The Mode that is on is marked. When it cannot offer a choice it says which of
  the three reasons applies — pages the launcher has not built yet, Modes it
  could not read without root, or a device with no Modes at all.

Reading Modes needs root. Since Android 15 `getAutomaticZenRules` reports only
the rules the calling app owns, so a person's real Modes — the system's,
Wellbeing's, GMS's — come back as nothing at all. The module's own app reads
them through root and answers the launcher through a content provider that
serves no other caller. Root stays with the module app; the launcher never
receives it.

## Fixed

- **The hotseat is no longer empty after the launcher restarts.** The taskbar is
  told whether home is visible, and on a restart that report arrives out of
  order — describing the launcher it has just replaced. Nothing takes it back,
  so the taskbar spent the session believing the launcher was behind something
  and stopped drawing the hotseat icons it had been handed.
- **The taskbar no longer sits across the app you switched to.** Ending a quick
  switch between two apps left the same answer stale the other way round, and
  going into the Google feed and back left it stale a third way.
- **Switching between two apps along the bottom edge** no longer leaves the
  taskbar drawn across the app it switched to. Standing down early now also asks
  whether the launcher is in front, which a quick switch answers for itself.
- **Focus pages previews show each page's own contents**, rather than a
  screenshot of the home screen drawn underneath every one of them. The capture
  no longer races the compositor, is not taken through the notification shade or
  part way through a page change, and a frame sampled while anything was in
  front is dropped instead of kept.
- **The page gallery no longer squashes previews**, and newly-created home pages
  appear in the chooser immediately.

## Removed

- **Blur Wallpaper**, and with it the `com.google.android.apps.wallpaper` scope.
  The depth floor it raised shared one number with the app drawer and Recents,
  so every launcher state and every launcher update was a way for the two to
  disagree. Replacing the wallpaper with a blurred copy fixed that but could not
  cover a live wallpaper, which stores no image; blurring the bitmaps a live
  wallpaper renders from covered that in turn, but only for one app, by name.
  The module is scoped to the launcher alone again, and every setting is in the
  launcher's own Home settings. A value left in the old `Settings.Secure` key by
  v0.0.1 is ignored and never read.

## Upgrading from v0.0.1

Install over the top and force-stop the Pixel Launcher once. Your existing
settings are kept. Grant the module app root if you want Focus home screens;
Double tap to sleep already needed it.

---

Built against the modern libxposed API 102; requires a framework implementing API
101 or newer, such as Vector v2.2.

Verified on a Pixel 8 Pro running Android 17 (`CP2A.260805.005`) with Vector v2.2.
