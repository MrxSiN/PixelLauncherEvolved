# Pixel Launcher Evolved v0.0.3

Third alpha. The app drawer's search gets a page of its own, and Home settings
reads as a menu rather than as four words.

## New

- **App Drawer**, a new page in Home settings, with three switches that each
  take one whole group out of the search results: **Hide Web Search**, **Hide
  Play Store** and **Hide Search in Apps**. A group's heading goes with its
  rows, so nothing is left as a title over nothing. Your apps, their shortcuts,
  Settings results and tips are untouched, and a switch applies to the next
  keystroke.
- **Open Web Search with**, on the same page. Tapping a Web Search result opens
  the results in the app you pick instead of the Google app. The list is every
  app on the device that opens a website link, and it is read again each time
  you open it, so an app installed or removed since is already right.

  What is searched for is the result you tapped, not what you typed: type
  *weather*, tap *weather tomorrow*, get *weather tomorrow*. The results are
  opened as a link, so the app shows them on the first tap rather than offering
  to search again. Leave it alone, or point it at an app you later uninstall,
  and the launcher's own answer stands.

The results reach the app drawer from two places at once — the platform's
search service for what is on the device, and the Google app for web
suggestions — and both are filtered, because the filtering happens where the
launcher merges them rather than at either source.

## Changed

- **Home settings names its pages properly.** **Home Screen**, **App Drawer**,
  **Overview** and **Tablet Layout (Experimental)**, each with a line saying
  what it holds.
- **The lines under the tweaks are shorter.** They sit among the launcher's own
  rows, which say what a setting does and what it needs in one line, so these
  do too.
- **The app icon is the mark alone**, white on the flat brand circle, rather
  than a small badge under a long diagonal shadow. The badge read as a circle
  inside the mask's circle at the one size the icon is actually seen. The
  themed icon is now the same drawing rather than an approximation of it.

## Removed

- **The module's own screen**, and with it the activity, the application class,
  the framework-binder listener only that screen used, and every Compose
  dependency. Every setting already lived in the launcher's own Home settings,
  so the screen opened on nothing to change and existed to answer one question:
  whether a framework had picked the module up. An Xposed manager answers that
  itself. The APK is a fraction of its former size, and one runtime dependency
  is left.

  The module no longer appears in your app list. Its settings are where they
  have always been: long press an empty part of the home screen, **Home
  settings**, then scroll to the bottom.

## Upgrading from v0.0.2

Install over the top and force-stop the Pixel Launcher once. Your existing
settings are kept, and the new switches start off, so the app drawer's search
looks exactly as it did until you change something.

---

Built against the modern libxposed API 102; requires a framework implementing API
101 or newer, such as Vector v2.2.

Verified on a Pixel 8 Pro running Android 17 (`CP2A.260805.005`) with Vector v2.2.
