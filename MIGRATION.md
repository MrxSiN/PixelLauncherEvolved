# Migration

Nothing in this pass changes identity or stored state, so upgrading from any earlier release keeps
everything.

- **Package and module identity:** `applicationId` `io.github.mrxsin.pixellauncherevolved`, namespace,
  entry class `my.github.MrxSiN.pixellauncherevolved.PixelLauncherEvolvedModule`, `module.prop`
  (`minApiVersion=101`, `targetApiVersion=102`, `staticScope=true`), `scope.list` and
  `java_init.list` are unchanged (`scripts/check-project.sh` asserts them).
- **Preferences:** the file name, location (device-protected storage, launcher data directory), every
  key and every value format are unchanged. `app_drawer_hidden_apps` is still one comma-separated,
  sorted string, and it is still read on every call. The new cache only skips re-parsing identical
  text, so a value written by an older version, a backup import or a reset is picked up as before.
- **Backup/import format:** unchanged (`SettingsBackupTest` passes).
- **Providers:** `.focus` and `.screenlock` authorities, their method names and result keys are unchanged.
- **Upgrade path:** tested on device by `adb install -r` over the installed v0.1.0 and then back and forth
  between v0.1.1 and the candidate eight times. Afterwards the settings file still held the same 24
  entries, and the values checked were unchanged.
- **Version:** `versionCode` 12 / `versionName` 0.1.1 are unchanged in the working tree. A release of
  this pass needs the usual bump.
