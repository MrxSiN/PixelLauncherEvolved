# Pixel Launcher Evolved v0.0.6

Sixth alpha. Recents cards get a split screen button, the Recents action buttons
arrive instead of appearing, this module's own settings pages are drawn the way
Android 17 settings is, and Focus pages opens again.

## Added

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

## Fixed

- **Focus pages opens again, and Focus home screens can read the Modes.** The
  launcher asks this module's app for them through a content provider, and the
  authority it asked by was written out in full — so moving the module to the
  `io.github.mrxsin` package in 0.0.5 left the launcher asking for a provider
  that no longer existed, which reads on screen as root not being granted. The
  authority is now the module's package plus a suffix, asked for at runtime the
  way the screen-lock provider already did, so a rename cannot separate the two
  again.

## Changed

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
  with — the launcher puts the row up already opaque, about 215ms after the app
  has finished shrinking into its card — so the arrival starts on the first
  frame the buttons can be drawn and runs on a clock from there.

- **This module's own settings pages are drawn the way Android 17 settings is.**
  Each row is a filled, rounded shape, and a run of rows shares the rounded ends
  of a single card. The fill is the platform's own bright surface — the colour
  the settings app fills its cards with — so it follows the wallpaper and the
  dark theme. Home settings itself is untouched, this module's section in it
  included: those rows sit among the launcher's own on a screen the launcher
  lays out, so they keep the launcher's look.

- **A page of this module's settings is named after itself.** The pages are
  built by this module rather than by the launcher's own navigation, and the
  launcher names an open page from the screen it built itself, so these opened
  under the title of the screen they were opened from.
