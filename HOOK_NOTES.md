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

## The app drawer's search results (Android 17, verified September 8, 2026)

The results are assembled from two sources and merged before anything is drawn,
so filtering them at either source misses the other:

- **The platform's search service.** `com.google.android.apps.nexuslauncher.allapps.n4`
  builds an `android.app.search.SearchContext` with a corpus bitmask and calls
  `SearchSession.query(Query, Executor, Consumer<List<SearchTarget>>)`, which
  streams several batches per keystroke. This carries apps, shortcuts, Settings
  slices, tips, the Play Store group and the Search in Apps group.
- **The Google app.** Web suggestions come over its own binder channel, from
  `com.google.android.apps.search.googleapp.search.suggest.plugins.onesearch.server.OneSearchSuggestService`,
  and the launcher turns those into `SearchTarget`s itself. They never pass
  through `SearchSession.query`.

Both converge on one call, which is what the module hooks:

```
com.android.launcher3.allapps.ActivityAllAppsContainerView
  public void setSearchResults(java.util.ArrayList<BaseAllAppsAdapter.AdapterItem>)
```

It is reached from `com.google.android.apps.nexuslauncher.allapps.UniversalSearchInputView`,
which implements `com.android.launcher3.search.SearchCallback`; the two-argument
`onSearchResult` delegates to the three-argument one, whose `int` is a reason
code rather than a count, so the list may be shortened freely.

The items are `AdapterItem` subclasses. The one carrying a search target is
renamed by the shrinker, but its field is the only one on that class whose type
is `android.app.search.SearchTarget`, so the module finds it by type and caches
the answer per item class. An item with no such field — the launcher's own rows
— is never a candidate for hiding.

### Which result is which

Read off a device with `adb shell setprop log.tag.SearchTargetUtil VERBOSE`,
which turns on the launcher's own `SearchTargetUtil` dump of every batch:

| Group | Heading | Rows |
|---|---|---|
| Web Search | `resultType=131072`, `text_header_row` | `resultType=131072` |
| Play Store | `resultType=8388608`, `text_header_row`, `com.android.vending` | `resultType=256` |
| Search in Apps | `resultType=262144`, `text_header_row`, no package | `resultType=512` |

`resultType` is a bit set. `1 << 17` is a web suggestion, `1 << 8` a Play Store
listing, `1 << 9` an offer to run the query inside one app, `1 << 18` a result
that fulfils nothing itself, and `1 << 23` the heading above one app's group.
Apps are `1`, shortcuts `2`, Settings slices `16`, tips `8192`.

Two of those need more than the bit:

- **Play Store's heading** carries `1 << 23`, the same as the YouTube and
  Settings headings, so the package is what separates it. The **Search on Play
  Store** row in Search in Apps also carries that package, but is `1 << 9` and
  not a heading, so hiding one group never empties the other.
- **Search in Apps' heading** carries `1 << 18` — but so does every blank
  separator, `empty_divider`. Only `text_header_row` is the heading.

Separators are left alone. The launcher already emits consecutive ones, so one
left behind by a removed group reads as spacing rather than as a stray line.

### Opening a Web Search result somewhere else

The launcher answers a tapped web suggestion with an intent it builds nowhere
else:

```
com.google.android.apps.nexuslauncher.allapps.c
  public final void a(byte[], String, String, android.view.View, boolean)
      new Intent("com.google.android.PIXEL_SEARCH")
          .setPackage("com.google.android.googlequicksearchbox")
          .putExtra("onesearch_request_type", …)
          .putExtra("onesearch_request", byte[])
```

so that action is what identifies the tap. It reaches the system through

```
com.android.launcher3.uioverrides.QuickstepLauncher
  public RunnableList startActivitySafely(View, Intent, ItemInfo)
```

which is where the module swaps the intent. Three declarations of that method
exist — `ActivityContext`, `Launcher`, `QuickstepLauncher` — and the launcher
activity is a `QuickstepLauncher`, so the most derived one is the one to hook.

The call is also the only place the query is still readable. `onesearch_request`
is a protobuf meant for the Google app, but `ItemInfo.title` on the same call is
the suggestion's own words, which is what a person tapped: typing *weather* and
tapping *weather tomorrow* has to search for the second.

Pressing enter in the search box takes the same path, with the title of the
suggestion the launcher would have opened, so it follows the choice too.

`SearchActionItemInfo.onItemClicked` is **not** this path. It is unobfuscated and
looks like the click handler, but a web suggestion's row carries an `ItemInfo`
tag already, which skips the factory that method belongs to; hooking it catches
no web result.

The replacement is a **link**, `ACTION_VIEW` on
`https://www.google.com/search?q=…`, not `ACTION_WEB_SEARCH` with
`SearchManager.QUERY`. An app handed a query may do what it likes with it:
Chrome answers `ACTION_WEB_SEARCH` by opening its own search box with the words
filled in, so a tap that should have reached the results asks to be tapped
again. A link is opened once by every app that takes links.

### Gotcha: enumerating browsers needs both halves

```
Intent(ACTION_VIEW, Uri.parse("http:")).addCategory(CATEGORY_BROWSABLE)
packageManager.queryIntentActivities(probe, PackageManager.MATCH_ALL)
```

Neither half is optional, and the launcher's `QUERY_ALL_PACKAGES` does not
substitute for either:

| Asked as | Answer on the verification device |
|---|---|
| `https://www.google.com/search?q=…`, flags `0` | LinkSheet only |
| `http:`, flags `0` | LinkSheet only |
| `http:`, `MATCH_ALL` | AdGuard, Chrome, LinkSheet, MiChat |

A real address resolves to whoever verified that address rather than to the
browsers, which is Android 12's web-link behaviour; and the ordinary answer is
narrowed to the app already holding the browser role. `MATCH_ALL` is documented
as "if the platform is doing any filtering of the results, the filtering will
not happen", which is exactly the narrowing to undo.

The Google app is filtered out of the list because it is already the first
choice — the one that means leaving the launcher alone.

What is stored is a package name, not a component: which screen of an app opens
a link is the app's business and moves between versions, so it is resolved
again on every tap. That resolution is also the check that the app is still
there; an app uninstalled after it was chosen leaves the launcher's own answer
working.

### Gotcha: the launcher has its own Web search switch

Home settings → **Search settings** has one, stored as `pref_allowWebResult` in
`com.android.launcher3.prefs`, which clears `1 << 17` from the corpus mask at
session creation. It is not what this module uses: it needs a launcher restart
to take effect, it is one of three groups rather than all three, and driving it
would make the same choice readable in two places that could disagree.

## Where the settings live

They live in the launcher's own data directory, in
`shared_prefs/pixel_launcher_evolved.xml`, and both the screen that writes them
and the features that read them are the launcher process.

That is forced by the settings screen having moved into the launcher's Home
settings. The framework's own store is the obvious place to put shared settings,
but it is read-only on the hooked side:

```
io.github.libxposed.api.XposedInterface
  SharedPreferences getRemotePreferences(String group)      // hooked process, read-only
```

so a switch drawn inside the launcher has nothing it could write to there. A
file the launcher owns has no such problem, is visible to a preference change
listener in the same process immediately, and needs no bridge at all.

The framework's store is still read once, to carry across the settings earlier
versions wrote to it. The marker for that is written whether or not anything was
copied, so a later change made in Home settings is never overwritten by a stale
value the old store still holds.

### Gotcha: the module is loaded before the process has a Context

`onPackageLoaded` runs as the launcher's class loader is created, which is inside
`handleBindApplication` and before the `Application` exists. There is no
`Context` to open a preference file with, and `ActivityThread.getApplication()`
is still null.

Installation therefore intercepts:

```
com.android.launcher3.LauncherApplication extends android.app.Application
  public void onCreate()
```

which is hooked because it is actually declared, so the hook cannot be missed by
a subclass that forgets to call `super`. `Application.onCreate` is the fallback.
Android has attached the base context before this call. The registry installs
before `chain.proceed()`, so launcher startup code cannot build a device profile
before the layout hooks exist.

### The framework service, in the module's own app

```
io.github.libxposed.service.XposedService
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
forever. That report is all the app does now; the tweaks themselves are in Home
settings.

## Home settings (Android 17, verified September 7, 2026)

```
com.android.launcher3.settings.SettingsActivity            // exported, no android:process
  intent-filter: android.intent.action.APPLICATION_PREFERENCES

com.android.launcher3.settings.SettingsActivity$LauncherSettingsFragment
    extends androidx.preference.PreferenceFragmentCompat
  protected void onCreatePreferences(Bundle, String)
  public PreferenceScreen getPreferenceScreen()
```

`res/xml/launcher_preferences.xml` ends with

```xml
<SwitchPreference android:key="pref_fixed_landscape_mode" … />
```

as the last child of the root `PreferenceScreen`, so a category appended after
`onCreatePreferences` returns lands directly under **Landscape mode**.
`PreferenceGroup.addPreference` gives a preference the next order when it has
none, which is what makes appending enough.

`onCreatePreferences` is also where the fragment walks its own screen and drops
preferences its `initPreference` refuses. Hooking after it means the added
section is never a candidate for that.

The activity carries no `android:process`, so the screen and the hooks share one
process and one preference file.

### Gotcha: androidx.preference is shrunk

The launcher ships an R8-processed copy. Class names survive, because the
preference XML names them, but the API does not:

| Wanted | State in this build |
|---|---|
| `Preference(Context)` | gone; `(Context, AttributeSet)` and `(Context, AttributeSet, int, int)` remain |
| `setPersistent(boolean)` | gone; field `mPersistent` remains |
| `setOnPreferenceChangeListener` | gone; field `mOnChangeListener` remains |
| `setOnPreferenceClickListener` | gone; field `mOnClickListener` remains |
| `setEnabled`, `setSelectable`, `setIconSpaceReserved` | gone; fields remain |
| `setTitle`, `setSummary`, `setKey`, `setOrder` | present |
| `PreferenceGroup.addPreference`, `TwoStatePreference.setChecked` | present |

The listener interfaces are renamed *and* reshaped — R8 dropped the arguments
the launcher never read:

```
Preference.mOnChangeListener : u4.u   boolean onPreferenceChange(Object)
Preference.mOnClickListener  : u4.v   void    onPreferenceClick(Preference)
```

So the module reads those interface types off the fields rather than naming
them, and implements them with a `java.lang.reflect.Proxy` that answers by
elimination rather than by method name. A change listener that answers `false`
vetoes the change, so a boolean answer is always `true`.

The click path is intact and both fields are consulted:

```java
void performClick(View v) {
    if (!isEnabled() || !mSelectable) return;
    onClick();                                     // TwoStatePreference toggles here
    if (mOnClickListener != null) { mOnClickListener.onPreferenceClick(this); return; }
    …preferenceManager.h.onPreferenceTreeClick(this)…
}

boolean callChangeListener(Object v) {
    return mOnChangeListener == null || mOnChangeListener.onPreferenceChange(v);
}
```

Rows are built with the two-argument constructor and a null `AttributeSet`, so
the style still comes from the theme and an added row looks like the launcher's
own. They are built non-persistent, because otherwise a switch would also write
its value into `com.android.launcher3.prefs`, where nothing reads it. With
`mPersistent` false and no default value, `dispatchSetInitialValue` does nothing,
so the `setChecked` made before the row is added survives being attached.

### Reading the module's own strings from inside the launcher

```
PackageManager.getResourcesForApplication(ApplicationInfo)   // public API
XposedInterface.getModuleApplicationInfo()
```

The `ApplicationInfo` overload builds resources from `sourceDir` and does not
perform a package-visibility lookup, so the launcher can read this module's
`strings.xml` without the launcher declaring a `<queries>` entry. Titles and
summaries therefore stay in one place instead of being duplicated as constants.

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

A write and every read of it are in the same process, so `apply()` is enough: a
feature that reads its setting from inside a hook sees the new value on its next
invocation, and Overview lays out again constantly.

One consequence shapes the code:

- **A live feature must restore, not just apply.** Hiding a view on every
  recompute is easy; putting it back when the tweak is switched off means
  remembering the visibility the launcher itself wanted, because the launcher
  hides some of those views on its own.

The launcher process cannot be ended from outside without a privileged
permission, so the restart is the launcher ending itself: the Restart launcher
row calls `Process.killProcess(Process.myPid())`, and Android brings the home app
straight back. Verified on device — the settings screen dies with the process and
the module reloads into the new one within half a second.

## Gotcha: private methods reached by direct calls

`addMenuOptions` is called with `invoke-direct` from `populateAndLayoutMenu`,
which ART may inline. That did not stop the hook here, but if a hook on a small
private method never fires, `XposedInterface.deoptimize` on its *caller* is the
remedy.

## Tablet mode (Android 17, verified September 7, 2026)

The installed launcher uses a shared classifier:

```
com.android.launcher3.display.LauncherDisplayInfo
  public final boolean isLargeScreen(com.android.launcher3.util.WindowBounds)
```

DEX inspection confirms that `DeviceProfile.Builder`, `InvariantDeviceProfile`,
and taskbar code call this method before deriving their dimensions. The module
returns true only when tablet mode was enabled at process startup. It does not
rewrite display density, dimensions, global Configuration, or profile fields.
The registry skips the hook when disabled; both transitions require a restart.
A missing class or signature is contained by the registry's installation guard.

ADB verification on Pixel 8 Pro with Vector build 3080:

- Before: `isTablet:false`, `isPhone:true`, `isTaskbarPresent:false`.
- Enabled and restarted: `isTablet:true`, `isPhone:false`, `isTaskbarPresent:true`.
- Settings survive reopening the app; Overview opens without a launcher crash.
- Disabled and restarted: stock phone classification restored.

This selects tablet layout on the existing physical display; it does not turn
phone System UI into a tablet system. Widgets and labels may become cramped.

## Tablet taskbar only (Android 17, verified September 7, 2026)

The taskbar and the tablet grid are decided together, in one expression inside
`DeviceProfile$Builder.build()`:

```
isTaskbarPresent = LauncherDisplayInfo.isLargeScreen(windowBounds)
                   && WindowManagerProxy.isTaskbarDrawnInProcess()
```

There is a single `isLargeScreen` call, and its result is both the grid's
classification and the taskbar's, so the two cannot be separated there. They
separate one step later: the answer is stored as the only field of

```
com.android.launcher3.deviceprofile.TaskbarConfiguration
  public final boolean isTaskbarPresent
```

which this build reaches by direct field access rather than through a getter.
Constructing that class with `true` gives a taskbar while `isLargeScreen`, and
therefore the grid, the app drawer and Recents, stays on phone measurements.

`DeviceProfile` on this build composes small profile objects —
`DeviceProperties`, `HotseatProfile`, `TaskbarProfile`, `WorkspaceProfile` —
and each is derived from that one boolean:

```
TaskbarProfile$Factory.createTaskbarProfile(Resources, isTransient, isPresent, spec)
```

returns every dimension zeroed when `isPresent` is false, so nothing else has
to be forged.

### Gotcha: one process, one profile

An earlier attempt gave the taskbar window a large-screen profile of its own and
left the launcher on its phone profile. Both windows then drew, but the
launcher's profile reported `taskbarHeight: 0`, `taskbarIconSize: 0` and
`taskbarBottomMargin: 0`, and hotseat alignment reads those numbers from the
launcher's profile rather than the taskbar's. The icons animated towards zero
and the taskbar row landed off the bottom of the screen.
`LauncherTaskbarUIController.onStashedInAppChanged` then rebuilt `TaskbarProfile`
on the launcher's profile, which is how a stray `isTaskbarPresentInApps:true`
appeared on a profile that reported no taskbar at all.

Two profiles in one process is the bug. The rewrite applies to every profile the
process builds.

### Gotcha: an inlined constructor

`TaskbarConfiguration.<init>` is one field assignment reached by `invoke-direct`,
which ART is free to inline. Its caller, `DeviceProfile$Builder.build()`, is
deoptimized before the hook is placed.

### Where the search bar ends up

`DeviceProfile.getQsbOffsetY()` places the search bar from the same boolean:

```java
if (isQsbInline)      return getHotseatBarBottomPadding() - (qsbHeight - cellHeightPx) / 2;
if (isTaskbarPresent) return barSizePx - qsbHeight + qsbShadowHeight;  // top of the hotseat bar
                      return barBottomSpacePx - qsbShadowHeight;       // below the icons
```

So switching the taskbar on moves the search bar above the hotseat icons, which
is where tablet mode puts it too. Measured on a Pixel 8 Pro:

| | stock phone | tablet mode | taskbar only |
|---|---|---|---|
| `isTablet` | false | true | false |
| `numShownHotseatIcons` | 4 | 6 | 4 |
| `isTaskbarPresent` | false | true | true |
| `taskbarHeight` | 0 | 229px | 229px |
| `taskbarIconSize` | 0 | 171px | 171px |
| Search bar within the hotseat | 252–441 | 0–189 | 0–189 |

Tablet mode changes the grid from 4x6 to 6x5, and `ModelDbController` logs
`Cannot migrate from source ... to destination` when the workspace cannot be
carried across. Taskbar only keeps the phone grid and never triggers that
migration.

### Gotcha: there are two device profiles, and they disagree

The launcher process builds one `DeviceProfile` for the launcher window and
another for the taskbar's own window. The app-to-home morph reads from both:

```
com.android.launcher3.taskbar.TaskbarLauncherStateController
  private void onIconAlignmentRatioChanged()
    // X and scale targets come from the launcher's profile
    taskbarViewController.animateChildViews(view, anim, launcherDp, taskbarDp, …)
    // Y comes from the taskbar window's profile
    setFloat(taskbarIconTranslationYForHome,
             -taskbarActivityContext.mDeviceProfile.getTaskbarOffsetY(), interpolator)
```

The taskbar window is only a few hundred pixels tall, and the responsive grid
sizes that profile's workspace icons for the window rather than for the screen,
so the two profiles do not agree on a phone grid:

| | taskbar window profile | launcher profile |
|---|---|---|
| `mWorkspaceProfile.iconSizePx` | 171px | 198px |
| `getHotseatBarBottomPadding()` | 118px | 132px |
| `getTaskbarOffsetY()` | **107px** | **129px** |

`getTaskbarOffsetY()` is, for a transient taskbar,

```java
getHotseatBarBottomPadding()
    + min((hotseat.cellHeightPx - workspace.iconSizePx) / 2,
          workspace.gridVisualizationPaddingY)
    - (taskbar.height - workspace.iconSizePx) / 2
```

so the 27px difference in icon size propagates into a 22px difference in the
offset. The icons therefore settle 22px below the hotseat row and step onto it
when the morph hands over. On a tablet both profiles agree and there is no step.

Answering `getTaskbarOffsetY()` with the launcher's profile removes it. The
launcher's profile is captured from `TaskbarLauncherStateController.getDeviceProfile()`,
which returns `LauncherUiState.deviceProfileRef`.

Measured on a Pixel 8 Pro with `animator_duration_scale` at 20, sampling the
bottom edge of the hotseat icon row through the transition:

| | last animated frames | after handoff | step |
|---|---|---|---|
| Stock offset | 2860px | 2837px | 23px |
| Cell-slack term only | 2852px | 2837px | 15px |
| Launcher's profile | 2837px | 2837px | **0** |

`animator_duration_scale` and `transition_animation_scale` are the only way to
see this over adb; both are set back to 1 afterwards.

Two other explanations were measured and ruled out first. The `FINAL_FRAME`
branch of that interpolator is not taken — `isHotseatIconOnTopWhenAligned()`
logs `true` throughout — and the offset is not being read from a stale profile,
because both profiles are live and each is internally consistent.

The override applies to every `getTaskbarOffsetY()` call made on a profile that
is not the launcher's, which also covers
`NavbarButtonsViewController.addVisibleButtonsRegion`. That matters only in
three-button navigation, which this has not been checked against.
`com.android.launcher3.views.Snackbar` asks the launcher's own profile and is
unaffected.

### Gotcha: the taskbar mirrors fewer icons than the hotseat holds

```
com.android.launcher3.taskbar.TaskbarView
  private int calculateMaxNumIcons()
  private int mIconTouchSize
  private int mItemMarginLeftRight
```

`calculateMaxNumIcons()` divides the taskbar window's width by a slot of
`mIconTouchSize + 2 * mItemMarginLeftRight` and adds the views the taskbar
always carries itself. Logged on this device it answers **5** for a slot of
`171 + 2 * 29 = 229px`, and the taskbar then shows **three** app icons — the
other two slots are the all apps button and the divider. That is where the
constant 2 comes from; it is read off the measurement, not assumed.

A phone hotseat holds four icons and a tablet's holds six, so on either the
taskbar mirrors fewer icons than the hotseat has, and the leftovers have
nothing to morph out of. They appear fully drawn in the frame the transition
ends. Raising the limit to `numShownIcons + 2` gives every hotseat icon a
counterpart; the stock answer stays the floor, so the hook only ever adds
capacity.

Verified by sampling the app-to-home transition at `animator_duration_scale`
20: before, the icon row carried three icons until the last frame and then
four; after, all four leave the stashed handle together and travel to their
hotseat positions.

**Not verified:** the taskbar as it appears *inside* an app, where the all apps
button, the divider and recents share those slots. `input motionevent` and
`input swipe` do not carry enough velocity through the gesture handler to
unstash a transient taskbar, so this could not be reached over adb. Six slots
of 229px come to 1374px against a 1344px screen, so the in-app taskbar is worth
a look by hand for cramped or clipped icons.

### Gotcha: icons are revealed by splitting open across the middle

```
com.android.launcher3.taskbar.TaskbarViewController
  private int mStashedHandleHeight
  void animateIconsForReveal(ViewGroup, AnimatorSet, AnimatorSet, boolean,
                             int, long, android.graphics.Rect, boolean)
```

`TaskbarStashController.createAnimToIsStashed` passes
`StashedHandleViewController.mStashedHandleBounds` as that `Rect`, and each icon
is revealed through a clip that starts as a band across its middle:

```java
Rect iconRect = new Rect(0, 0, child.getWidth(), child.getHeight());
int centerY = iconRect.centerY();
int half    = mStashedHandleHeight / 2;
int top     = centerY - half;
int bottom  = centerY + half;
```

The band is 96px against an icon of `mIconTouchSize` 171px, so a little over
half the icon is shown and the rest opens outwards. On a tablet the icons
hardly move while that happens; on a phone hotseat they are also crossing a
third of the screen and growing, and the split is what the eye follows.

Setting the band to the icon's own height for the duration of that call starts
the reveal at full height, so icons emerge sideways out of the pill instead of
splitting. `TaskbarViewController.mStashedHandleHeight` is written once in
`TaskbarActivityContext.<init>` and read only by this animation, and the
identically named field on `StashedHandleViewController` — which outlines the
pill in `getOutline` — is a different field and is left alone. The value is
restored after the call rather than overwritten.

The same animation builds both directions. Sampled at
`animator_duration_scale` 20, app-to-home now shows whole icons from the first
frame they are visible, and home-to-app is unchanged.

## Gotcha: the window manager lends the taskbar to the app (verified September 7, 2026)

Swiping from an app into Overview sometimes drew the taskbar across the bottom
of the shrinking app card, several hundred pixels above where it belongs, until
the transition settled and it snapped back.

The taskbar had not moved. Its own view reports the same place throughout —
sampled every 50ms across the transition, `TaskbarView` sits at y=2691 with
`translationY=0` and only its alpha changes:

```
PROBE y=2691 TaskbarView[ty=0.0 top=206 h=229 a=0.08] TaskbarDragLayer[ty=0.0 top=0 h=507]
PROBE y=2691 TaskbarView[ty=0.0 top=206 h=229 a=1.0 ] TaskbarDragLayer[ty=0.0 top=0 h=507]
```

because `View.getLocationOnScreen` reads the window frame and knows nothing
about a transform applied to the window's surface. `dumpsys SurfaceFlinger` does:

```
                    taskbar frame           app frame
attached            92 2234 1252 2671       92   88 1252 2671
left alone           0 2485 1344 2992       54   27 1328 2863
```

When it goes wrong the taskbar's frame shares the app's left, right and bottom
edge exactly. The layer tree says why: the taskbar's `WindowToken` — type 2019,
`TYPE_NAVIGATION_BAR` — has been reparented from its display-level container
into the transition subtree that carries the app.

That is `RecentsAnimationController.attachNavigationBarToApp()`, the platform
behaviour that lets navigation buttons travel with an app during a recents
animation. It is gated by `config_attachNavBarToAppDuringTransition`, which is
true on a phone and false on a tablet, so a real tablet never sees it and a
stock phone never notices — with gesture navigation that window is empty. Give
the phone a taskbar and the same window is suddenly full of icons.

**It is not caused by either layout tweak.** Measured through the same
transition, tablet mode and tablet taskbar only reparent alike:

```
tablet mode          taskbar=[38 2379 1305 2856]  chrome=[38 37 1305 2856]
tablet taskbar only  taskbar=[28 2409 1316 2894]  chrome=[28 27 1316 2894]
```

It is also intermittent, and reproducibly so: it happens on the first recents
animation after the keyguard is dismissed, and not on the ones after it.

```
first Overview after unlocking   taskbar=[38 2379 1305 2856]   attached
second, same session             taskbar=[ 0 2485 1344 2992]   left alone
third                            taskbar=[ 0 2485 1344 2992]   left alone
```

### Nothing tells the launcher

The recents animation carries no navigation bar for the launcher to put back.
Its targets are the two tasks and the wallpaper, all with `windowType=-1`:

```
onAnimationStart apps=[{windowType=-1 mode=0 taskId=15533}, {windowType=-1 mode=1 taskId=15538}]
                 wallpapers=[{windowType=-1 mode=0 taskId=-1}]
                 transition={c=[{m=TO_FRONT …Task=15533}, {m=TO_BACK …Task=15538}, {m=IS_WALLPAPER …}]}
```

`RemoteAnimationTargets.getNavBarRemoteAnimationTarget()` exists, but the only
thing that reads it is `TaskViewUtils.createRecentsWindowAnimator`, which is the
other direction — launching a task out of Overview.

So the taskbar cannot be corrected. It can be left out of the transition
instead, which is what `TaskbarTransitionFeature` does: alpha 0 on the taskbar
window's root view when `RecentsAnimationCallbacks.onAnimationStart` reports the
animation, and back to 1 when the launcher's own animation for that transition
has ended **and** the window manager has taken the navigation bar back.

### Where the two ends come from

```
com.android.quickstep.RecentsAnimationCallbacks
  public void onAnimationStart(RecentsAnimationControllerCompat, RemoteAnimationTarget[],
                               RemoteAnimationTarget[], Rect, Bundle, TransitionInfo)
  public void onAnimationCanceled(java.util.HashMap)
  public void onAnimationFinished(com.android.quickstep.RecentsAnimationController)

com.android.launcher3.taskbar.TaskbarLauncherStateController
  public Animator applyState(long, boolean)
  public LauncherState mLauncherState

com.android.launcher3.LauncherState
  public boolean isTaskbarAlignedWithHotseat()
```

`applyState` is called once per transition, on the UI thread, immediately after
the animation is reported, and answers both questions at once: the animator it
returns ends when the transition does, and `mLauncherState` says where the
launcher is heading.

`onAnimationFinished` is **not** a usable end: landing in Overview keeps the
recents animation alive, and waiting for it leaves the taskbar invisible for as
long as Overview is open. That was measured before it was designed around.

The way home is deliberately left alone, because its taskbar icons travel to the
hotseat and that hand-off is worth watching. `isTaskbarAlignedWithHotseat()` is
true for that destination and false for Overview, and the check lands in the
same millisecond the animation starts:

```
PROBE hide
PROBE applyState toHotseat=true aligned=true result=AnimatorSet
PROBE show (heading home)
```

### Gotcha: the report arrives off the UI thread

`onAnimationStart` is delivered on a background thread, and `applyState` runs on
the UI thread a millisecond later. A first version set the "hidden" flag inside
a `View.post`, so the flag was still unset when `applyState` ran, the animator
was never listened to, and the taskbar stayed invisible for the whole time
Overview was open. The decision is now made synchronously; only the view is
touched from the UI thread.

### Gotcha: the launcher settles before the window comes back

Waiting only for the launcher's own animation left a flash: a fully drawn
taskbar across the bottom of the settled Overview card, for four or five frames,
before it snapped down to where it belongs. The two moments are not the same
one, and the gap is consistent:

```
PROBE hide              23:39:46.984
PROBE launcher settled  23:39:47.522
PROBE window returned   23:39:47.603     // 81 ms later
```

Three consecutive gestures measured 74, 81, 75 and 75 ms, always in that order.

The hand back is asked for by

```
com.android.quickstep.RecentsAnimationController
  public void detachNavigationBarFromApp(boolean)      // posts to UI_HELPER_EXECUTOR

com.android.systemui.shared.system.RecentsAnimationControllerCompat
  public void detachNavigationBarFromApp(boolean)      // IRecentsAnimationController #5
```

The first only posts the request, so hooking it is still too early. The second
is the call that crosses to the window manager, and it is a plain binder
transaction rather than a one-way: when it returns, the navigation bar has been
put back in its own place and only its fade in is still to come. That return is
the second of the two ends the taskbar waits for.

It is reached on `UI_HELPER_EXECUTOR`, so like `onAnimationStart` it arrives off
the UI thread, and the same rule applies — decide synchronously, touch the view
in a post.

For the way home there is nothing to wait for: that path stands down at
`isTaskbarAlignedWithHotseat()` before either end is reached. Anything that
never reports the hand back — a cancelled gesture, a quick switch to another
app — is covered by `onAnimationCanceled` and `onAnimationFinished`, which show
the taskbar outright, and by the two-second safety net behind them.

## Blurring the wallpaper on the home screen

Read off the same device, from
`/system_ext/priv-app/WallpaperPickerGoogleRelease/WallpaperPickerGoogleRelease.apk`
as well as the launcher.

```
com.android.quickstep.util.BaseDepthControllerImpl
  protected final int mMaxBlurRadius
  protected int mCurrentBlur
  private float mDepth
  private void setDepth(float)
  private static float mapDepthToBlur(float)
  private void applyDepthAndBlur(SurfaceTransaction, boolean, boolean)
  public void applyDepthAndBlur()
  public boolean shouldBlur()

com.android.launcher3.statehandlers.DepthController extends BaseDepthControllerImpl
com.android.launcher3.statehandlers.LauncherDepthController extends DepthController
```

One float drives the whole effect. `applyDepthAndBlur` computes

```
mCurrentBlur = shouldBlur() ? (int) (mMaxBlurRadius * mapDepthToBlur(mDepth)) : 0
```

puts that on the wallpaper surface with
`SurfaceTransaction.SurfaceProperties.setBackgroundBlurRadius`, and passes the
same depth to `WallpaperManager.setWallpaperZoomOut`, which is why the launcher's
own blur comes with the wallpaper pushed back rather than only softened.

`mapDepthToBlur` is `Interpolators.clampToProgress(LINEAR, depth, 0f, 0.3f)`, so
the blur reaches the full radius at a depth of `0.3` and scales linearly below
it. The module's floor of `0.15` is therefore half of the launcher's maximum.

`shouldBlur()` is `mCrossWindowBlursEnabled && !scrimView.isFullyOpaque() &&
!mPauseBlurs`, so a floor raised here still respects the platform switching
window blurs off, and still yields to an opaque scrim.

`setDepth` bounds its argument to `0..1`, quantises it, and returns early when
the value has not moved. Raising its argument is enough to raise the resting
depth of every state; the launcher's own deeper states are already above the
floor, so nothing stacks.

### Gotcha: the depth setter is reached through a synthetic bridge

```
BaseDepthControllerImpl$1.setValue(BaseDepthControllerImpl, float)   // the DEPTH FloatProperty
  -> BaseDepthControllerImpl.c(BaseDepthControllerImpl, float)       // static, synthetic, 4 units
     -> BaseDepthControllerImpl.setDepth(float)                      // private
```

The bridge is four code units long and is the only caller, so ART is free to
inline the setter past a hook on it. Its name is produced by the shrinker, so the
module deoptimises it by shape instead: the one static method on the class that
returns nothing and takes a controller and a float.

### The switch, in Wallpaper & Style

```
com.android.wallpaper.customization.ui.binder.ThemePickerCustomizationOptionsBinder
  public final void bind(CustomizationOptionsData, View, List, List, Map, …)

com.android.wallpaper.picker.customization.ui.CustomizationPickerFragment
  initCustomizationOptionEntries(CustomizationOptionsData, View, Screen)
```

`bind` is the only method of that name on the class, and its `View` is the root
that holds `id/home_customization_option_container` — a vertical `LinearLayout`
with `showDividers="middle"`. The entries are inflated and added by
`initCustomizationOptionEntries` before `bind` runs, so a row appended from a
hook after `bind` lands under a list the picker has finished building.

`ThemePickerCustomizationOptionViewUtil.getOptionEntries` builds the Home screen
list in order and ends with `GRID`, whose entry is
`layout/customization_option_entry_grid` and whose title is `string/grid_layout`
— **Layout**. Appending is therefore what puts the row directly under it.

The card is not drawn by the container. Each entry is given its own background by
index:

| Position | Background |
|---|---|
| only child | `drawable/customization_option_entry_singleton_background` |
| first | `drawable/customization_option_entry_top_background` |
| last | `drawable/customization_option_entry_bottom_background` |
| otherwise | `drawable/customization_option_entry_background` |

so appending a row also means handing the last background from the row that used
to be last to the new one, or the list stays rounded above it and square below.

The entry layout's root `ConstraintLayout` carries no id — only its children do
(`option_entry_title`, `option_entry_description`, `option_entry_icon_container`)
— so inflating it a second time adds no duplicate id for the picker's own lookups
to trip over. The icon slot is sized to `dimen/customization_option_entry_icon_size`
and rounded by `drawable/customization_option_entry_icon_background`; a switch is
neither, so the module clears both before putting one there.

### Where that setting lives, and why it is not a provider

`com.google.android.apps.wallpaper` targets API 37 and does not hold
`QUERY_ALL_PACKAGES`, so package visibility hides this module's own content
provider from it: a provider it cannot resolve is a setting it cannot store. It
does hold `WRITE_SECURE_SETTINGS`, and the settings provider is visible to
everything, so the choice is one secure setting instead:

```
Settings.Secure "pixel_launcher_evolved_home_blur_wallpaper"   // 0 or 1
```

Wallpaper & Style writes it, the launcher reads it, and the launcher watches
`Settings.Secure.getUriFor` for it so the switch reaches a running launcher
without a restart. `com.google.android.apps.nexuslauncher` does hold
`QUERY_ALL_PACKAGES`, so the launcher end was never the problem; the wallpaper
end was.

## Gotcha: the row is sized by counting children, not visible ones

```
com.android.launcher3.taskbar.TaskbarView extends android.widget.FrameLayout
  public final int getIconLayoutWidth()
  public final int getTotalNumberOfIcons()
  protected int mItemMarginLeftRight
  protected int mIconTouchSize
  public void onLayout(boolean, int, int, int, int)
```

`onLayout` places the icons right to left from

```
right = (left + right + getIconLayoutWidth()) / 2
```

and skips a `GONE` child entirely, so hiding one frees space without moving
anything: the width is unchanged, the right edge is unchanged, and every icon
that is left stays where it was. That is the app drawer button's gap.

`getIconLayoutWidth` is `icons * (2 * mItemMarginLeftRight + mIconTouchSize)`,
less `4 * mItemMarginLeftRight` for a row of more than one, less
`mAllAppsButtonTranslationOffset`. The count is `getTotalNumberOfIcons`, which
counts children. It already discounts the invisible entries of the pinned
container — the launcher's own precedent for this correction — but the app
drawer button and the divider are direct children of the taskbar, so nothing
discounts them.

Measured on a Pixel 8 Pro, with two recents in the row: margin 29px, touch size
171px, so a slot is 229px. The width comes back as 800 either way.

| | icons at | centre |
|---|---|---|
| nothing hidden | 243, 472, 643, 872 | 643 |
| pair hidden, uncorrected | 643, 872 | 843 |
| pair hidden, minus two slots | 414, 643 | 614 |
| pair hidden, minus two slots plus a margin | 443, 672 | **643** |

The row is 1344 wide, so the launcher's own row sits one margin left of the
middle and the corrected row lands on the same place. Two slots less one margin
is the right amount because the launcher lays the divider flush against the icon
after it — see the 643 shared by the divider's right edge and the next icon's
left edge in the uncorrected row — so the pair costs one margin less than two
whole slots.

`getTotalNumberOfIcons` is the tempting hook and the wrong one: `getIconViews`
sizes its array from it, so a smaller count there drops icons from the array
rather than from the width.

## Gotcha: a module update pulls the APK out from under a running process

Wallpaper & Style reads this module's strings through the `ApplicationInfo` the
framework handed over when the process started:

```
android.content.pm.PackageManager$NameNotFoundException:
  Unable to open /data/app/~~_aBzuMpbj5TEKVBqVJFGgA==/…-_HvbwlLGitdyyVBslHqtKA==/base.apk
```

That path stops existing the moment the module is updated, and Wallpaper & Style
outlives an update easily. Reading the strings on every bind meant the row
simply stopped appearing after an update until the app was killed. They are read
once now, the first time the screen is drawn, and kept.

The launcher side was never exposed to this: it reads its `Resources` once at
install and holds it, and an open `Resources` keeps the replaced file alive.


### Gotcha: the two halves of hiding the pair want opposite timing

`applyState` is called when the launcher decides where it is heading, not when
it gets there: the hotseat's morph into the taskbar runs for the whole
transition after it. That makes the timing of the two changes different.

**Removing the pair is free.** The launcher lays the row out right to left from
`(left + right + getIconLayoutWidth()) / 2`, and the app drawer button and the
divider are the leftmost children, so they are placed last. Taking them out
moves none of the icons that are left — only where the row ends on the left —
and at that moment the taskbar is not on screen yet. So it happens at once, and
the button is never seen in Overview.

**Re-centring is not.** It changes `getIconLayoutWidth`, which moves every icon,
and done at the same moment it moves them while the morph is already animating
them somewhere else. Sampled at `animator_duration_scale` 10, the icons held
hotseat spacing for three frames and then appeared grouped inside a pill.
Deferring both halves to `onAnimationEnd` fixed the morph but left the button on
screen for the length of it — measured at 0.8 s in a phone recording — and then
cut the row to its new width in a single frame.

So the two are split: the pair goes at once, and the width closes up over its
own 200 ms animation once the launcher has arrived, driven by a `ValueAnimator`
that asks for a layout pass per frame. Nothing is animated during the morph, and
nothing is left visible that should not be.

The way out changes nothing while it runs. The pair and the width are both put
back from the leaving transition's `onAnimationEnd`, by which time the taskbar
has finished morphing into the hotseat and is not the row on screen.

Both deferred changes are numbered, so a transition overtaken by another cannot
apply its change to the newer one; the last transition always decides.

## The home screen search bar (Android 17, verified September 8, 2026)

```
com.android.launcher3.qsb.OseWidgetView extends
    com.android.launcher3.widget.LauncherAppWidgetHostView

com.android.launcher3.widget.LauncherAppWidgetHostView extends AppWidgetHostView
  public boolean onInterceptTouchEvent(MotionEvent)
  public boolean onTouchEvent(MotionEvent)
  private final CheckLongPressHelper mLongPressHelper

com.android.launcher3.CheckLongPressHelper
  public boolean mHasPerformedLongPress
```

The bar is an app widget, so its taps are the Google app's own `RemoteViews`
`PendingIntent`s. There is no listener on the launcher side to replace, and
replacing one inside the widget would be undone the next time its `RemoteViews`
are applied. The touch is claimed a step earlier instead, at the host view that
sees it before the widget's children do.

`onInterceptTouchEvent` normally returns `mHasPerformedLongPress`, so the
children get every touch until a long press fires:

```java
public boolean onInterceptTouchEvent(MotionEvent ev) {
    if (ev.getAction() == ACTION_DOWN) { …setTouchCompleteListener(this); }
    mLongPressHelper.onTouchEvent(ev);
    return mLongPressHelper.mHasPerformedLongPress;
}
```

Returning true for the bar's own area as well hands the whole gesture to the
host, whose `onTouchEvent` already feeds the same long press helper — so
picking the widget up and moving it keeps working, and the tap is free to mean
something else.

### Which taps belong to the bar and which do not

Read off the widget on a Pixel 8 Pro, the clickable descendants are:

| Bounds | What it is |
|---|---|
| `[105,2417][1238,2585]` | the search field, the full width of the bar |
| `[105,2417][264,2585]` | the Google logo |
| `[788,2417][932,2585]` | first of the right-hand buttons |
| `[932,2417][1076,2585]` | second |
| `[1076,2417][1238,2585]` | Lens |

So a tap is the bar's own only when no clickable descendant narrower than the
bar contains it. Measured: taps at x=180, x=860, x=1000 and x=1150 still reach
`InternalGoogleAppActivityEntrypoint` and `LensActivity`, and only x=500 opens
the drawer.

`mHasPerformedLongPress` has to be consulted on the way up as well. Without it a
long press fires, the launcher offers **Widget settings**, and the finger
lifting still counts as a tap — measured, the drawer opened over the popup.

### Gotcha: the long press is cleared before you can read it

Reading that flag after the launcher's own `onTouchEvent` always answers false:

```java
public void cancelLongPress() {
    mHasPerformedLongPress = false;
    clearCallbacks();
}
```

and `CheckLongPressHelper.onTouchEvent` calls it on both `ACTION_UP` and
`ACTION_CANCEL`. So the flag is read *before* the hooked method proceeds, along
with the action and the pointer position, and the decision is made from that
snapshot afterwards. Reading it after proceed left long press opening the drawer
exactly as a tap did, which is how the bug was found.

### Opening the drawer's search

```
com.android.launcher3.statemanager.StatefulContainer
  StateManager getStateManager()
com.android.launcher3.statemanager.StateManager
  public final void goToState(BaseState, boolean, Animator.AnimatorListener)
com.android.launcher3.LauncherState
  public static final AllAppsState ALL_APPS
com.android.launcher3.views.ActivityContext
  ActivityAllAppsContainerView getAppsView()
com.android.launcher3.allapps.ActivityAllAppsContainerView
  public SearchUiManager mSearchUiManager
com.android.launcher3.allapps.SearchUiManager
  ExtendedEditText getEditText()
com.android.launcher3.ExtendedEditText
  public final boolean requestFocusExplicitly()
  public final void showKeyboard()
```

Every step is the launcher's own, including the focus and keyboard calls the
drawer uses when a search begins any other way. The focus waits for the
`AnimatorListener` the state change takes, because the search box is not on
screen until the transition ends; asking for it earlier leaves the drawer open
with no keyboard.

The widget's context is wrapped, so the launcher behind it is found by
unwrapping `ContextWrapper` until something implements `StatefulContainer`
rather than by casting what the view was handed.
