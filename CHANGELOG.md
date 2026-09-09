# Changelog

## 0.0.4

### Added

- Activating or deactivating a Mode now reveals its Focus page with a circular,
  wallpaper-style transition when the launcher becomes visible.

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

### Fixed

- Focus home screens now recognises a Mode turned on manually. Android reports
  that as an activation override while leaving the rule's condition false, so
  checking only the condition treated the active Mode as off. Closing the Modes
  sheet over the launcher also applies the selected page immediately, including
  for Modes that do not change Do Not Disturb.
- **Focus pages** no longer offers pages assigned to another Mode. A Mode's own
  pages remain visible when editing it.
- A page given to a Mode keeps its real preview in **Focus pages**. The page is
  filtered out of the workspace, so it has no view to photograph, and the whole
  set of page snapshots was replaced on every capture — which dropped exactly
  the pages the dialog is there to show. They fell back to being drawn from the
  model: widgets as empty boxes, icons without their labels. A page's last
  snapshot is now kept, and written down, so it survives a launcher restart.
- Focus page previews no longer lose their wallpaper on a launcher start. The
  wallpaper is read off the display only once the launcher has settled, so the
  first pages captured had none behind them and replaced the ones that did. A
  page captured without it now stands in only for a page that has no picture at
  all, and the wallpaper itself is kept between starts.
- A page the launcher has not filled in yet is no longer captured. Its picture
  was the wallpaper and nothing else, and it replaced the real one.
- Icons in a page drawn from the model are themed when the home screen's are.
  The launcher no longer has the setting this asked for, Themes
  .isThemedIconEnabled, so every icon came back in full colour beside a
  monochrome home screen. The themed icon is now simply asked for: the launcher
  hands one back only when it has one.
- A page drawn from the model uses the launcher's own grid, read from the page,
  rather than a guessed four by six, so its icons and widgets are the size they
  are on the home screen.
- A page with no snapshot of its own is drawn as a home screen rather than as
  a blank one. Its widgets are drawn with the same preview pictures the
  launcher's widget picker uses, on a dark surface instead of a near-white
  panel, and the search bar and folders follow the launcher's own colours.
- The wallpaper is no longer read off the display while a notification is on
  it. The shade takes the window focus and was already waited out, but a
  heads-up notification does not take it, so one arriving as the launcher came
  forward was captured and then used as the wallpaper for every page preview
  until the launcher restarted. The display is now read only once the launcher
  has been in front for longer than such a notification lasts.
- Going home with the back gesture no longer stutters. Reading which Modes are
  on is a call into this module's own app, which answers by shelling out as
  root, and it ran on the launcher's UI thread: 130ms with that app already up,
  490ms when the call had to start it. The launcher regains window focus
  part way through the back animation home, so the stall landed inside a running
  animation. Swiping home never stuttered because focus arrives there once the
  animation has finished. The reading now happens on its own thread.

## 0.0.3

Third alpha.

### Added

- App drawer search: three switches that each take one group out of the search
  results — **Hide Web Search**, **Hide Play Store** and **Hide Search in
  Apps** — on a new **App Drawer** page in Home settings. A group's heading
  goes with its rows, so nothing is left as a title over nothing, and apps,
  shortcuts, Settings results and tips are untouched. The results reach the app
  drawer from two places at once, the platform's search service and the Google
  app, and both are filtered because the filtering happens where the launcher
  merges them.
- **Open Web Search with**, on the same page: which app a tapped Web Search
  result opens. The list is every app on the device that opens a website link,
  and what is searched for is the result that was tapped rather than what was
  typed, which is the difference between typing *weather* and asking for
  *weather tomorrow*. The results themselves are opened as a link, so the app
  shows them on the first tap rather than offering to search again. Left alone,
  or pointed at an app that is later uninstalled, the launcher's own answer
  stands.

### Changed

- Home settings names the pages **Home Screen**, **App Drawer**, **Overview**
  and **Tablet Layout (Experimental)**, each with a line saying what it holds,
  so the section reads as a menu rather than as four words. The lines under the
  tweaks themselves are shorter: they sit among the launcher's own rows, which
  say what a setting does and what it needs in one line.

### Removed

- The module's own screen, and with it the activity, the application class, the
  framework-binder listener that only the screen consumed, and every Compose
  dependency. Every setting already lived in the launcher's own Home settings,
  so the screen opened on nothing to change and existed to answer one question:
  whether a framework had picked the module up. An Xposed manager answers that
  itself. The debug APK goes from 33 MB to 7 MB, and one runtime dependency is
  left.
- The application no longer declares a class of its own. What runs in this
  process is the two providers — Modes, and the screen lock — and a provider
  needs neither an `Application` nor an `Activity`.

### Changed

- The application icon is the mark alone, white, centred on the flat brand
  circle, rather than a small badge under a long diagonal shadow. The badge read
  as a circle inside the mask's circle at the one size the icon is actually
  seen, in an Xposed manager's list. The monochrome layer is now the same paths
  at the same coordinates, so the themed icon is the drawing rather than an
  approximation of it.
- The README leads with what the module is and why it has no settings app, and
  the feature list is grouped the way Home settings groups it. The screenshots
  are gone: they were captures of a real home screen, and the ones worth showing
  could not be taken without showing its contents.

## 0.0.2

Second alpha.

### Added

- Focus home screens: a home screen page can be given to one of the device's
  Modes. While that Mode is on the launcher shows only its pages, and when it
  ends the ordinary pages come back and the Mode's own are put away. Use
  **Focus pages** under Home settings to assign it.
- **Focus pages**, a row under the switch that opens a two-step chooser: pick a
  Mode, then pick its pages from live previews of the home screens themselves.
  A Mode that is on is marked, a page with no assignment reads as **No pages**,
  and the chooser says which of the three things is missing when it cannot
  offer a choice — pages the launcher has not built yet, Modes it could not
  read without root, or a device with no Modes at all.
- Unit coverage for the Focus model filter, which decides what the launcher is
  allowed to see.

### Fixed

- **Focus pages** previews show each page's own contents. The wallpaper behind a
  preview is captured by hiding the launcher and sampling the display, but the
  launcher was made visible again beside the capture request rather than when
  its result arrived, and the request itself was made one frame after the window
  turned transparent. Both raced the compositor, so the captured wallpaper was a
  screenshot of the home screen, which every preview then drew underneath its
  own page.
- Tablet mode and tablet taskbar only: switching between two apps along the
  bottom edge no longer leaves the taskbar drawn across the app it switched to.
  The taskbar is hidden for the length of a recents animation, because the window
  manager lends its window to the app, and it stands down early when the launcher
  says it is heading somewhere the taskbar aligns with — the way home, where the
  icons travel to the hotseat and that hand-off is worth watching. A quick switch
  reports that same heading part way through, so the taskbar was shown while its
  window was still the app's, and stayed there because the transition it belonged
  to was already over. Standing down now also asks whether the launcher is in
  front, which a quick switch answers for itself.
- Tablet mode and tablet taskbar only: the taskbar knows whether the launcher is
  in front again, and the hotseat is no longer empty after the launcher restarts.
  That answer reaches the taskbar from the shell, as a report of whether home is
  visible, and on a restart it arrives out of order: the taskbar is built with
  the launcher already resumed, records that, and is then handed a report
  belonging to the launcher it has just replaced.

  ```
  updateStateForFlag(1, true)  <- LauncherTaskbarUIController.init
  updateStateForFlag(1, false) <- HomeVisibilityState$init$1$onHomeVisibilityChanged
  ```

  Nothing takes that back, because the shell reports changes and nothing has
  changed since, so the taskbar spends the session believing the launcher is
  behind something. It decides a great deal on that answer, and the visible half
  was the hotseat: a launcher with a taskbar stops drawing its own hotseat icons
  while it believes the taskbar's are covering them, and the taskbar those icons
  were handed to is stashed, as a transient taskbar is on home.

  The same answer goes stale the other way round at the end of a quick switch
  between two apps: the launcher is in front for the length of the gesture and
  the shell says so, an app arrives instead, and no further report is made
  because home's visibility has not changed again. The taskbar is then told it is
  not in an app, brings its icons out of the pill, and sits across whatever is in
  front.

  It goes stale a third way going into the Google feed and back. That is an
  activity in the launcher's own task, so home's visibility never changes as far
  as the shell is concerned, and the taskbar is left with the launcher behind
  something and the hotseat empty.

  All three are repaired where the launcher can prove the answer wrong, and
  nowhere else: a report that the launcher is behind something is dropped while
  the launcher is resumed and was only just built, and a launcher that has paused
  or resumed says so when the taskbar disagrees — about having it in front, or
  about counting itself as somewhere other than in an app. The correction is made
  on the next message rather than after a delay, because the taskbar starts
  drawing itself out of the pill 18 ms after a pause, and a correction that
  arrives later is a taskbar that appears and then collapses. Every other report is the
  shell's to make, because this answer also drives the taskbar's animation
  between home and an app, and a launcher that states it whenever it likes
  animates the taskbar at moments it never intended.
- The wallpaper behind a preview is no longer captured through the notification
  shade, or part way through a page change. The launcher keeps running while the
  shade covers it, so the capture waits for the launcher's window to come back to
  the front rather than for a resume that may never arrive, and a frame sampled
  while anything was in front is dropped instead of kept. Page snapshots are
  taken from launcher views, which nothing in front can reach, so they no longer
  wait on any of this.
- The page gallery no longer squashes previews. Its columns were stretched to
  fill the dialog, which made them wider than the width the row height was
  measured for.
- Newly-created home pages appear in **Focus pages** immediately. The launcher
  commits a dragged-to empty page by changing its screen order directly, so
  waiting for a model callback missed it.
- Full model binds no longer replace the complete page catalogue with the
  filtered screens passed to `bindAddScreens`.
- Enabling or disabling Focus home screens, or changing a page assignment,
  rebuilds the workspace even when the active Mode id did not change.
- The launcher is handed a filtered model and nothing else, so no page is ever
  written out of the database. The items are what is filtered: the launcher
  keeps no list of screens, and derives one by walking the items and collecting
  the screens they name. The one write that would turn
  a hidden page into a deleted one, `Workspace.stripEmptyScreens`, is held off
  while a Mode is on, and the feature refuses to install if it cannot find it.
- Modes are read through root, in this module's own app, and answered to the
  launcher through a content provider that serves no other caller. The platform
  API is not used: since Android 15 `getAutomaticZenRules` reports only the rules
  the calling app owns, so on a real device it returns nothing at all rather than
  an error. Measured on Android 17 as `granted=true rules=0 error=null`.
- Modes are looked at when the launcher returns to the front and when Do Not
  Disturb changes, rather than on a timer. Driving and Transit run without
  touching Do Not Disturb, which is why returning to the launcher is the second
  prompt rather than the only one.

### Removed

- Blur Wallpaper, and every way it was tried. The depth floor under the
  launcher's own state handler shared one number with the app drawer and
  Recents, so every launcher state and every launcher update was a way for the
  two to disagree. Replacing the wallpaper with a blurred copy of itself fixed
  that but could not cover a live wallpaper, which stores no image. Blurring the
  bitmaps a live wallpaper renders from covered that in turn, but only for one
  app, by name, and restarting a wallpaper service to apply it risks clearing
  the wallpaper it was meant to blur.
- The Wallpaper & Style hook, and with it the `com.google.android.apps.wallpaper`
  scope. The module is scoped to the launcher alone again, and every setting is
  in the launcher's own Home settings.
- The `Settings.Secure` key the switch was stored in. A value left there by an
  earlier version is ignored and never read.

## 0.0.1

First alpha.

### Added

- Settings as a section of the launcher's own Home settings, appended under the
  landscape switch. They are built from the launcher's `androidx.preference`
  classes by reflection, and their titles and summaries are read out of this
  module's APK, so every tweak is still described once.
- Settings stored in one preference file in the launcher's own data directory,
  written and read by the same process. Installation intercepts
  `LauncherApplication.onCreate` after its base context exists, before launcher
  startup code initializes device profiles.
- Live settings: a feature that can react to a running launcher is installed
  whatever its setting says and reads that setting from inside its hooks, so a
  toggle takes effect on the next frame. Switching a tweak off restores the state
  the launcher itself wanted rather than leaving the change applied.
- Restart Launcher row at the end of the section, for the cases live application
  cannot cover. The launcher ends its own process from the inside, which needs no
  permission and exposes no receiver.
- Feature registry: each tweak is a `LauncherFeature`, and a failure in one is
  contained.
- Bubble button on every Overview task card, anchored to the bottom-right of the
  task thumbnail and sized as a Material 3 medium floating action button.
- Overview is put away before a bubble is raised, back onto whatever it covered:
  the app it was opened from is relaunched with `TaskView.launchWithAnimation()`,
  otherwise `RecentsView.startHome(Runnable)` runs.
- Individual hiding of the Screenshot and Select buttons in the Recents action
  row. Buttons are matched by resource id.
- Clear all in the Recents action row.
- Full tablet layout: the launcher's large-screen classification, which decides the
  grid and the taskbar together.
- Taskbar Only: the taskbar without the tablet grid, with the hotseat
  handoff, the icon count and the reveal shape corrected so the transition reads
  as the launcher's own.
- The taskbar is left out of the app's own transition into Overview. The window
  manager reparents that window — the display's navigation bar — under the app
  for the length of a recents animation, which on a phone drew the taskbar
  across the bottom of the shrinking app card until the transition settled.
  Nothing in the animation tells the launcher it happened, so the taskbar is
  hidden for exactly that transition instead. It comes back once the launcher's
  own animation has ended and the window manager has taken the navigation bar
  back — measured 75 ms apart, in that order, which is how long a fully drawn
  taskbar used to sit across the bottom of the settled Overview card. The way
  home keeps its hand-off, where the taskbar icons travel to the hotseat.
- Hide the taskbar app drawer button and its divider while Overview is open,
  with the icons that remain re-centred. The two happen at different moments,
  because they cost different things to watch: the pair is taken out as the
  transition begins, where removing it moves nothing and the taskbar is not on
  screen yet, and the row closes up over its own short animation once the
  launcher has arrived. Re-centring during the morph moved icons that were
  already being animated and read as a jump; waiting for the morph to hide the
  pair as well left the button on screen for the length of it. Both are put back
  after the launcher has left Overview, where neither is on screen. The
  re-centring is needed because the launcher sizes the row by counting children
  rather than visible ones, so a hidden pair kept its slots and left everything
  else two slots right of where it belonged.
- Double tap empty workspace to send Android's power-key event through `su`.
  Only the module app receives root access.
- Search bar opens app search: tapping the home screen search bar opens the app
  drawer with its search box focused, the way earlier Pixel Launcher versions
  did. The bar is a widget, so the tap belongs to the Google app's own
  `RemoteViews` and there is no listener to replace; it is claimed a step
  earlier, at the launcher's widget host. A tap that lands on one of the
  widget's narrower buttons — the logo, the microphone, Lens — is left alone,
  and a press that the launcher has already taken as a long one still picks the
  widget up rather than counting as a tap.
- Blur Wallpaper, switched from **Wallpaper & Style → Home screen**, directly
  under **Layout**. It raises the floor under the depth the launcher's own state
  handler asks for, so the home screen wallpaper is blurred and pushed back with
  the launcher's own blur at half the strength it uses behind the app drawer,
  and a deeper state still only deepens it rather than stacking a second blur.
  The choice is one secure setting, because Wallpaper & Style cannot see this
  module's own provider but can write secure settings; the launcher watches that
  setting, so the switch reaches a running launcher. The row's text is read out
  of this module's APK once and kept, because a module update replaces that file
  and Wallpaper & Style outlives an update — reading it per bind meant the row
  stopped appearing until the app was killed.
- Module app reduced to what Home settings cannot answer — whether a framework
  accepted the module — plus a way straight into Home settings. Settings written
  by earlier builds are carried into the new store once. It is not in the app
  drawer: every setting lives in Home settings or in Wallpaper & Style, so an
  icon would open a screen with nothing to change. The activity stays exported
  and addressable by name.
- Application icon: a launcher grid whose top-right cell has burst into a spark,
  set in a circular badge under a long diagonal shadow. The badge and shadow are
  the Pixel Launcher's idiom so the two sit together on a home screen; the mark
  is original, and Google's "G" is deliberately not reproduced. The mark alone
  serves as the themed-icon layer.
- R8 shrinking for release builds, with the module entry class kept by name.
