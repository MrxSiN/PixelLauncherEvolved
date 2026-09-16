# Pixel Launcher Evolved v0.0.8

Eighth alpha, and a repair release. Android 17 QPR1 (`CP3A.260905.009`) moved
eleven of the launcher signatures this module reads, and every one of them
disabled a tweak without saying so: the hook was placed, the feature reported
`Installed`, and nothing happened.

Nothing new is added here. Everything that worked on `CP2A.260805.005` works
again, and the notes in `HOOK_NOTES.md` now carry both builds' signatures side
by side so the next launcher update is a shorter read.

### Fixed

- **The Overview action row.** Its recompute, `updateActionButtonsVisibility`,
  was inlined into `updateForGroupedTask(boolean)`, which still logs the old
  method's name. Hiding Screenshot or Select, and adding Clear all, both stopped
  surviving a change of selected task — the button came back, or went missing,
  as soon as another card was picked. The two names now live in one place
  instead of in each feature that needs them.

- **The app drawer's search results.** `setSearchResults` gained a second
  argument, so nothing was filtered any more: hidden apps, Play Store results
  and web suggestions all came back whatever the settings said. The launcher's
  new argument says whether to scroll the list back to the top and is handed
  back untouched.

- **The home screen search bar.** It lost the host view class that identified
  it and is now built as a plain widget host like any other. A tap on the bar
  went to the Google app again instead of opening the app drawer's search. The
  bar is recognised through the launcher's own setup call for it, which covers
  the hotseat's bar as well.

- **The split button on an Overview card.** The orientation handler's
  `getSplitPositionOptions` became `getSplitPositionOption`, answering one
  position rather than a list, so the button had nothing to start the selection
  with.

- **Overview Only and Taskbar Only.** Neither could install at all:
  `DeviceProfile$Builder` moved out to a class of its own, and the two device
  profile fields they read dropped their `m` prefixes. Taskbar Only also lost
  the constructor it reported a taskbar through — the shrinker inlined it — so
  the boolean is now written on the device properties the launcher's own factory
  has just answered, which it reads back a step later. The hook that kept that
  constructor from being inlined is gone with it.

- **The taskbar's view of the launcher.** `onLauncherVisibilityChanged` took two
  more booleans. Without it the taskbar kept believing the launcher was behind
  something after a restart, which leaves the hotseat empty, and stayed drawn
  over an app after a quick switch between two.

### Changed

- Overview Only no longer keeps a Split button out of the action row. That
  button is gone from the row on every device, along with
  `updateSplitButtonHiddenFlags` and `id/action_split`, so there is nothing left
  to keep out.

- `Snackbar.getDismissTimeout`, one of the home surfaces Overview Only puts back
  on the phone's measurements for the length of their own call, is gone from the
  launcher. That list already reports and skips a surface it cannot find, so the
  rest still apply.

This is still an alpha, and Tablet Layout is still marked experimental. A layout
choice needs a launcher restart to take effect.
