# Pixel Launcher Evolved v0.0.9

Ninth alpha, and a second repair release for Android 17 QPR1
(`CP3A.260905.009`). v0.0.8 brought back every signature that update moved.
These four were a different kind of break: the signature was still there, the
hook was placed and the feature reported `Installed`, but the launcher had moved
the work somewhere else.

Nothing new is added.

### Fixed

- **Focus home screens.** Turning a Mode on or off reloaded the home screen but
  never changed its pages, so it kept whichever set it was last shown — the
  Driving page with no Mode on, or the ordinary page with Driving on — until the
  launcher restarted. `ModelCallbacks.bindCompleteModelAsync` is still declared
  and still called, but its body is now empty. The workspace is built by
  `bindModelWithAsyncInflation`, reached from launcher start, a configuration
  change and the reload a Mode change asks for. The filter now hooks that, and
  falls back to the old name on a build that still has it.

- **Hide Select.** The Select button stayed in Overview. The Pixel action row
  shows Select again from a method the shrinker names, which each task card
  calls after the row has been recomputed. Rather than chase a name that changes
  with every build, the row is checked just before it draws; a frame in which a
  button had to be hidden is skipped, so it never flickers.

- **Taskbar Only.** The launcher crashed on the first app opened from the home
  screen, with `CalledFromWrongThreadException`. The taskbar now draws on a
  thread of its own, and the corrections that tell it the launcher has paused or
  resumed were posted to the main thread, which started the taskbar's animations
  there. They now run on the taskbar's thread.

- **Hide app drawer button.** In Recents the taskbar slid off the left edge of
  the screen. The launcher already leaves hidden buttons out of the taskbar's
  width and re-centres the row itself, so taking the button's space off again
  shrank it twice. The launcher's own width is now read either side of the first
  hide, and the space is taken off only where the launcher did not already do it.

### Changed

- With Hide app drawer button on, the taskbar is already centred when Recents
  opens. It used to arrive at its old width, icons right of centre, and slide
  across once the transition had finished.

This is still an alpha, and Tablet Layout is still marked experimental. A layout
choice needs a launcher restart to take effect.
