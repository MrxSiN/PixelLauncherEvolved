# Publishing

## GitHub Actions behaviour

`.github/workflows/android.yml`:

- Every branch push builds `assembleRelease`, signs the APK, verifies the
  signature with `apksigner`, and uploads it under the run's **Artifacts**.
- A manual **Run workflow** does the same.
- Pull requests build an unsigned release APK, because GitHub does not expose
  signing secrets to untrusted PR code.
- A pushed `v*` tag builds, signs, uploads, and attaches the APK to the matching
  GitHub Release. The tag must equal `v` plus `appVersion` from
  `app/build.gradle.kts`, or the run fails.

The expected APK name is `PixelLauncherEvolved-v<version>.apk`.

## Required repository secrets

`Settings > Secrets and variables > Actions`:

- `SIGNING_KEY` — Base64-encoded keystore contents.
- `ALIAS` — key alias.
- `STORE_PASSWORD` — keystore password.
- `KEY_PASSWORD` — key password.

Pushes, manual runs, and tag releases fail with an explicit message when any of
the four is missing, so an unsigned APK is never mistaken for a publishable
build.

### Encode the keystore

```bash
base64 -w 0 release.keystore > release.keystore.base64
```

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) |
    Set-Content -NoNewline release.keystore.base64
```

Paste the whole file into `SIGNING_KEY`. Never commit the keystore or its Base64
form.

## Required on-device check before tagging

Release builds are shrunk with R8, so a release APK has to be exercised on a
device rather than inferred from a debug build. With the module enabled:

```bash
adb logcat -c
adb shell am force-stop com.google.android.apps.nexuslauncher
adb shell input keyevent KEYCODE_HOME
adb logcat -d -s PixelLauncherEvolved
```

Every enabled feature must report `Installed <id>`. Then open Home settings and
confirm the Pixel Launcher Evolved section is there: it is drawn through
reflection, which R8 could otherwise break silently.

Then check the launcher that starts at boot, before the first unlock. Reboot,
leave the phone locked, and run the same `logcat` line with the launcher's pid
(`adb logcat -d --pid=$(adb shell pidof com.google.android.apps.nexuslauncher)`).
Logs from boot roll over quickly, so restarting the launcher while still locked
reproduces the same start if they are gone.

## Release notes

`RELEASE_NOTES.md` is the body of the GitHub Release, so it only ever describes
the version being tagged. Keep it short and in this shape:

- `# Pixel Launcher Evolved v<version>`, then one paragraph: which alpha, and the
  device, Android build and framework it was tested on.
- `### What's in it` for a release that adds features, or `### Fixed` and
  `### Changed`: one bullet each, `**Name**: what a person notices.`
- `### Install`: the four numbered steps.
- `### Notes`: framework requirement, upgrade caveats, known issues.

The reasoning behind a change belongs in `CHANGELOG.md` and the commit, not here.

## Local checks

```bash
./scripts/check-project.sh
./gradlew clean assembleRelease
```

## Cut a release

```bash
git add .
git commit -m "Release Pixel Launcher Evolved v0.0.1"
git push origin HEAD
```

After the branch run succeeds:

```bash
git tag -a v0.0.1 -m "Pixel Launcher Evolved v0.0.1"
git push origin v0.0.1
```
