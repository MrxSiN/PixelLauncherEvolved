# Pixel Launcher Evolved

An Xposed module that adds the controls the Android 17 Pixel Launcher does not
ship, with a Material 3 Expressive settings app to turn them on and off.

Every tweak is off unless you switch it on, and each one is installed only when
its preference says so — the launcher is left exactly as Google shipped it for
anything you have not asked to change.

## Features

### Overview

| Tweak | What it does |
|---|---|
| Bubble button on task cards | Adds a Material 3 medium FAB to the bottom-right of each Recents card. Tapping it puts Overview away — back to the app you came from, or to home — and reopens the card's app as a floating bubble. |
| Hide Screenshot | Removes the Screenshot button from the Recents action row. |
| Hide Select | Removes the Select button from the Recents action row. |
| Hide Clear all | Removes the Clear all button from the Recents action row. |

Home-and-drawer layout and gesture tweaks are the next two categories; their
sections appear in the app as soon as they carry features.

## Requirements

| | |
|---|---|
| Android | 17 (API 37) |
| Launcher | Pixel Launcher (`com.google.android.apps.nexuslauncher`) |
| Framework | [Vector](https://github.com/JingMatrix/Vector) v2.2 or newer, or any framework implementing libxposed API 101+ |

Built against the modern [libxposed API](https://github.com/libxposed/api)
(`io.github.libxposed:api`), not the legacy `de.robv.android.xposed` bridge.

## Install

1. Install the APK from [Releases](https://github.com/MrxSiN/PixelLauncherEvolved/releases).
2. Enable **Pixel Launcher Evolved** in your Xposed manager. The module declares
   a static scope, so there is no scope to pick.
3. Open the app, switch on what you want.
4. Force-stop the Pixel Launcher, or reboot.

The app tells you at the top whether a framework has accepted the module.
Settings cannot be changed while it has not: there would be nowhere to store
them.

## How it works

The settings app and the hooked launcher process never talk to each other
directly. They share one preference group, which the framework mirrors into the
launcher:

```
settings app  --write-->  XposedService.getRemotePreferences("settings")
                                     |
                            Xposed framework
                                     |
launcher      --read--->  XposedInterface.getRemotePreferences("settings")
```

Both sides describe a tweak once, in `catalog/`: the settings screen renders from
that list and the hooks read the same `Setting` objects, so a key cannot drift
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
settings/                    reading (hook side) and writing (app side)
hook/                        the feature contract, context and registry
feature/                     one package per area; each feature installs itself
ui/                          Material 3 Expressive settings screen
```

Adding a tweak means adding a `LauncherFeature`, a `Setting`, and one line in
`FeatureRegistry` and `FeatureCatalog`. Nothing existing changes, and no feature
knows about any other.

Features depend on contracts (`BubbleLauncher`, `TaskTargetResolver`,
`OverviewCloser`, `SettingsSource`) rather than on reflection details, so a
launcher change is absorbed by one implementation class.

## Licence

Apache License 2.0. See `LICENSE` and `NOTICE.md`.

The bubble feature began life as
[BubblesLauncher](https://github.com/MrxSiN/BubblesLauncher), which this project
replaces.
