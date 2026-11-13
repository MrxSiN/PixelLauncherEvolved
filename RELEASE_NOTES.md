# Pixel Launcher Evolved v0.1.8

Tested on a Pixel 8 Pro running the November 2026 Android 17 update (`CP4A.261105.040.A1`), Pixel
Launcher `907`, with Vector 2.2. Your settings carry over.

### New

- **At a Glance, your way.** Add your own cards and extras (calendar, notifications, any app widget,
  screen time, greeting, clock, date, battery, next alarm, torch), choose when each shows (a part of
  the day or a custom time and days, while charging, playing music, on headphones, Wi-Fi or mobile
  data), hide them after a while, and put At a Glance on every home screen or per Mode. A live,
  swipeable preview shows the result, and **Demo** lets you try any moment without waiting for it.
  Touch and hold At a Glance → **Customize** to open it.
- **Check for updates.** Once per launcher start the module asks GitHub for the newest release and
  offers it on your home screen. Turn it off under **Advanced → Updates**. The module's own app makes
  the request, because Pixel Launcher has no Internet access.

### Improved

- At a Glance, Reorder home screens, Assign home screens to Modes and App labels open as real
  Settings screens, with the same animations as the rest of Home settings.
- Module pages no longer scroll under the status bar.
- Fewer allocations on every At a Glance update (measured: 536 to 512 with three sources).

### Changed

- With **Show on every home screen** off, At a Glance shows only on your first home screen. During a
  Mode that hides it, choose **All home screens** or a page in that Mode's settings to keep it.

### Fixed

- Calendar cards skipped events created on the phone.
