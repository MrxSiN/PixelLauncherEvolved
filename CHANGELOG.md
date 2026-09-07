# Changelog

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
