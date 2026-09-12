# Pixel Launcher Evolved v0.0.7

Seventh alpha. One tweak: **Overview Only**, a third choice on the Tablet Layout
page, which gives Recents the grid a tablet lays it out as while the workspace
keeps its phone grid and no taskbar appears.

The launcher decides the whole tablet layout from a single boolean, but it
measures the workspace, the app drawer, the hotseat and the taskbar from it once
while the device profile is being built and reads it again on every Recents
layout — so this rewrites it after that profile is finished, and hands the
phone's answer back to the dozen of the launcher's own surfaces that read it
later. Recents keeps the Screenshot, Select and Clear all row a phone has, and
the grid's own dimensions are read against the width the launcher itself calls
large, because three of the nine resolve to zero below `sw600dp`.

Two things about the swipe into Overview were measured off screen recordings
rather than guessed at, and both are fixed: the task cards jumped 84px sideways
on the frame the gesture finished, because Recents only agreed it was a grid
while a gesture was in flight; and the app chip is now fitted to its card without
ever asking the Recents pager for a layout, which is what the pager answers by
running its page scrolls again.

The bubble and split buttons on a card now arrive with that chip instead of from
the frame their card was inflated.

This is still an alpha, and Tablet Layout is still marked experimental. A layout
choice needs a launcher restart to take effect.

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
