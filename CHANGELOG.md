# Changelog

## 0.0.7

### Added

- **Overview Only**, a third choice on the Tablet Layout page. It gives Recents
  the grid a tablet lays it out as, while the workspace keeps its phone grid and
  no taskbar appears. The three layout choices are answers to the same question,
  so switching one on switches the other two off.

  The launcher decides the whole tablet layout from one boolean on the device
  profile, but it measures the workspace, the app drawer, the hotseat and the
  taskbar from it once, while that profile is being built, and reads it again on
  every Recents layout. This tweak rewrites it after the profile is finished, so
  it reaches Recents and nothing that was already sized, and puts the phone's
  answer back for the length of each call from the launcher's own home surfaces
  — the drawer's insets and the auto-rotate setting among them.

  Overview's own dimensions are rewritten with it. Three of the nine the grid is
  built from resolve to zero below `sw600dp`, so a grid laid out on a phone's
  numbers would have no space between its rows, no margin to sit inside and no
  icon on a card; they are read again against the width the launcher itself
  calls large.

  Recents is laid out as a grid whether or not a gesture is running. The launcher
  decides that from a flag it only ever turns off, so a layout with no gesture in
  flight worked the page scrolls out as a phone's while the end of the swipe-up
  worked them out as a grid's — and the task cards jumped 84px sideways on the
  frame the gesture finished. Both sides now get the same answer.

  The Screenshot, Select and Clear all row is kept, with the three buttons a
  phone's row holds and the side margins it holds them in. A tablet offers those
  from the task menu instead, so the launcher hides the row, puts a Split button
  in it, places what is left against the taskbar, and lets the cards have the
  space — four separate decisions, each answered on its own. Horizontally the
  row now matches stock Recents exactly; vertically it sits under the cards as
  it does in stock, 36px lower in absolute terms because the grid's own
  rectangle is centred that much further down.

  The app chip on each card is fitted to the card it sits on. It is laid out from
  unqualified dimensions, so on a grid card a phone's width it covered nearly the
  whole of every preview; it is now capped in the same proportion the launcher
  caps it at for the halves of a split card, and its name is dropped rather than
  clipped to a single character when there is no room for it. Resizing a chip
  asks the Recents pager for a layout, and the pager answers a layout by running
  its page scrolls again, so the fit is done when the chip is inflated and
  without asking for one: the widths are written onto the layout parameters it
  already carries, from a ratio worked out once from the device profile.

- **The bubble and split buttons on a Recents card arrive with the card's app
  chip.** They were added when a card was inflated and drawn from that frame on,
  so they were already sitting on a card the launcher was still fading the chip
  onto. A button now takes the chip's own opacity, which is the launcher's
  account of how far Overview has arrived, so the two land together at whatever
  speed the gesture ran.

## 0.0.6

### Added

- **Show Split screen button**, on the Overview page. Every Recents card gets a
  button that starts the launcher's own split selection, so an app can be paired
  with another without the long press and menu that was the only way in.

  It sits in the bottom corner of the thumbnail, and moves to the opposite
  bottom corner while the bubble button is on, so the two are at either end of
  the card rather than crowding one side. Which corner it takes is decided every
  time a card is laid out, so switching the bubble button off brings it back
  across on the next frame.

  What the button starts is the launcher's own selection, begun the way the
  card's own menu begins it, so the prompt, the toast when an app refuses to
  split, and the animation are all the launcher's. Which half the first app
  takes is the half the launcher's menu would have chosen: asked of the
  orientation handler, because the shorter call that works this out for itself
  refuses to on a phone.

### Fixed

- **Focus pages opens again, and Focus home screens can read the Modes.** The
  launcher asks this module's own app for them through a content provider, and
  the authority it asked by was written out in full — so moving the module to
  the `io.github.mrxsin` package left the launcher asking for a provider that no
  longer existed, which reads on screen as root not being granted. The authority
  is now the module's package plus a suffix, asked for at runtime the way the
  screen-lock provider already did, so a rename cannot separate the two again.

### Changed

- **This module's own settings pages are drawn the way Android 17 settings is.**
  Each row is a filled, rounded shape, and a run of rows shares the rounded ends
  of a single card. The fill is the platform's own bright surface — the colour
  the settings app fills its cards with — so it follows the wallpaper and the
  dark theme. Home settings itself is untouched, this module's section in it
  included: those rows sit among the launcher's own on a screen the launcher
  lays out, so they keep the launcher's look.
- **The Overview action buttons arrive rather than fade up with the
  background.** The launcher fades the row holding Screenshot, Select and Clear
  all across the whole Overview opening, so they were lit and in place while the
  task cards were still moving. They now rise into place on a Material 3
  Expressive spring, each a moment behind the one before it, and the last of
  them comes to rest as the cards do.

  Coming from the home screen the arrival is driven by how far the opening has
  got rather than by a clock of its own, so it lands with the transition whether
  that was a flung gesture or a slow drag, and follows a gesture a person scrubs
  back and forth. Coming from an app there is no such transition left to land
  with — the launcher puts the row up already opaque, and does it about 215ms
  after the app has finished shrinking into its card — so the arrival starts on
  the first frame the buttons can be drawn and runs on a clock from there.
- **A page of this module's settings is named after itself.** The pages are
  built by this module rather than by the launcher's own navigation, and the
  launcher names an open page from the screen it built itself, so these opened
  under the title of the screen they were opened from.

## 0.0.5

### Fixed

- **Focus home screens now notices a Mode that switches itself on.** Only the
  interruption filter was watched, and Driving and Transit never move it — they
  run at `ZEN_MODE_OFF` — so nothing reported the change and the pages waited
  until the launcher was next brought to the front. Switching a Mode by hand
  looked immediate only because closing the shade hands the launcher back its
  window focus. The zen configuration tag is watched as well, which the
  notification service rewrites whenever a rule's state changes at all, whether
  or not it filters anything.
- **Reading the Modes no longer happens while the workspace is being built.**
  It is a call into this module's own app, answered out of a root shell, and it
  ran again on every bind — including the bind that same reading had just asked
  for. It happens once now, on the thread that asked to look.

### Changed

- **The Focus page swap is Material 3 Expressive.** It used to cut the
  workspace to invisible, hold it blank for as long as the model took to bind,
  then uncover it from the middle over three quarters of a second. Content being
  replaced now fades and shrinks on an emphasized accelerating curve, and
  content arriving is sprung into place — a real spring, slightly underdamped,
  so it overshoots a little before it settles.
- **The wallpaper keeps its blur through the animation home.** The launcher
  switches its own window blurs off for the length of that animation, which on a
  blurred home screen reads as the wallpaper snapping sharp until it lands. That
  pause is no longer passed on while the tweak is on.

  This has a cost, and it is the reason the approach was dropped once before. A
  back gesture follows an app's window off the screen, which puts the launcher's
  own content under the transition leash, and the blur applies to everything
  behind the surface it is set on — so the icons, their labels and the search
  bar can blur along with the wallpaper. Switch **Blur wallpaper** off if that
  is worse for you than the flicker it replaces.

## 0.0.4

### Added

- **Blur wallpaper**, on the Home Screen page. The wallpaper is blurred and
  pushed back while the home screen is showing, with the launcher's own blur at
  half the strength it uses behind the app drawer.

  It says what depth the home screen rests at and lets the launcher draw the
  result, so the effect is the launcher's own: no blur where the platform has
  switched cross-window blurs off, none behind an opaque scrim, and a deeper
  state deepens the blur rather than stacking a second one on it.

  **Blur strength** sits underneath it, greyed out while the switch is off. The
  middle is what the tweak did before it could be changed; right of it goes as
  deep as the launcher's own blur ever goes, left of it down to nothing.

  Both rows are drawn the way Material 3 Expressive draws them. The launcher's
  theme leaves a slider with the platform's old thin track and round knob, and
  reserves room for an icon no row here has, so the slider is aligned with the
  switches above it and redrawn with a tall rounded track, a handle that is a
  bar, and the gap that holds one off the other. Its colours are the launcher's
  own, so it follows the wallpaper.

  The launcher blurs its own workspace, not only the wallpaper, while the app
  drawer is the state being left, and it recomputes that only when the depth
  moves. A home screen that rests at a depth stops it moving on the last frame
  of the transition, so the workspace was left blurred — icons, labels and the
  search bar — until something else moved it. The launcher is now asked for that
  answer again once the state has settled.

  Arriving home from an app no longer leaves the workspace smeared. The launcher
  switches its own window blurs off for the length of that animation, which is
  meant to take the blur it puts on the workspace and the hotseat with it — but
  it recomputes that only when the depth has moved, and a home screen resting at
  a depth is the case where it has not. The launcher is asked for that answer
  again straight after it pauses. Measured over ten gestures, swiping up and
  going back both keep the wallpaper blurred throughout.

  The pause itself is left alone. It cannot be skipped: the animation reparents
  the launcher's own content under the transition leash, and a blur behind that
  leash blurs the icons and the search bar along with the wallpaper. Both ways
  home run through the same animation, and which one it is cannot be told apart
  where that decision has to be made — `HOOK_NOTES.md` records the three ways
  that were tried.

  This is the tweak removed in 0.0.3, on a design that answers why it went. What
  changes is the depth the home state itself reports, not the depth the launcher
  ends up applying. The launcher animates a state change from the depth it
  believes it is at, so raising only what it applies leaves it starting every
  transition out of home from zero — the blur drops out for the first frames of
  Overview and of the app drawer. Told that home is 0.15, the launcher animates
  from 0.15 and nothing drops out. Only the home state is answered for, so this
  module's one number is no longer something the app drawer's depth and Recents'
  depth have to stay above. The switch is a row in the launcher's own Home
  settings, so the Wallpaper & Style hook, the extra package scope and the
  secure setting stay gone.

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
