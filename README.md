# Pixel Launcher Evolved

An Xposed module that adds the controls the Android 17 Pixel Launcher does not
ship, and puts them where a person already goes to change how home behaves: long
press an empty part of the home screen, tap **Home settings**, and scroll to
**Pixel Launcher Evolved** at the bottom of the launcher's own list.

Overview tweaks apply to the running launcher. The layout tweaks are off by
default and require **Restart Launcher** after enabling or disabling them,
because the launcher builds its device profiles once at startup.

## Features

### Home Screen

| Tweak | What it does |
|---|---|
| Double Tap to Sleep | Sends the power-key event through `su` after two taps on empty workspace. Root permission belongs to this module app; the launcher never receives root access. |

**Blur Wallpaper** is the exception to the rule above: its switch is in
**Wallpaper & Style → Home screen**, directly under **Layout**, because that is
where a person already goes to change how the wallpaper looks. It raises the
floor under the depth the launcher's own state handler asks for, so the
wallpaper is blurred and pushed back on the home screen with the launcher's own
blur, at half the strength it uses behind the app drawer. Opening the app drawer
or Recents still deepens it the usual amount rather than stacking a second blur.
Turning it on or off applies to the running launcher.

### Overview

| Tweak | What it does |
|---|---|
| Show Bubble launcher button | Adds a Material 3 medium FAB to the bottom-right of each Recents card. Tapping it puts Overview away — back to the app you came from, or to home — and reopens the card's app as a floating bubble. |
| Show Clear all button | Adds a Clear all button beside Screenshot and Select. The stock row has none. |
| Hide Screenshot button | Removes the Screenshot button from the Recents action row. |
| Hide Select button | Removes the Select button from the Recents action row. |

### Tablet Layout

**Full tablet layout** enables the launcher's large-screen layout and taskbar decisions
without changing system density or other apps. It also changes the grid, which
means the launcher has to migrate the workspace; a workspace it cannot carry
across comes back empty. Tablet grids can make widgets and labels cramped on a
phone. Switching it off and restarting restores stock device classification.

**Taskbar Only** takes the taskbar and leaves the grid alone. The home
screen, the app drawer and Recents keep their phone measurements, the workspace
is never migrated, and the taskbar is sized by the launcher itself rather than
by this module. The search bar moves above the hotseat icons, which is where
tablet mode puts it as well. Returning to home settles the taskbar icons onto
the hotseat rather than stepping onto it, which needs the launcher to be asked
the right one of the two device profiles it keeps, the taskbar carries one icon
per hotseat slot so every icon travels instead of the last one appearing once
the animation has finished, and the icons come out of the stashed pill whole
rather than splitting open across the middle.

The two are alternatives, and switching one on switches the other off. Either
way, the taskbar is kept out of the app's own transition into Overview: the
window manager lends that window to the app for the length of a recents
animation, which on a phone would otherwise draw the taskbar across the bottom
of the shrinking app card before it snapped back into place.

**Hide app drawer button** removes the taskbar app drawer button and its divider
only while Overview is open. What is left is re-centred, because the launcher
sizes the row by counting children rather than visible ones and would otherwise
leave the icons sitting where the removed pair used to end.

### Everywhere

A **Restart Launcher** row at the end of the section, for when a hooked process
needs a clean slate. The launcher ends its own process, and Android brings the
home app straight back.

The module's own app is not in the app drawer. Every setting is in the
launcher's own Home settings and the blur switch is in Wallpaper & Style, so an
icon there would open a screen with nothing to change. The app itself stays —
it is what holds root, and its screen still answers the one question Home
settings cannot, whether a framework accepted the module — and an Xposed
manager can still open it by name.

## Requirements

| | |
|---|---|
| Android | 17 (API 37) |
| Launcher | Pixel Launcher (`com.google.android.apps.nexuslauncher`) |
| Framework | [Vector](https://github.com/JingMatrix/Vector) v2.2 or newer, or any framework implementing libxposed API 101+ |
| Root | Required only for Double tap to sleep |

Built against the modern [libxposed API](https://github.com/libxposed/api)
(`io.github.libxposed:api`), not the legacy `de.robv.android.xposed` bridge.

## Install

1. Install the APK from [Releases](https://github.com/MrxSiN/PixelLauncherEvolved/releases).
2. Enable **Pixel Launcher Evolved** in your Xposed manager. The module declares
   a static scope, so there is no scope to pick.
3. Force-stop the Pixel Launcher once, so it loads the module.
4. Long press an empty part of the home screen, tap **Home settings**, and scroll
   to the bottom.

Open the module's app to check that a framework has accepted it. Settings stored
by earlier versions are carried across the first time the launcher loads this
one.

## How it works

Settings live in one preference file in the launcher's own data directory, so
what draws the switches and what reads them are the same process:

```
Home settings section  --write-->  pixel_launcher_evolved.xml  <--read--  features
                                   (launcher data directory)
```

That is what makes a change apply without a restart. A feature that can react to
a running launcher declares itself live: it is installed whatever its setting
says, and reads that setting from inside its hooks. Overview lays out again
constantly, so the next frame reflects the new value, and switching a tweak off
restores what it changed rather than leaving it applied until the next start.

Opening that file needs a `Context`, and the module is loaded before the process
has one. Installation intercepts `LauncherApplication.onCreate` after Android
attaches its base context, then installs hooks before launcher startup code can
initialize device profiles.

The section itself is built from the launcher's own `androidx.preference`
classes, reached by reflection because a `SwitchPreference` compiled here would
be a different type from the one the screen accepts. Titles and summaries are
read from this module's APK with `getResourcesForApplication`, so they are still
written once, in `strings.xml`.

Both sides describe a tweak once, in `catalog/`: the section renders from that
list and the hooks read the same `Setting` objects, so a key cannot drift
between them.

`HOOK_NOTES.md` records the launcher internals each feature relies on and how
they were verified on a device.

## Build

```bash
./gradlew clean assembleRelease
```

A local release build is unsigned unless `ANDROID_KEYSTORE_PATH`,
`ANDROID_KEYSTORE_ALIAS`, `ANDROID_KEYSTORE_PASSWORD`, and `ANDROID_KEY_PASSWORD`
are set. `scripts/check-project.sh` runs the static invariants that CI enforces.

Release builds are shrunk with R8; the module entry class is kept by name because
the framework resolves it from `META-INF/xposed/java_init.list` rather than from
any call site.

## Design

```
PixelLauncherEvolvedModule   Xposed entry: recognises the launcher, hands off
PixelLauncherEvolvedApp      app entry: binds to the framework service
catalog/                     every tweak described once, shared by both sides
settings/                    where a setting is stored, and how it is read
hook/                        the feature contract, context, startup and registry
feature/                     one package per area; each feature installs itself
ui/                          the module's own screen: module status, and a way in
```

Adding a tweak means adding a `LauncherFeature`, a `Setting`, and one line in
`FeatureRegistry` and `FeatureCatalog`. Nothing existing changes, and no feature
knows about any other — the settings section included, since it renders whatever
the catalog holds.

Features depend on contracts (`BubbleLauncher`, `TaskTargetResolver`,
`OverviewCloser`, `SettingsSource`) rather than on reflection details, so a
launcher change is absorbed by one implementation class.

## Licence

GNU General Public License v3.0. See `LICENSE` and `NOTICE.md`.
