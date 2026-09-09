# Pixel Launcher Evolved v0.0.4

Fourth alpha. Focus home screens learns the Modes it was missing, the app
drawer learns to leave apps out, and going home stops stuttering.

## New

- **Hidden apps**, on the App Drawer page: the apps you pick are left out of
  the drawer, and out of its search, so one that is hidden does not come back
  the moment its name is typed.

  They are picked in the drawer itself rather than from a list of names: the row
  opens the app drawer with a tick on every icon, tapping one takes it instead
  of opening it, and a button in the corner ends it. Apps already hidden are put
  back for the length of it, ticks on, so one can be recovered. Nothing is
  written until the button is pressed; leaving the drawer any other way keeps
  what was already hidden.

  The launcher's own per-tab filter does the hiding, wrapped rather than
  replaced, so a work app is still a work app. An icon already on the home
  screen or in the hotseat stays where it was put.
- **A Focus page arrives with a reveal.** Turning a Mode on or off clips its
  page in with a circular, wallpaper-style transition once the launcher is
  visible again, rather than swapping the pages under you.

## Fixed

- **Going home with the back gesture no longer stutters.** Reading which Modes
  are on is a call into this module's own app, which answers by shelling out as
  root: about 130ms with that app already running, about 490ms when the call
  has to start it. All of it ran on the launcher's UI thread, from the hook that
  notices the launcher coming forward. The launcher regains window focus part
  way through the back animation home, so the stall landed inside a running
  animation. Swiping home never stuttered for the same reason it never looked
  wrong — focus arrives there once the animation has finished, so the identical
  stall fell out of sight. The reading now happens on a thread of its own.
- **A Mode turned on by hand is recognised.** Android reports that as an
  activation override while leaving the rule's own condition false, so checking
  the condition alone treated the Mode as off. Closing the Modes sheet over the
  launcher now applies the chosen page immediately as well, including for Modes
  such as Driving that never touch Do Not Disturb.
- **Focus pages shows the page you are choosing.** A page given to a Mode is
  filtered out of the workspace, so it has no view left to photograph, and the
  whole set of page pictures was replaced every time one was taken — which
  dropped exactly the pages the dialog exists to show. A page's last picture is
  now kept, and written down, so it survives a launcher restart. It works in
  both directions, because while a Mode is on it is the ordinary pages that are
  filtered out.
- **A page with no picture is drawn as a home screen.** Its widgets use the same
  preview pictures the launcher's own widget picker shows, on a dark surface
  rather than a near-white panel; its icons are themed when the home screen's
  are; and it is laid out on the launcher's real grid rather than a guessed four
  by six, so nothing is the wrong size.
- **Page previews keep their wallpaper**, including on the first launcher start
  after a restart, and a page the launcher has not filled in yet is no longer
  photographed over a good picture of it.
- **A notification is no longer captured as your wallpaper.** The wallpaper is
  read off the display, and while the notification shade takes the window focus
  and was already waited out, a heads-up notification does not — so one arriving
  as the launcher came forward was captured and then used behind every page
  preview until the launcher restarted. The display is now read only once the
  launcher has been in front for longer than such a notification lasts.
- **Focus pages no longer offers pages that belong to another Mode.** A Mode's
  own pages stay visible while you edit it.

## Upgrading from v0.0.3

Install over the top and force-stop the Pixel Launcher once. Your existing
settings and page assignments are kept.

A page that was already assigned to a Mode before this build has never been on
screen for its picture to be taken, so it is drawn from the model until the
first time it is visible — turn that Mode on once, or unassign and reassign the
page, and it is photographed from then on.

---

Built against the modern libxposed API 102; requires a framework implementing API
101 or newer, such as Vector v2.2.

Verified on a Pixel 8 Pro running Android 17 (`CP2A.260805.005`) with Vector v2.2.
