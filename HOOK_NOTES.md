# Hook notes

Everything about the launcher below was read off a Pixel 8 Pro running Android 17
(`CP2A.260805.005`, SDK 37) by pulling
`/system_ext/priv-app/NexusLauncherRelease/NexusLauncherRelease.apk` and dumping it
with `dexdump` and `aapt2`. Re-verify these signatures before targeting a newer
launcher build.

## The bubble entry point

The launcher reaches the shell's bubble controller through its own proxy:

```
com.android.quickstep.SystemUiProxy
  public static final DaggerSingletonObject INSTANCE
  public final void showAppBubble(
      android.content.Intent,
      android.os.UserHandle,
      com.android.wm.shell.shared.bubbles.logging.EntryPoint,
      com.android.wm.shell.shared.bubbles.BubbleBarLocation)
```

`INSTANCE` is a `com.android.launcher3.util.DaggerSingletonObject`, whose
`get(Context)` returns the proxy. `showAppBubble` forwards to
`com.android.wm.shell.bubbles.IBubbles` transaction 14 and swallows
`RemoteException`, so a failed call is silent rather than fatal.

The trailing `BubbleBarLocation` is written with `Parcel.writeTypedObject`, so
`null` is accepted and lets the shell place the bubble itself.

`EntryPoint` is a `Parcelable` enum with `ALL_APPS_ICON_DRAG`,
`ALL_APPS_ICON_MENU`, `HOTSEAT_ICON_MENU`, `LAUNCHER_ICON_MENU`,
`TASKBAR_ICON_DRAG`, and `TASKBAR_ICON_MENU`. There is no Overview constant, so
the bubble feature reports `LAUNCHER_ICON_MENU`; the value only feeds telemetry.

The platform's own path is `com.android.launcher3.popup.SystemShortcut$BubbleShortcut`,
which copies `ItemInfo.getIntent()`, restates the package when the copy has none,
and calls `SystemShortcut$BubbleActivityStarter.showAppBubble(Intent, UserHandle,
EntryPoint)`. The bubble feature performs the same intent preparation.

## Overview task cards

```
com.android.quickstep.views.TaskView extends android.widget.FrameLayout
  public void onFinishInflate()
  public void onLayout(boolean, int, int, int, int)     // calls super.onLayout
  public void setFullscreenProgress(float)
  public void getThumbnailBounds(android.graphics.Rect)
  public Task getFirstTask()
  public List getTaskContainers()
```

Because `onLayout` delegates to `FrameLayout.onLayout`, an injected child is laid
out normally; the module then repositions it against `getThumbnailBounds`, which
is the visible snapshot rectangle rather than the whole card.

`TaskView` already carries a platform overlay button, `R.id.task_dismiss_button`,
laid out with `layout_gravity="top|end"`. The bubble button takes the opposite
corner.

## Putting Overview away

```
com.android.quickstep.views.RecentsView
  public TaskView getRunningTaskView()
  public void startHome()
  public void startHome(java.lang.Runnable)
  public void startHome(boolean, java.lang.Runnable)
  public abstract boolean canStartHomeSafely()

com.android.quickstep.views.TaskView
  public final RunnableList launchWithAnimation()

com.android.launcher3.util.RunnableList
  public final void add(java.lang.Runnable)
```

Both transitions take a completion callback, which is exactly the ordering a
bubble needs: raised while Overview is still up, the bubble ends up behind it.

`getRunningTaskView()` is the card of the task Overview was opened from, and is
null when Overview was opened from home. Relaunching it with
`launchWithAnimation()` returns that app to the foreground, so a bubble raised
from the returned `RunnableList` floats over the session the user was already in
rather than over home.

`RunnableList.add` runs the runnable immediately once the list has been destroyed,
so a callback registered after the launch settles is still invoked rather than
dropped. This is what makes chaining onto `launchWithAnimation()` safe without
any timing assumption.

The concrete recents class differs by surface — `LauncherRecentsView`,
`FallbackRecentsView`, `FallbackActivityRecentsView`, `FallbackWindowRecentsView`
— so the module walks up from the task card and takes the first ancestor that
answers to `getRunningTaskView()` rather than matching a class name.

`canStartHomeSafely()` guards the home path; when it refuses, the bubble is opened
without a transition instead of being dropped.

The task itself:

```
com.android.systemui.shared.recents.model.Task
  public Task.TaskKey key
com.android.systemui.shared.recents.model.Task$TaskKey
  public Intent baseIntent
  public int userId
```

## Borrowed resources

Looked up by name at runtime through `Resources.getIdentifier`, so the button
tracks the launcher's theme instead of shipping duplicates:

| Resource | Use |
|---|---|
| `drawable/ic_bubble_button` | The glyph Android 17 uses for its own bubble shortcut |
| `drawable/circle_dismiss_background` | Ripple over an oval filled with `materialColorPrimary` |
| `color/materialColorOnPrimary` | Glyph tint |
| `string/bubble` | Content description |

Sizing is *not* borrowed. The button follows the Material 3 medium floating
action button metrics — a 56dp container around a 24dp glyph, 12dp from the
thumbnail edge — because it is a primary action on the card, not a corner
affordance like the dismiss button it sits opposite.

Each lookup has a hard-coded fallback, so a renamed resource degrades the styling
rather than breaking the button.

## Gotcha: background padding

`View.setBackground` only overwrites padding when the new drawable reports its
own. `circle_dismiss_background` is a `RippleDrawable` and reports none, so a
widget's default padding survives and squeezes the glyph to a few pixels. The
button therefore sets its padding *after* the background.

## On-device verification

```bash
adb logcat -s PixelLauncherEvolved
```

A healthy launcher start logs the module load, then one line per hook and one per installed feature, for example:

```
Loading in com.google.android.apps.nexuslauncher
Hooked TaskView.onFinishInflate
Hooked TaskView.onLayout
Hooked TaskView.setFullscreenProgress
Installed overview_bubble_button
```

A successful tap logs `Bubble entry point resolved on com.android.quickstep.SystemUiProxy`
followed by `Requested bubble for ComponentInfo{...}`, and the shell answers with
`D Bubbles : BubbleTaskViewListener.onTaskCreated() ... bubble=key_app_bubble:0:<package>`.

## The Overview action row

```
com.android.quickstep.views.OverviewActionsView extends android.widget.FrameLayout
  public void onFinishInflate()
  private void updateActionButtonsVisibility()
```

The row is inflated from `res/layout/overview_actions_container.xml` as
`com.google.android.apps.nexuslauncher.overview.NexusOverviewActionsView`. Its
`action_buttons` LinearLayout holds `action_screenshot`, `action_select`, and
`action_split`.

The launcher recomputes the row whenever the selected task changes, so hiding a
button once at inflation is not enough; `updateActionButtonsVisibility` is the
private recompute and is hooked alongside `onFinishInflate`.

**Clear all is not in that layout.** On this build the row's Clear all button is
added programmatically and carries no resource id, confirmed with
`uiautomator dump`:

```
LinearLayout #action_buttons
  Button #action_select "Select"
  Button "Clear all"          <- no id
```

`com.android.quickstep.views.ClearAllButton` (`id/clear_all`,
`res/layout/overview_clear_all_button.xml`) exists in the launcher but is not
what the row shows, so hooking its `onLayout` hides nothing. Buttons are
therefore matched by resource id where they have one and by the launcher's own
`string/recents_clear_all` where they do not, which keeps the match correct in
every language.

## The settings bridge

```
io.github.libxposed.api.XposedInterface
  SharedPreferences getRemotePreferences(String group)      // hooked process, read-only

io.github.libxposed.service.XposedService
  SharedPreferences getRemotePreferences(String group)      // settings app, writable
  String getFrameworkName()
  long getFrameworkVersionCode()

io.github.libxposed.service.XposedServiceHelper
  static void registerListener(OnServiceListener listener)
```

The framework hands its binder to the app through the `XposedProvider` content
provider contributed by the service AAR, which runs before
`Application.onCreate`. `XposedServiceHelper` caches the binder until a listener
registers, so registering from `Application.onCreate` is safe and cannot miss it.

A framework that is not installed simply never answers. The app therefore waits a
short grace period and then reports the module as inactive rather than waiting
forever.

## The task menu

```
com.android.quickstep.views.TaskMenuView extends com.android.launcher3.AbstractFloatingView
  private final void addMenuOptions()
  private final void populateAndLayoutMenu()
  private com.android.quickstep.views.RecentsViewContainer recentsViewContainer

com.android.quickstep.views.RecentsViewContainer
  public View getOverviewPanel()

com.android.quickstep.views.RecentsView
  private void dismissAllTasks(android.view.View)

com.android.launcher3.AbstractFloatingView
  public final void close(boolean)
```

`addMenuOptions()` walks `TaskOverlayFactory.getEnabledShortcuts` and calls
`addMenuOption` per entry, so hooking it after gives a menu the launcher has
finished populating. The row layout is `res/layout/task_view_menu_option.xml`:
a `View` with id `icon`, whose *background* carries the glyph, and a `TextView`
with id `text`. They live in `id/menu_option_layout` from `res/layout/task_menu.xml`.

The menu is a floating view rather than a child of the task list, so the recents
view is reached through `recentsViewContainer.getOverviewPanel()`.

`dismissAllTasks` is private and declared on `RecentsView` while the instance is
a `LauncherRecentsView`, so neither `getMethod` nor a single `getDeclaredMethod`
finds it — the lookup has to walk the hierarchy.

The launcher's own menu already has a **Clear** entry, which dismisses that one
task. The added entry is Clear all, and takes the launcher's own
`string/recents_clear_all` so the two read distinctly.

## Applying settings without restarting the launcher

`RemotePreferences.apply()` updates its in-process map and notifies listeners
synchronously, and only the commit back to the framework is queued, so a read
straight after a write sees the new value on both sides of the bridge.

Two consequences shape the code:

- **Listeners are held weakly.** `RemotePreferences` keeps them in a
  `WeakHashMap`, so anything that registers one must hold the registration for
  as long as it wants the callbacks. A local variable is not enough.
- **A live feature must restore, not just apply.** Hiding a view on every
  recompute is easy; putting it back when the tweak is switched off means
  remembering the visibility the launcher itself wanted, because the launcher
  hides some of those views on its own.

The launcher process cannot be ended from outside without a privileged
permission, so the restart request travels on this same channel as a counter and
the module calls `Process.killProcess(Process.myPid())` from inside the
launcher.

## Gotcha: private methods reached by direct calls

`addMenuOptions` is called with `invoke-direct` from `populateAndLayoutMenu`,
which ART may inline. That did not stop the hook here, but if a hook on a small
private method never fires, `XposedInterface.deoptimize` on its *caller* is the
remedy.
