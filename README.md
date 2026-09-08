<div align="center">

<img src="docs/icon.svg" width="120" alt="Pixel Launcher Evolved">

# Pixel Launcher Evolved

**The controls the Android 17 Pixel Launcher does not ship — put where you already go to change how home behaves.**

Long press an empty part of the home screen → **Home settings** → scroll to the bottom.

<br>

[![Release](https://img.shields.io/github/v/release/MrxSiN/PixelLauncherEvolved?include_prereleases&color=5B3DF5&label=release&style=for-the-badge)](https://github.com/MrxSiN/PixelLauncherEvolved/releases)
[![Downloads](https://img.shields.io/github/downloads/MrxSiN/PixelLauncherEvolved/total?color=3DDC84&logo=android&logoColor=fff&style=for-the-badge)](https://github.com/MrxSiN/PixelLauncherEvolved/releases)
[![Android](https://img.shields.io/badge/Android-17-3DDC84?logo=android&logoColor=fff&style=for-the-badge)](https://developer.android.com)
[![Licence](https://img.shields.io/github/license/MrxSiN/PixelLauncherEvolved?color=5B3DF5&style=for-the-badge)](LICENSE)

</div>

---

## Why it works this way

Most launcher modules hand you a second settings app. This one does not have a
screen at all. Every tweak is a row in the launcher's **own** Home settings,
drawn with the launcher's own preference classes and stored in the launcher's
own data directory — so the code that draws a switch and the code that reads it
are the same process, and a change reaches the running launcher on the next
frame.

|  | |
|---|---|
| ⚡ **Live** | Overview and home screen tweaks apply immediately. Switching one off restores exactly what it changed. |
| 🔁 **Restart only when it must** | Layout tweaks need **Restart Launcher**, because device profiles are built once at startup. That button is in the same section. |
| 🔒 **Root stays put** | Root belongs to this module's app. The launcher never receives it. |
| 🪶 **Small** | No Compose, no settings app, one runtime dependency. |
| 🧩 **Contained** | Each tweak is a `LauncherFeature`. One failing feature cannot take the others down. |

---

## Features

<details open>
<summary><b>🏠 Home Screen</b></summary>
<br>

| Tweak | What it does | Root |
|---|---|:---:|
| **Focus home screens** | Gives a home screen page to one of the device's Modes — Bedtime, Driving, Sleeping, whatever you have. While that Mode is on, only its pages show; when it ends the ordinary pages come back and its own go away. Assign them under **Focus pages**. | ✅ |
| **Double Tap to Sleep** | Two taps on empty workspace send the power-key event through `su`. | ✅ |
| **Search bar opens app search** | Tapping the home screen search bar opens the app drawer with its search box focused, the way earlier Pixel Launcher versions did, instead of handing the tap to the Google app. The bar's own buttons — logo, microphone, Lens — keep their actions, and a long press still picks the widget up. | — |

</details>

<details>
<summary><b>🔍 App Drawer</b></summary>
<br>

Apps you hide are left out through the launcher's own per-tab filter, so a work
app is still a work app. Nothing is written until you press the button; leaving
the drawer any other way keeps what was already hidden.

The app drawer's search results are not the launcher's own work: it asks the
platform's search service and the Google app for them and draws what comes
back, grouped the way they grouped it. Each switch takes one whole group out on
the way in — heading and rows together — so the launcher lays out a shorter
list rather than one with a gap in it.

| Tweak | What it does |
|---|---|
| **Hidden apps** | Leaves the apps you pick out of the drawer, and out of its search. Tapping it opens the drawer itself with a tick on every icon: tap the apps to hide, then the button in the corner. Apps already hidden come back with their ticks on, so you can take them off. An icon already on the home screen or in the hotseat stays where you put it. |
| **Hide Web Search** | Removes Google's suggestions for what you typed. |
| **Hide Play Store** | Removes the apps-to-install results. |
| **Hide Search in Apps** | Removes the row that hands your search to Google, YouTube, Maps and the rest. |
| **Open Web Search with** | Tapping a Web Search result opens the results in the app you pick here instead of the Google app. The list is every app on the device that opens a website link. What is searched for is the result you tapped, not what you typed — type *weather*, tap *weather tomorrow*, get *weather tomorrow*. |

Your apps, their shortcuts, Settings results and tips are untouched, and every
switch applies to the next keystroke.

</details>

<details>
<summary><b>🗂 Overview</b></summary>
<br>

| Tweak | What it does |
|---|---|
| **Show Bubble launcher button** | A Material 3 medium FAB at the bottom-right of each Recents card. Tapping it puts Overview away — back to the app you came from, or home — and reopens the card's app as a floating bubble. |
| **Show Clear all button** | Adds **Clear all** beside Screenshot and Select. The stock row has none. |
| **Hide Screenshot button** | Removes Screenshot from the action row. |
| **Hide Select button** | Removes Select from the action row. |

</details>

<details>
<summary><b>📐 Tablet Layout</b></summary>
<br>

| Tweak | What it does |
|---|---|
| **Full tablet layout** | The launcher's large-screen classification, which decides the grid and the taskbar together. Changes the grid, so the workspace is migrated — one it cannot carry across comes back empty. |
| **Taskbar Only** | The taskbar without the tablet grid. Home, app drawer and Recents keep their phone measurements and the workspace is never migrated. |
| **Hide app drawer button** | Takes the taskbar app drawer button and its divider out while Overview is open, and re-centres what is left. |

The first two are alternatives; switching one on switches the other off. Either
way the taskbar is kept out of the app's own transition into Overview, because
the window manager lends that window to the app for the length of a recents
animation.

</details>

<details>
<summary><b>⚙️ Everywhere</b></summary>
<br>

**Restart Launcher**, at the end of the section, for when a hooked process needs
a clean slate. The launcher ends its own process and Android brings the home app
straight back.

</details>

---

## Requirements

| | |
|---|---|
| **Android** | 17 (API 37) |
| **Launcher** | Pixel Launcher (`com.google.android.apps.nexuslauncher`) |
| **Framework** | [Vector](https://github.com/JingMatrix/Vector) v2.2+, or any framework implementing libxposed API 101+ |
| **Root** | Required for Double tap to sleep and Focus home screens |

Built against the modern [libxposed API](https://github.com/libxposed/api)
(`io.github.libxposed:api`), not the legacy `de.robv.android.xposed` bridge.

## Install

```
1. Install the APK from Releases
2. Enable Pixel Launcher Evolved in your Xposed manager
3. Grant it root, if you want the two tweaks that need it
4. Force-stop the Pixel Launcher once
5. Long press home → Home settings → scroll to the bottom
```

The module declares a **static scope**, so there is nothing to pick. It has no
icon in the app drawer and no screen of its own — every setting is in Home
settings, so a screen here would open on nothing to change. Settings stored by
earlier versions are carried across the first time the launcher loads this one.

---

## How it works

Settings live in one preference file in the launcher's own data directory, so
what draws the switches and what reads them are the same process:

```
Home settings section  ──write──▶  pixel_launcher_evolved.xml  ◀──read──  features
                                    (launcher data directory)
```

That is what makes a change apply without a restart. A feature that can react to
a running launcher declares itself **live**: it is installed whatever its setting
says and reads that setting from inside its hooks. Overview lays out again
constantly, so the next frame reflects the new value, and switching a tweak off
restores what it changed rather than leaving it applied until the next start.

Opening that file needs a `Context`, and the module is loaded before the process
has one. Installation intercepts `LauncherApplication.onCreate` after Android
attaches the base context, then installs hooks before launcher startup code can
initialize device profiles.

The section itself is built from the launcher's own `androidx.preference`
classes, reached by reflection because a `SwitchPreference` compiled here would
be a different type from the one the screen accepts. Titles and summaries are
read from this module's APK with `getResourcesForApplication`, so they are still
written once, in `strings.xml`.

<details>
<summary><b>Focus home screens, in detail</b></summary>
<br>

It filters the model the launcher builds its workspace from. The database is
never written, so a hidden page is hidden the way a page scrolled off the side
is hidden — turning the Mode off brings it back exactly as it was.

What is filtered is the **items**, not a list of screens, because the launcher
keeps no such list: `collectWorkspaceScreens` walks the items, takes the ones
sitting on the workspace and collects the screens they name. A page with no
items left is a page never asked for. The launcher is handed a second model
around a second map rather than its own — the model's `copy()` hands back the
very same map, and it is shared with the loader and with the code that writes.

Three things follow from that:

- The launcher prunes screens it finds empty and saves the result. While a Mode
  is on, the screens it was not shown are absent rather than empty, so that
  pruning is held off. If the pruning cannot be found at all, the feature
  refuses to install rather than risk deleting a page it hid.
- **Reading Modes needs root.** Android only reports the Modes an app created
  itself: `getAutomaticZenRules` returns nothing for the Modes a person actually
  has, which belong to the system, to Wellbeing, to GMS and to Settings
  Intelligence. So this module's own app reads them out of the notification
  service's own dump and answers the launcher through a content provider that
  serves no other caller.
- The launcher creates its first screen before it is told what to show. The
  feature skips that screen when the active Mode does not own it, avoiding an
  empty page at the front.

Modes are looked at when the launcher comes back to the front, and whenever Do
Not Disturb changes. Nothing polls on a timer. A Mode that changes while the
home screen is already showing applies on the next return to it, unless it moved
Do Not Disturb, which most do — Driving and Transit are the ones that do not.

</details>

`HOOK_NOTES.md` records the launcher internals each feature relies on, and how
they were verified on a device.

---

## Build

```bash
./gradlew clean assembleRelease
```

A local release build is unsigned unless `ANDROID_KEYSTORE_PATH`,
`ANDROID_KEYSTORE_ALIAS`, `ANDROID_KEYSTORE_PASSWORD` and `ANDROID_KEY_PASSWORD`
are set. `scripts/check-project.sh` runs the static invariants that CI enforces.

Release builds are shrunk with R8; the module entry class is kept by name,
because the framework resolves it from `META-INF/xposed/java_init.list` rather
than from any call site.

## Design

```
PixelLauncherEvolvedModule   Xposed entry: recognises the launcher, hands off
catalog/                     every tweak described once, shared by both sides
settings/                    where a setting is stored, and how it is read
hook/                        the feature contract, context, startup and registry
feature/                     one package per area; each feature installs itself
focus/                       Modes, pages, and the provider that answers for them
lock/                        the root-backed endpoint that turns the screen off
```

Adding a tweak means adding a `LauncherFeature`, a `Setting`, and one line each
in `FeatureRegistry` and `FeatureCatalog`. Nothing existing changes, and no
feature knows about any other — the settings section included, since it renders
whatever the catalog holds.

Features depend on contracts (`BubbleLauncher`, `TaskTargetResolver`,
`OverviewCloser`, `SettingsSource`) rather than on reflection details, so a
launcher change is absorbed by one implementation class.

---

<div align="center">

**GNU General Public License v3.0** · see [`LICENSE`](LICENSE) and [`NOTICE.md`](NOTICE.md)

</div>
