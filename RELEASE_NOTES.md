# Pixel Launcher Evolved v0.1.2

Tested on a Pixel 8 Pro running Android 17 QPR1 (September 2026), build `CP3A.260905.009`,
Pixel Launcher `907`. A performance release: every tweak does what it did in v0.1.1, and your
settings carry over untouched.

### Faster

- **Overview**: opening and closing Overview takes the launcher about 45% less main-thread time,
  with about a quarter of the dropped frames. The task-card buttons were looking up the app chip by
  name on every frame; they remember it now.
- **App drawer**: the hidden-apps check no longer re-reads the list for every app, so the drawer
  rebuilds with less work.
- **Drawer search**: filtering results as you type creates about half the garbage per keystroke,
  and takes about 18% less time. Results are read from the system directly instead of through
  reflection.
- **Double tap to sleep**: reuses a waiting thread instead of starting one per tap.

How each change was measured, including the ones tried and dropped, is in `PERFORMANCE.md` and
`OPTIMIZATION_LEDGER.md`.
