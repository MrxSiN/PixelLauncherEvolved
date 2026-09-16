# Hook notes

Everything about the launcher below was read off a Pixel 8 Pro running Android 17
(`CP2A.260805.005`, SDK 37) by pulling
`/system_ext/priv-app/NexusLauncherRelease/NexusLauncherRelease.apk` and dumping it
with `dexdump` and `aapt2`. Re-verify these signatures before targeting a newer
launcher build.

## What Android 17 QPR1 moved

The same read was done again on `CP3A.260905.009` (SDK 37, launcher
`versionCode=907`). Everything else below still holds; these eleven had
moved, and each is noted again where it appears.

| Was, on `CP2A.260805.005` | Is, on `CP3A.260905.009` |
| --- | --- |
| `OverviewActionsView.updateActionButtonsVisibility()` | inlined into `updateForGroupedTask(boolean)`, which still logs the old name |
| `OverviewActionsView.updateSplitButtonHiddenFlags(int, boolean)` | gone, with `id/action_split`: no build puts a Split button in the row |
| `ActivityAllAppsContainerView.setSearchResults(ArrayList)` | `setSearchResults(ArrayList, boolean scrollToTop)` |
| `com.android.launcher3.qsb.OseWidgetView` | gone: the bar is a plain `LauncherAppWidgetHostView`, recognised through `OseWidgetController.applyTo(LauncherAppWidgetHostView, boolean)` |
| `DeviceProfile.mDeviceProperties` | `DeviceProfile.deviceProperties` |
| `DeviceProfile.mHotseatProfile` | `DeviceProfile.hotseatProfile` |
| `RecentsPagedOrientationHandler.getSplitPositionOptions(DeviceProfile)` | `getSplitPositionOption(DeviceProfile)`, one option rather than a list |
| `com.android.launcher3.DeviceProfile$Builder` | `com.android.launcher3.deviceprofile.DeviceProfileBuilder`, still with `build()` |
| `TaskbarConfiguration(boolean)` | no constructor at all: the shrinker inlined it into `DeviceProperties$Factory.createDeviceProperties`, which writes `isTaskbarPresent` directly |
| `LauncherTaskbarUIController.onLauncherVisibilityChanged(boolean)` | `(boolean isVisible, boolean visibleBehindDesktop, boolean skipAnimation)`, beside a no-argument overload and one returning an `Animator` |
| `Snackbar.getDismissTimeout(ActivityContext)` | gone, replaced by a `Snackbar$SnackbarDismissTimer`. one entry in the phone-surface list, which reports and skips a surface it cannot find |

`OverviewActionsView.HIDDEN_LARGE_SCREEN` is still `32`,
`DeviceProperties.isLargeScreen` still carries that name, and
`id/action_buttons`, `id/action_screenshot`, `id/action_select`,
`string/recents_clear_all`, `drawable/ic_remove_task_option` and
`dimen/overview_actions_button_spacing` are all still there.

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
  public void updateForGroupedTask(boolean isGroupedTask)   // CP3A.260905.009
  private void updateActionButtonsVisibility()              // CP2A.260805.005
```

The row is inflated from `res/layout/overview_actions_container.xml` as
`com.google.android.apps.nexuslauncher.overview.NexusOverviewActionsView`. Its
`action_buttons` LinearLayout holds `action_screenshot` and `action_select`;
`CP2A.260805.005` also had `action_split` there, and `CP3A.260905.009` does not.

The launcher recomputes the row whenever the selected task changes, so hiding a
button once at inflation is not enough; the recompute is hooked alongside
`onFinishInflate`. On `CP2A.260805.005` that recompute was the private
`updateActionButtonsVisibility`. `CP3A.260905.009` inlined it into its only
caller, `updateForGroupedTask(boolean)`, which still logs
`updateActionButtonsVisibility() called: showActions = [...]` and is the same
moment. Both names live in one place, `OverviewActionsRow`.

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

## Leaving apps out of the app drawer (Android 17, verified September 9, 2026)

The launcher already filters the drawer, one predicate per tab:

```
com.android.launcher3.allapps.ActivityAllAppsContainerView$AdapterHolder
  public void setup(android.view.View, java.util.function.Predicate)
    -> AlphabeticalAppsList.mItemFilter = predicate
    -> AlphabeticalAppsList.onAppsUpdated()

com.android.launcher3.allapps.AlphabeticalAppsList
  public List mApps
  public AllAppsStore mAllAppsStore
  public Predicate mItemFilter
  public void onAppsUpdated()
```

`onAppsUpdated` streams `AllAppsStore.mApps` through `mItemFilter`, so wrapping
the predicate handed to `setup` hides an app without replacing what the
launcher was doing with it — the personal and work tabs each pass their own
predicate, and the wrapper ANDs with it. The predicate may be null for a drawer
with nothing to separate, so the wrapper has to stand alone in that case.

The wrapper asks the store on every call rather than closing over an answer,
which means nothing has to be rebuilt for a change to be *correct* — only for
it to be *visible*.

### Gotcha: the filter only runs when the search box is empty

```
if (mSearchResults.isEmpty() && mItemFilter != null) stream = stream.filter(mItemFilter);
```

So an app hidden from the grid is still a search result. The module drops it in
the same `setSearchResults` filter as the hidden groups, matched on an app
result type (`1`) and the package. The three apps in the top row of the results
are three separate adapter items, not one, so dropping one leaves the others.

### Picking the apps in the drawer itself

A list of names is a poor way to choose apps, so the choosing happens where the
icons are. Home settings starts it and the launcher finishes it; both are the
same process, so a plain object carries the state between them.

```
com.android.launcher3.BubbleTextView            // extends TextView
  public void onDraw(android.graphics.Canvas)
  public void getIconBounds(android.graphics.Rect)

android.view.View
  public boolean performClick()

com.android.launcher3.uioverrides.QuickstepLauncher
  public void onStateSetEnd(com.android.launcher3.statemanager.BaseState)

com.android.launcher3.Launcher
  public BaseDragLayer getDragLayer()

com.android.launcher3.statemanager.StateManager
  public void goToState(com.android.launcher3.statemanager.BaseState)
```

- **The tick is drawn by the icon**, in `onDraw`, against `getIconBounds`. The
  drawer recycles its views, so anything hung on a particular view — a badge
  child, a foreground drawable — is handed to a different app on the next
  scroll. Drawing from the view's own item is the only thing that cannot drift.
- **The tap is taken at `performClick`**, which is where a view decides to act
  on being clicked. Returning true there means the launcher's own listener is
  never reached, so it never has to be found or replaced. It is a framework
  method, so it does not move; the guard is that only a `BubbleTextView` whose
  tag is an `AppInfo` answers, which is also what keeps the home screen behind
  the drawer out of it — a workspace icon carries a `WorkspaceItemInfo`.
- **Leaving is a state change.** `onStateSetEnd` says what the launcher settled
  into; anything that is not `ALL_APPS`, after `ALL_APPS` has been reached at
  least once, is a person changing their mind. The first settle has to be seen
  or the one that opens the drawer reads as the one that closed it.

While the choosing lasts the hide filter passes everything, or an app already
hidden could never be recovered. Both entering and leaving therefore rebuild the
list.

### Gotcha: the drag layer rebuilds layout parameters

`BaseDragLayer` accepts only its own `LayoutParams`, and builds them from
another kind by copying width and height — `FrameLayout.LayoutParams(ViewGroup.LayoutParams)`
does not carry gravity across. A confirm button added with
`gravity = BOTTOM|END` therefore lands in the top-left corner. Setting gravity
and margins on the parameters the view *ends up with*, after `addView`, is what
places it.

### Making a change appear

The launcher rebuilds the drawer when the installed apps change and not
otherwise, so a change made in Home settings would sit unseen. `AllAppsStore`
carries the launcher's own way to ask:

```
com.android.launcher3.allapps.AllAppsStore
  public void notifyUpdate()
  public void addUpdateListener(AllAppsStore$OnUpdateListener)
```

The store is reached from the `AlphabeticalAppsList` captured in the `setup`
hook, and `notifyUpdate()` is called from `Launcher.onResume` when the stored
set differs from the one the drawer was last built for — which is the moment a
person returns from having changed it. `getAppsStore()` exists too, but only on
a Dagger component interface, so the field is the shorter route.

Apps are stored by package name. `ItemInfo.getTargetPackage()` is what the
predicate reads, and a package present in both a personal and a work profile is
the same app to the person hiding it.

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
  // CP3A.260905.009
  public void setSearchResults(
      java.util.ArrayList<BaseAllAppsAdapter.AdapterItem>, boolean scrollToTop)
  // CP2A.260805.005
  public void setSearchResults(java.util.ArrayList<BaseAllAppsAdapter.AdapterItem>)
```

The second argument says whether the list is scrolled back to the top, which is
the launcher's call: the filter hands it back untouched.

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
Setting that field to `true` gives a taskbar while `isLargeScreen`, and
therefore the grid, the app drawer and Recents, stays on phone measurements.

On `CP3A.260905.009` the configuration is reached through the properties the
factory has just answered:

```
com.android.launcher3.deviceprofile.DeviceProperties
  public TaskbarConfiguration taskbarConfiguration
com.android.launcher3.deviceprofile.DeviceProperties$Factory
  public static DeviceProperties createDeviceProperties(
      boolean, WindowBounds, DeviceConfiguration, boolean)
```

`DeviceProfileBuilder.build()` calls the factory and only then reads
`taskbarConfiguration` back out of it to derive the taskbar profile, so a hook
on the factory's return lands between the two.

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

On `CP2A.260805.005`, `TaskbarConfiguration.<init>` is one field assignment
reached by `invoke-direct`, which ART is free to inline; its caller,
`DeviceProfile$Builder.build()`, was deoptimized before the hook was placed.

`CP3A.260905.009` takes that further: the class has no `<init>` in the dex at
all. The shrinker inlined it into `DeviceProperties$Factory`, which does
`new-instance`, `Object.<init>` and the field write itself. There is nothing
left to hook, which is why the field is now written on the factory's answer and
the deoptimization is gone with the hook that needed it.

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

## Tablet Overview only (Android 17, verified September 12, 2026)

Recents is a grid on a large screen and a single row of cards on a phone, and
every part of the launcher decides which from one field:

```
com.android.launcher3.deviceprofile.DeviceProperties
  public final boolean isLargeScreen
```

reached by direct field access, never through a getter. The state answers from
it:

```java
// com.android.launcher3.uioverrides.states.OverviewState
public boolean displayOverviewTasksAsGrid(DeviceProfile dp) {
    return dp.deviceProperties.isLargeScreen;
}
// com.android.quickstep.views.RecentsView
public boolean showAsGrid() {
    return mOverviewGridEnabled
        || (mCurrentGestureEndTarget != null
            && stateFromGestureEndTarget(mCurrentGestureEndTarget)
                   .displayOverviewTasksAsGrid(getDeviceProfile()));
}
```

and so does everything that lays a card out — `BaseContainerInterface`'s
`calculateTaskSize`, `calculateGridSize`, `calculateModalTaskSize` and
`getTaskDimension`, `TaskView.isGridTask`, `updateTaskSize` and `updatePivots`,
a dozen methods of `RecentsView`, `TaskViewSimulator`, and — on
`CP2A.260805.005` only — `OverviewActionsView.updateForIsTablet`.

### Why the field can be rewritten after the profile is built

The same field is what the workspace, the app drawer, the hotseat and the
taskbar are measured from — but they are measured **once**, inside
`DeviceProfile$Builder.build()`, which reads
`LauncherDisplayInfo.isLargeScreen(WindowBounds)` for itself and derives
`WorkspaceProfile`, `AllAppsProfile`, `HotseatProfile` and `TaskbarProfile`
before it returns. Recents reads the stored field again on every layout.

So rewriting `DeviceProfile.deviceProperties.isLargeScreen` to true **after**
`build()` has returned reaches Recents and nothing that was already sized. That
is what separates this from tablet mode, which changes the `isLargeScreen` call
itself and therefore changes the grid with it, and from taskbar only, which
rewrites `TaskbarConfiguration` instead.

The rewrite applies to every profile the process builds, for the reason given
under *one process, one profile* above.

### Gotcha: the grid dimensions are zero on a phone

Grid Overview is not only a different arrangement; it has its own dimensions,
and they live in `OverviewProfile`:

```
com.android.launcher3.deviceprofile.OverviewProfile
  public OverviewProfile(int taskMarginPx, int taskIconSizePx,
      int taskIconDrawableSizePx, int taskIconDrawableSizeGridPx,
      int actionsHeight, int actionsTopMarginPx, int pageSpacing,
      int rowSpacing, int gridSideMargin)
```

constructed only in `DeviceProfile$Builder.build()`, from nine dimension
resources read through a `createConfigurationContext` whose
`smallestScreenWidthDp` is computed from the window bounds. Five of the nine are
qualified, and the three the grid is made of resolve to **nothing** below
`sw600dp`:

| Resource | Field | phone | `sw600dp` | `sw720dp` |
|---|---|---|---|---|
| `overview_grid_side_margin` | `gridSideMargin` | **0dp** | 64dp | — |
| `overview_grid_row_spacing` | `rowSpacing` | **0dp** | 28dp | 36dp |
| `task_thumbnail_icon_drawable_size_grid` | `taskIconDrawableSizeGridPx` | **0dp** | 44dp | — |
| `overview_page_spacing` | `pageSpacing` | 16dp | 36dp | 44dp |
| `overview_task_margin` | `taskMarginPx` | 16dp | 12dp | 16dp |

A grid laid out on the phone's numbers therefore has no space between its rows,
no margin to sit inside and no icon on a card, and the previews are drawn at the
size a single row of full-height cards is measured for. Reading the same nine
against a `smallestScreenWidthDp` of **600** — `#4416` in
`LauncherDisplayInfo.isLargeScreen`, which is 600.0f — and writing them back
over the built `OverviewProfile` is what sizes the previews for the grid.

Only that one value is overridden; density and orientation stay as the launcher
is running, so a dimension with a `land` answer — `overview_actions_top_margin`
— still resolves to it.

### The launcher's own surfaces read the same field

Outside `com.android.quickstep` and Overview's own states, these read
`isLargeScreen` while the launcher runs, and none of them is Recents:

```
com.android.launcher3.Launcher.initDeviceProfile(InvariantDeviceProfile)
com.android.launcher3.DropTargetBar.setInsets(Rect)
com.android.launcher3.Workspace.isSignificantMove(float, int)
com.android.launcher3.QuickstepTransitionManager.getLauncherContentAnimator(int, boolean, boolean)
com.android.launcher3.allapps.ActivityAllAppsContainerView.setInsets(Rect)
com.android.launcher3.model.data.AppPairInfo.isLaunchable(Context)
com.android.launcher3.states.RotationHelper.onDeviceProfileChanged(DeviceProfile)
com.android.launcher3.touch.AbstractStateChangeTouchController.onDragEnd(float)
com.android.launcher3.uioverrides.QuickstepLauncher.getSupportedShortcuts(ItemInfo)
com.android.launcher3.views.AbstractSlideInView.onDragEnd(float)
com.android.launcher3.views.Snackbar.getDismissTimeout(ActivityContext)
com.android.launcher3.widget.LauncherAppWidgetProviderInfo.initSpans(Context, InvariantDeviceProfile)
```

Two of them are why the field cannot simply be left true:
`ActivityAllAppsContainerView.setInsets` zeroes the drawer's left and right
margins on a large screen, and `RotationHelper.onDeviceProfileChanged` sets
`mIgnoreAutoRotateSettings` from it, which would let the home screen rotate
freely. Each is given the phone's answer back for the length of its own call.

### The Overview action row is four separate decisions

A tablet has no Screenshot, Select or Clear all under Overview — it offers those
from the task menu — and the launcher acts on that in four places, none of which
is the state or the alphas:

```
com.android.quickstep.views.RecentsView.setInsets(Rect)
    mActionsView.updateHiddenFlags(HIDDEN_LARGE_SCREEN /* 32 */, isLargeScreen);
com.android.quickstep.views.OverviewActionsView.updateForIsTablet()   // CP2A only
    updateSplitButtonHiddenFlags(FLAG_IS_NOT_TABLET /* 1 */, !isLargeScreen);
com.android.quickstep.views.OverviewActionsView.getBottomMargin()
    isLargeScreen ? taskbarHeight + actionsTopMarginPx
                  : heightPx - mTaskSize.bottom - actionsTopMarginPx - actionsHeight
com.android.quickstep.BaseContainerInterface.calculateGridSize(DeviceProfile, Rect)
    claimedBelow += isLargeScreen ? 0 : actionsTopMarginPx + actionsHeight
```

so keeping the row means answering all four, and each was visible on its own.
On `CP3A.260905.009` the second is no longer one of them: `updateForIsTablet`
and `updateSplitButtonHiddenFlags` are both gone, along with `id/action_split`,
so no build puts a Split button in the row and there is nothing to keep out.

| Left alone | What it looked like |
|---|---|
| `updateHiddenFlags` | the row in the hierarchy at `0,2704 1344x288` with no children |
| `updateSplitButtonHiddenFlags` | a fourth button in the row: it gave up its side margins for the full `0..1344`, and Clear all came out 232px wide against 350 |
| `getBottomMargin` | the row **266px** below the stock one, against the gesture bar, because with no taskbar its large-screen position measures from zero |
| `calculateGridSize` | the cards laid out over the row — this is the rectangle everything is measured inside, and `calculateLargeTileSize` insets it, a grid card being half of that |

Neither the state nor the alphas were ever the cause:
`OverviewState.getVisibleElements` reads `isPhone`, not `isLargeScreen`, and still
carried the actions bit; `animateActionsViewIn` does return early on
`showAsGrid()`, but only for the swipe-up path, and the row was missing coming
from the Recents button too; scrolling the grid changed nothing, so
`getIndexScrollAlpha` was not it either.

Measured against stock Recents afterwards, horizontally exact:

| | `action_buttons` | Screenshot | Select | Clear all |
|---|---|---|---|---|
| stock | `x=98..1245` | `98..498` | `546..847` | `895..1245` |
| Overview only | `x=98..1245` | `98..498` | `546..847` | `895..1245` |

Vertically the row sits directly under the cards in both — a 71px gap under the
last card row — but 36px lower in absolute terms, `y=2546..2690` against
`2510..2654`, because the grid's own rectangle comes out 37px below the phone's
single card. Both are 2094px tall; the grid's is centred 37px further down.

### Gotcha: the screen classification cannot be stepped back inside Overview

The first attempt gave `RecentsView.setInsets` the phone answer for the length of
the call, the way the home surfaces get it, and it worked: it clears the flag,
and because `setInsets` also recomputes Overview's own task rectangle, a phone's
reserved the row's space and the placement came out matching stock exactly.

It also made the app-to-Overview transition jitter, and had to. `setInsets` is
called while the app window is still animating, and the cards are laid out from
the same field on the gesture thread, so every window in which that field reads
phone is a frame of the transition measured as a phone's. The home surfaces are
safe from this only because none of them is measuring while Overview is, which
is not an assumption: logging every entry and exit across a swipe from an app
into Overview counted **zero**.

So inside Overview each decision is answered on its own, deterministically —
a flag forced, a margin computed, a rectangle's bottom edge rewritten — and the
shared field is never moved.

### The app chip is the same size on a card a quarter the width

```
com.android.quickstep.views.IconAppChipView          // R.id.icon on a task card
  public void setMaxWidth(int)
  private void updateChipSize()
  private int maxWidth                 // starts at Integer.MAX_VALUE
  private int collapsedMenuDefaultWidth
  private int minWidthAllowed
  private int appIconSize
com.android.quickstep.views.TaskView
  public void updateTaskSize(Rect, Rect)
  public boolean isGridTask()
  public float getNonGridScale()       // full card width / this card's width
```

The chip — app icon, app name, chevron — is laid out from unqualified
dimensions, so it is the same size on a grid card as on a full-height one. A
tablet gets away with that because its grid cards are wide. Measured here, a grid
card is **451x1005** and the chip **439x156**: 97% of the card's width, against
45% of a full-height card's.

The collapsed width is

```java
getCollapsedBackgroundWidth()
    = min(maxWidth, collapsedMenuDefaultWidth) + backgroundMarginTopStart
```

so `maxWidth` decides nothing until it is lowered below the default — which is
what `setMaxWidth` is for, and what the launcher itself calls for the two halves
of a split card. Dividing `collapsedMenuDefaultWidth` by `getNonGridScale()`
gives the chip the same share of a grid card that it has of a full one, and
brought it to **210x156**, 47% of the card.

`calculateCollapsedTextWidth` gives the app name whatever is left above
`minWidthAllowed`, and at first attempt that was **14px** — one clipped
character, which reads as damage rather than as a name. A title that would come
out narrower than the app icon beside it is therefore dropped, by asking for
`minWidthAllowed` itself, and the chip becomes an icon and a chevron. A wider
grid card keeps its name.

### Gotcha: the thumbnail was never the thing that was wrong

Worth recording because it looked like it was. With the grid on, the previews
measured **451x1005** inside cards of **451x1005** — exactly the screen's 0.449
aspect, filling the card. `calculateGridTaskSize` derives them from
`getTaskDimension`, which is the screen, so they cannot come out any other shape.
What made the cards look wrong was the chip drawn on top of them.

### What the app-to-Overview gesture actually runs through

Worth writing down, because two plausible causes of jitter turned out not to be
causes at all.

**It is not dropped frames.** `dumpsys gfxinfo` across the gesture: 156 frames,
**0 janky**, 50th and 95th percentile 5ms. Anything visible in it is a geometry
step, not a missed deadline.

**The simulator already knows about the grid.** The gesture animates a
`SurfaceControl` through `TaskViewSimulator`, and it does not aim at the
full-height card and then hand over to a grid one:

```java
// TaskViewSimulator.setDp
mIsGridTask = dp.deviceProperties.isLargeScreen && !mIsDesktopTask;
// TaskViewSimulator.calculateTaskSize
if (mIsGridTask) {
    mSizeStrategy.calculateGridTaskSize(mContext, mDp, mFullTaskSize, handler);
    mSizeStrategy.calculateTaskSize(mContext, mDp, mCarouselTaskSize, handler);
} else {
    mSizeStrategy.calculateTaskSize(mContext, mDp, mFullTaskSize, handler);
    mCarouselTaskSize.set(mFullTaskSize);
}
```

so it lands on the grid rectangle, and it reaches it through
`calculateGridTaskSize` → `calculateLargeTileSize` → `calculateGridSize` — the
same call this tweak rewrites for the action row. That rewrite is therefore on
the gesture thread, and every field it reads is resolved once at install rather
than looked up per call.

### The 84px snap: Recents disagrees with itself about being a grid

The task cards jumped sideways on the frame the swipe-up-from-an-app gesture
finished. Measured off a recording, decoded frame by frame and cross-correlated
for a horizontal shift, the tail read

```
f172 -7   f173 -2   f174 0   f175 +84   f176 0   f177 0
```

— deceleration to a dead stop, two still frames, then **84px in one frame**. Band
by band on that frame: both card rows +84, the action row 0, the status bar 0,
nothing vertical. A pager scroll correction and nothing else.

Hooking `PagedView.scrollTo(int, int)` and logging a stack whenever the scroll
moved 40px or more named it outright:

```
SCROLLPROBE d=84 from=5472 to=5556 ::
  PagedView.updateCurrentPageScroll | PagedView.setCurrentPage
  | RecentsView.updateOrientationHandler | RecentsView.onGestureAnimationEnd
  | AbsSwipeUpHandler.setupLauncherUiAfterSwipeUpToRecentsAnimation
```

paired with a `d=-84 from=5556 to=5472` out of `PagedView.onLayout`. Two page
scroll bases, 84px apart, and the gesture's end jumps from one to the other.

They come from the two halves of

```java
public boolean showAsGrid() {
    return mOverviewGridEnabled
        || (mCurrentGestureEndTarget != null
            && stateFromGestureEndTarget(mCurrentGestureEndTarget)
                   .displayOverviewTasksAsGrid(getDeviceProfile()));
}
```

Only the second half is this tweak's. `mOverviewGridEnabled` is a flag the
launcher turns **off** — `LauncherRecentsView.onStateTransitionComplete` clears
it when the state is not a grid, and nothing outside the tablet path sets it — so
a layout with no gesture in flight computes the phone's page scrolls while
`onGestureAnimationEnd` computes the grid's. `PagedView.getPageScrolls` consults
`showAsGrid()`, which is how that reaches the scroll.

Answering it once, `showAsGrid()` forced true, removes the disagreement: probe
silent on three runs, and the settle clean on four recordings afterwards.

### Gotcha: three wrong suspects, and how each was cleared

Worth recording, because each looked convincing:

- **The phone-answer flips.** Logging every `asPhone` entry and exit across the
  gesture counted **zero**. None of the home surfaces runs during it.
- **Dropped frames.** `dumpsys gfxinfo` over the gesture: 156 frames, **0 janky**,
  50th and 95th percentile 5ms.
- **The app chip.** Three versions were built to stop it laying out during the
  transition, and the snap survived all three. Disabling the chip *and* the
  action row and leaving only the core grid still reproduced it, which is what
  finally pointed at `showAsGrid`.

The repro matters too, because the obvious gestures do not produce it: `input
swipe` lands on home, and a slow dwell reaches Overview without ever snapping.
What reproduces it is a **decelerating** fling —

```
input motionevent DOWN 672 2950
  MOVE 672 {2750 2450 2050 1750 1550 1420 1360 1340 1335}
  UP   672 1335
```

— and even then only intermittently, so measure it rather than watch it. Record
with `screenrecord --size 672x1496` to keep the frame rate near 60: a capture
that drops to ~25fps hides a one-frame jump entirely, which cost two rounds of
false negatives.

### Gotcha: touching the app chip while a card is laid out drags the cards sideways

`IconAppChipView.updateChipSize` ends in `setLayoutParams`, and a `requestLayout`
inside a `PagedView` sends it back through its page scrolls. Capping the chip
from `TaskView.updateTaskSize` — which is where the launcher works the ratio out,
and which is inside the pager's own layout — therefore moved the task cards on
the x-axis as the app-to-Overview gesture finished.

Measured off a `screenrecord`, decoded frame by frame and cross-correlated
against the previous frame for a horizontal shift, the tail of that transition
read

```
f408 -13   f409 -8   f410 -3   f411 0   f412 0   f413 +84   f414 0   f415 0 …
```

— a clean deceleration to a dead stop, two still frames, then **84px in one
frame**, and still for good after. Edge tracking agreed: every card edge moved by
the same 84, `326→242`, `419→335`, `455→371`, `666→582`, `791→708`. With the same
gesture and the same build minus the chip and the action row, the tail was
`-23, -16, -12, -8, -2, 0, 0, 0 …` and no jump.

Two smaller versions did not fix it, and it is worth recording why: capping only
when the value changed, and then writing the widths straight onto the layout
parameters without `setLayoutParams` at all. Neither is enough, because any
change to a chip's measurement while its card is in the pager's layout is a
change the pager answers.

`onFinishInflate` is not outside the transition either, which cost another
round: `RecentsView` inflates a task card when it binds a task, and that is part
of Overview opening, so an `updateChipSize` there is a `requestLayout` in the
middle of the gesture like any other. Measured off a recording of the real fling
with the cap moved there, both card rows still moved **+84px in the frame after
the animation came to rest** — and, measured band by band, the action row and the
status bar did not move at all and nothing moved vertically. A pager scroll
correction, and nothing else.

**So the chip is capped in `onFinishInflate` and without asking for a layout even
there.** A view that has not been measured yet has no layout to invalidate, so
the two widths are written straight onto the layout parameters the chip and its
title already carry and its own first measure reads them. Writing them that way
to a chip already on screen would not be safe — that is exactly what the pager
answers.

### Where the chip's ratio comes from without a card to measure

`getNonGridScale()` is what the launcher works out in `updateTaskSize`, and at
inflation there is no card size to work it out from. The device profile has it,
though: a grid card is half of the large tile, less the row spacing, and both
keep the screen's aspect, so

```
gridHeight = (largeTileHeight - rowSpacing) / 2
ratio      = largeTileWidth / gridWidth
           = 2 * largeTileHeight / (largeTileHeight - rowSpacing)
```

which needs only the large tile, and the launcher is asked for that directly:

```
com.android.quickstep.views.RecentsViewContainer
  public static Context containerFromContext(Context)      // -> the launcher
com.android.launcher3.views.ActivityContext
  DeviceProfile getDeviceProfile()
com.android.quickstep.LauncherActivityInterface
  public static final DaggerSingletonObject INSTANCE       // .get(Context)
com.android.quickstep.BaseContainerInterface
  private void calculateLargeTileSize(Context, DeviceProfile, Rect)
```

It is asked once and the answer kept: it is a property of the profile, not of any
card. The cap it produces is **210x156** on a **451x1005** card, the same number
the per-card ratio gave, which is the check that the arithmetic matches the
launcher's own.

A build that no longer offers any of that leaves the chip the size it was drawn.
A wide chip is worth less than a snapping transition.

### Gotcha: the thumbnail was never the thing that was wrong

Worth recording because it looked like it was. With the grid on, the previews
measured **451x1005** inside cards of **451x1005** — exactly the screen's 0.449
aspect, filling the card. `calculateGridTaskSize` derives them from
`getTaskDimension`, which is the screen, so they cannot come out any other shape.
What made the cards look wrong was the chip drawn on top of them.

### What the app-to-Overview gesture actually runs through

Worth writing down, because two plausible causes of jitter turned out not to be
causes at all.

**It is not dropped frames.** `dumpsys gfxinfo` across the gesture: 156 frames,
**0 janky**, 50th and 95th percentile 5ms. Anything visible in it is a geometry
step, not a missed deadline.

**The simulator already knows about the grid.** The gesture animates a
`SurfaceControl` through `TaskViewSimulator`, and it does not aim at the
full-height card and then hand over to a grid one:

```java
// TaskViewSimulator.setDp
mIsGridTask = dp.deviceProperties.isLargeScreen && !mIsDesktopTask;
// TaskViewSimulator.calculateTaskSize
if (mIsGridTask) {
    mSizeStrategy.calculateGridTaskSize(mContext, mDp, mFullTaskSize, handler);
    mSizeStrategy.calculateTaskSize(mContext, mDp, mCarouselTaskSize, handler);
} else {
    mSizeStrategy.calculateTaskSize(mContext, mDp, mFullTaskSize, handler);
    mCarouselTaskSize.set(mFullTaskSize);
}
```

so it lands on the grid rectangle, and it reaches it through
`calculateGridTaskSize` → `calculateLargeTileSize` → `calculateGridSize` — the
same call this tweak rewrites for the action row. That rewrite is therefore on
the gesture thread, and every field it reads is resolved once at install rather
than looked up per call.

### Gotcha: laying the app chip out drags the cards sideways

`IconAppChipView.updateChipSize` ends in `setLayoutParams`, and a `requestLayout`
inside a `PagedView` sends it back through its page scrolls — so capping the chip
while the app-to-Overview gesture is running moves the task cards on the x-axis.

Capping it fewer times was not enough. The first version asked for the chip's
default back whenever `isGridTask()` was false or `getNonGridScale()` had no
answer yet, and a card mid-gesture answers both ways, so the cap flipped between
capped and uncapped and paid for a pager layout each time; instrumented across
one swipe, **58** evaluations and **8** applications. Tightening only, never
restoring, brought that to one per chip — and it still snapped. It is the layout
itself that is not affordable here, not how many of them there are.

So the two widths that method would write are written straight onto the layout
parameters the views already hold, and `setLayoutParams` is never called:

```java
// IconAppChipView.updateChipSize(), collapsed
appTitle.getLayoutParams().width =
    calculateCollapsedTextWidth(getCollapsedBackgroundLtrBounds().width());
appTitle.setLayoutParams(lp);        // <- not called
getLayoutParams().width = getChipWidth();
setLayoutParams(lp);                 // <- not called
```

The pass already running picks the widths up and the pager is never asked to lay
itself out again. Both widths are still the launcher's own, asked of
`getChipWidth`, `getCollapsedBackgroundLtrBounds` and
`calculateCollapsedTextWidth`, so a build that changes how a chip is measured
changes this with it. Only the collapsed widths are written: the expanded chip is
the task menu standing open, which is not something a card is resized underneath,
and the launcher runs its own `updateChipSize` on that transition.

Measured after: chip **210x156** on a **451x1005** card, unchanged.

### Gotcha: the thumbnail was never the thing that was wrong

Worth recording because it looked like it was. With the grid on, the previews
measured **451x1005** inside cards of **451x1005** — exactly the screen's 0.449
aspect, filling the card. `calculateGridTaskSize` derives them from
`getTaskDimension`, which is the screen, so they cannot come out any other shape.
What made the cards look wrong was the chip drawn on top of them.

### What the app-to-Overview gesture actually runs through

Worth writing down, because two plausible causes of jitter turned out not to be
causes at all.

**It is not dropped frames.** `dumpsys gfxinfo` across the gesture: 156 frames,
**0 janky**, 50th and 95th percentile 5ms. Anything visible in it is a geometry
step, not a missed deadline.

**The simulator already knows about the grid.** The gesture animates a
`SurfaceControl` through `TaskViewSimulator`, and it does not aim at the
full-height card and then hand over to a grid one:

```java
// TaskViewSimulator.setDp
mIsGridTask = dp.deviceProperties.isLargeScreen && !mIsDesktopTask;
// TaskViewSimulator.calculateTaskSize
if (mIsGridTask) {
    mSizeStrategy.calculateGridTaskSize(mContext, mDp, mFullTaskSize, handler);
    mSizeStrategy.calculateTaskSize(mContext, mDp, mCarouselTaskSize, handler);
} else {
    mSizeStrategy.calculateTaskSize(mContext, mDp, mFullTaskSize, handler);
    mCarouselTaskSize.set(mFullTaskSize);
}
```

so it lands on the grid rectangle, and it reaches it through
`calculateGridTaskSize` → `calculateLargeTileSize` → `calculateGridSize` — the
same call this tweak rewrites for the action row. That rewrite is therefore on
the gesture thread, and every field it reads is resolved once at install rather
than looked up per call.

### Gotcha: re-capping the app chip drags the cards sideways

`updateChipSize` ends in `setLayoutParams`, and a `requestLayout` inside a
`PagedView` sends it back through its page scrolls — so a chip re-capped on every
pass moves the task cards on the x-axis under the app-to-Overview gesture, which
is exactly what it did.

The cap is applied from `TaskView.updateTaskSize`, which is where the launcher
works the ratio out, and the first version asked for the chip's default back
whenever `isGridTask()` was false or `getNonGridScale()` had not been worked out
yet. A card mid-gesture answers both ways, so the cap flipped between capped and
uncapped and paid for a whole-pager layout each time.

**The cap is therefore only ever tightened.** A card that is not a grid card, or
one without a ratio yet, is left exactly as it is rather than handed its default
back. Instrumented across one swipe from an app into Overview afterwards: **58**
evaluations, **8** applications — one per chip — with the ratio constant at
`2.0864744` throughout, so the answer stops moving after the first pass that
knows it.

Nothing else is asked for either: no `invalidate` on the card, no post.

Sizing it from inside `updateChipSize` instead, so the launcher's own pass does
the work, does not work: the launcher only calls that when the chip opens or
closes, so a chip on a card that has just been resized is never told. Measured,
the chip stayed at its full 439px.

### Gotcha: profiles are built off the UI thread

`DeviceProfile$Builder.build()` was seen running on both the launcher's main
thread and a background thread during one start, so the rewrite and the
put-it-back are guarded rather than assumed to be single-threaded. The surfaces
that are put back are all on the UI thread, and Recents neither lays out nor
runs a gesture while one of them is measuring itself.

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

## Blurring the wallpaper on the home screen (Android 17, re-verified September 9, 2026)

Read off the launcher on the same device, `CP2A.260805.005`.

```
com.android.quickstep.util.BaseDepthControllerImpl
  protected final int mMaxBlurRadius            // 90 on a Pixel 8 Pro
  protected int mCurrentBlur
  private float mDepth
  public final MultiPropertyFactory$MultiProperty stateDepth
  public final MultiPropertyFactory$MultiProperty widgetDepth
  private void setDepth(float)
  private static float mapDepthToBlur(float)
  public void applyDepthAndBlur()

com.android.launcher3.statehandlers.DepthController extends BaseDepthControllerImpl
com.android.launcher3.statehandlers.LauncherDepthController extends DepthController
```

`mCurrentBlur = shouldBlur() ? (int) (mMaxBlurRadius * mapDepthToBlur(mDepth)) : 0`,
put on the wallpaper surface with `SurfaceProperties.setBackgroundBlurRadius`,
and the same depth passed to `WallpaperManager.setWallpaperZoomOut` — which is
why the launcher's own blur comes with the wallpaper pushed back rather than
only softened.

`mapDepthToBlur` is `clampToProgress(LINEAR, depth, 0f, 0.3f)`, so the blur
reaches the full radius at a depth of `0.3`. The module's `0.15` is half of it.

`shouldBlur()` is `mCrossWindowBlursEnabled && !scrimView.isFullyOpaque() &&
!mPauseBlurs`, so raising a depth still respects the platform switching window
blurs off, and still yields to an opaque scrim.

### Where a state's depth comes from

`mDepth` is the aggregate of several channels. The one the state machine drives
is `stateDepth`, and the number it is driven to is the state's own:

```
DepthController.setState(Object state)
  stateDepth.setValue(state.getDepth(mContainer))

DepthController.setStateWithAnimation(BaseState state, StateAnimationConfig, PendingAnimation p)
  p.setFloat(stateDepth, MULTI_PROPERTY_VALUE, state.getDepth(mContainer), interpolator)
```

```
com.android.launcher3.LauncherState
  public final float getDepth(com.android.launcher3.views.ActivityContext)   // 5 code units
  public float getDepthUnchecked(com.android.launcher3.views.ActivityContext) // returns 0f
  public static final LauncherState NORMAL   // of type LauncherState$3
```

`LauncherState$3` — the home state — overrides only `getTransitionDuration`, so
`NORMAL.getDepth` is the base pair above and answers `0f`.

The call is `invoke-interface BaseState.getDepth`, so grepping a dex dump for
`LauncherState;.getDepth:` finds nothing. There are five call sites:
`BaseDepthControllerImpl.setDepth`, `DepthController.setState`,
`DepthController.setStateWithAnimation`,
`TaskViewUtils.createRecentsWindowAnimator` and
`RecentsView.createAdjacentPageAnimForTaskLaunch`. Only the two on
`DepthController` decide the depth home rests at and the depth it animates
towards; `setDepth` asks only to decide whether to round its argument off.

The module hooks `LauncherState.getDepth` and answers for `NORMAL` alone,
recognised by identity. Every other state keeps its own number, and this
module's number is compared with none of them.

### Gotcha: raising the applied depth desynchronises the state machine

The first version of this tweak held a floor under the depth the launcher ends
up applying, in `setDepth`, rather than changing what the state reports. That is
wrong in a way `dumpsys` states plainly:

```
adb shell dumpsys activity com.google.android.apps.nexuslauncher

    DepthController
    	mMaxBlurRadius=90
    	mStateDepth=0.0      <- what the launcher believes home is
    	mCurrentBlur=44      <- what the wallpaper surface got
```

The launcher animates a state change from the depth it believes it is at, so
believing home is `0.0` it starts every transition out of home from zero. The
blur drops out for the first frames of Overview and of the app drawer, and comes
back only once the next state has settled. Gating the floor on "the launcher is
on home", read off `onStateSetStart` and `onStateSetEnd`, does not help: it
narrows what the floor touches without making the two numbers agree, and it adds
a second failure — `onResume` runs before `onStateSetStart(NORMAL)` does, so a
switch flipped in Home settings was applied while the module still believed it
was somewhere else, and the blur waited for the state after that.

Answered at the state, `mStateDepth` reads `0.15` and `mCurrentBlur` reads `45`.
The launcher and the wallpaper agree, transitions start from the blurred depth,
and neither symptom exists to be gated around.

### Gotcha: the ask is five code units behind an interface call

`getDepth` is small enough for ART to inline past a hook. `DepthController.setState`
and `DepthController.setStateWithAnimation` are deoptimized so the call stays
real. They are the two that matter; an inlined copy inside `setDepth` would only
change whether the depth is rounded to 1/256.

For the record, the setter is also reached through a shrunk static bridge, which
is what an earlier version had to deoptimize instead:

```
BaseDepthControllerImpl$1.setValue(BaseDepthControllerImpl, float)   // the DEPTH FloatProperty
  -> BaseDepthControllerImpl.c(BaseDepthControllerImpl, float)       // static, synthetic
     -> BaseDepthControllerImpl.setDepth(float)                      // private
```

### Gotcha: the launcher blurs its own workspace, not only the wallpaper

```
com.android.launcher3.statehandlers.LauncherDepthController extends DepthController
  public final boolean blurWorkspaceDepthTargets()
  public final void onDepthAndBlurApplied()

com.android.launcher3.LauncherState
  public boolean shouldBlurWorkspace(NexusLauncherActivity, LauncherState)
com.android.launcher3.uioverrides.states.AllAppsState
  public final boolean shouldBlurWorkspace(NexusLauncherActivity, LauncherState)
```

`blurWorkspaceDepthTargets` puts a `RenderEffect` on the launcher's own views:

```java
Object target = stateManager.mConfig.targetState;
if (target == null) target = stateManager.mState;
boolean should = stateManager.mCurrentStableState.shouldBlurWorkspace(launcher, target);
RenderEffect effect = should && mCurrentBlur > 0
        ? RenderEffect.createBlurEffect(mCurrentBlur, mCurrentBlur, tileMode)
        : null;
for (View v : launcher.mDepthBlurTargets) v.setRenderEffect(effect);   // Workspace, Hotseat
```

The receiver is the **stable** state and the argument is the one being headed
to. `LauncherState` answers `other == ALL_APPS`; `AllAppsState` answers
`other == ALL_APPS || other == NORMAL`, which is what blurs the workspace on the
way out of the drawer as well as on the way in. A resource boolean
(`0x7f050012`) switches the whole thing off, and is false on this build.

It is called from one place: `onDepthAndBlurApplied`, which the base calls after
it has applied a depth — and it applies one only when the depth has **moved**.

That last sentence is the trap. On a stock home screen the depth carries on past
the end of the transition to zero, so the effect is recomputed with the settled
state and cleared. With a home screen that rests at `0.15`, the depth reaches
its resting value on the last frame of the transition, while the stable state is
still `AllApps` — so the workspace is blurred, the depth never moves again, and
nothing recomputes it. The icons, their labels and the search bar stay blurred
until something else moves the depth. Only the status bar, a window of its own,
is unaffected.

The fix is to call `blurWorkspaceDepthTargets()` once from
`QuickstepLauncher.onStateSetEnd`, where the stable state is the settled one.
Nothing is decided here: the launcher's own answer is asked for again at the
moment its inputs have changed. Verified in its own log:

```
adb logcat -s BaseDepthController

  shouldBlurWorkspace: true  targetState: Normal currentStableState: AllApps  mCurrentBlur: 45
  shouldBlurWorkspace: false targetState: Normal currentStableState: Normal   mCurrentBlur: 45
```

The second line is the call above, three milliseconds after the transition's
last frame.

### Gotcha: the launcher switches its own blurs off to come home

```
com.android.quickstep.util.BaseDepthControllerImpl
  public void pauseBlursOnWindows(boolean)      // sets mPauseBlurs, then applyDepthAndBlur()
```

`mPauseBlurs` is one of the three things `shouldBlur()` asks, so while it is set
`mCurrentBlur` is 0 whatever the depth says. Six call sites set it, and the ones
that matter here are the two ways home is reached from an app:

| Caller | Gesture |
|---|---|
| `RectFSpringAnim` / `ScalingWorkspaceRevealAnim` | swipe up to home |
| `LauncherBackAnimationController.tryStartBackAnimation`, `.finishAnimation` | swipe to go back |

Each pauses for the length of its animation and unpauses at the end. On a stock
home screen that is invisible, because home rests at no depth and there is no
blur to switch off. On a blurred one it reads as the wallpaper snapping sharp
for the length of the animation and blurring again once it lands.

**The pause cannot be skipped, and does not need to be. Three ways of skipping
it only where it is safe were tried; none holds.** Skipped on a back gesture,
the icons, their labels and the search bar blur along with the wallpaper — that animation follows an app's
window off the screen, which puts the launcher's content under the transition
leash, and `setBackgroundBlurRadius` blurs everything behind the surface it is
set on. Only the status bar, a window of its own, stays sharp.

Both ways home run through one animation, and it is where the pause is raised:

```
com.android.quickstep.util.ScalingWorkspaceRevealAnim
  ScalingWorkspaceRevealAnim(QuickstepLauncher, RectFSpringAnim, RectF, boolean, boolean)
  private final void addBlurLayer()      // insns size: 1 — return-void
  private final void applyBlur(float)    // returns at once: blurLayer is never created
```

The animation was meant to draw its own blur while the controller's are off — it
carries a `blurLayer`, a `BLUR_INTERPOLATOR` and the calls to fill it — but
**`addBlurLayer` is an empty method in this build**, so nothing takes over from
the pause. That is why the pause looks skippable when it is not.

What the wallpaper actually does through these animations is settled by clearing
the workspace effect after every pause, below. With that in place, ten gestures
— swiping up and going back — applied a zero blur on no frame.

What was tried, and why each fails:

- **`mBaseSurfaceOverride`** is null for both gestures at the moment the pause
  is raised, so it separates nothing.
- **What the reveal is built with.** A back gesture is handed a `RectFSpringAnim`;
  a swipe up to home is handed one *sometimes* and nothing the rest of the time,
  so gating on it leaves the flicker in place for most real swipes.
- **Skipping only the pause raised inside the reveal.** Going back usually
  raises its own pause first, from `LauncherBackAnimationController`, so the
  skip lands on an already-paused controller and changes nothing — *usually*.
  It is a race: when the reveal gets there first, the back gesture runs
  unpaused and the whole home screen blurs. Measured both outcomes on the same
  build, minutes apart.

The launcher's own log is the way to see the flicker rather than guess at it,
and `grep -c 'Applying blur: 0 '` over one gesture is the measure of it:

```
adb logcat -s BaseDepthController

  Applying blur: 90 …
  Applying blur: 0  …   <- about 170ms of it
  Applying blur: 90 …
  Applying blur: 45 …
```

### Gotcha: pausing the blurs leaves the workspace effect behind

`pauseBlursOnWindows` applies depth and blur again, which should recompute
`blurWorkspaceDepthTargets` with `mCurrentBlur` at zero and clear the effect. It
does not always: the launcher applies a depth only when the depth has moved, and
a home screen that rests at a depth is the case where it has not. So the
workspace keeps the `RenderEffect` it was given while blurs were on, and the
icons stay smeared for the length of the animation even though the blur behind
them is off. Asking the launcher for that answer again straight after a pause is
what clears it.

### Gotcha: the slider row reserves room for an icon

`Preference.mIconSpaceReserved` is false by default, which is why the switch
rows here need nothing done to them. The theme's slider style sets it, so a
`SeekBarPreference` built the same way lands 56dp right of every switch above
and below it. The field survives the shrinker; writing false to it is the whole
fix.

### Gotcha: SeekBarPreference is renamed all the way down

`androidx.preference.Preference` keeps its member names in this build —
`mPersistent`, `mEnabled`, `mOnChangeListener`, `notifyChanged` are all
there — but `androidx.preference.SeekBarPreference` does not: every field and
method of it is rewritten.

```
androidx.preference.SeekBarPreference
  <init>(Context, AttributeSet)
  c: I   e: I   f: I   g: I        // value, min, max, increment
  c(int, boolean)                  // setValueInternal
```

Nothing there can be asked for by name, and letters change with the next
launcher build. Two things make the row buildable without them:

- **The bounds are the ones it builds itself.** With a null `AttributeSet` the
  constructor reads `min` with a default of 0 and `android:max` with a default
  of 100, so a slider is already 0..100 and `setMin`/`setMax` — both dropped —
  are never wanted. The stored setting carries the same range.
- **The value is set through the only method of that shape.** `c(int, boolean)`
  is the one method the class declares that takes a number and a flag and
  returns nothing, so it is recognised the way the depth bridge is: by shape.

`setEnabled` was dropped as well, so greying the row out writes `mEnabled` on
`Preference` and calls `notifyChanged`.

### Drawing the slider the way Material 3 Expressive draws one

The launcher's theme leaves the row with the platform's thin track and round
knob. Material 3 Expressive wants a 16dp track, a 4dp by 44dp handle that is a
bar, and a 6dp gap holding the track off the handle on both sides.

`onBindViewHolder` keeps its name — it overrides a method of `Preference`, which
is not renamed — so the bar is reached there, and only for rows whose key
carries this module's prefix. The bar itself is the one field of type
`android.widget.SeekBar`.

A `SeekBar` normally clips one drawable over another, which squares off the end
the clip falls on and leaves nowhere to put a gap, so both halves of the track
are drawn by one `Drawable` of this module's own instead. A `ProgressBar` hands
progress to a progress drawable as that drawable's *level* when the drawable is
not a layer list, so the level is what it reads — and because the drawable is
installed after the row already has its value, the first level has to be set by
hand or the track draws empty under a handle that is already halfway across.

Colours are resolved through `android.R.attr.colorAccent`, a framework
attribute. Looking Material attributes up by name does not work here: the
launcher's resource names do not survive its build, `getIdentifier` returns 0,
and the slider comes out white.

### The switch, in Home settings

The switch is a row on the Home Screen page of the launcher's own Home settings,
stored in this module's own preference file beside every other setting. It is
read when the launcher comes back to the front rather than on every call, which
keeps a preference lookup out of every state change: Home settings is the
activity a person leaves to get back to the launcher, so `onResume` is where a
change arrives.

What makes the change visible without waiting for the next state change is
calling `DepthController.setState` again with the state that controller last
applied — the launcher's own path to the wallpaper rather than a second one.
The controllers and their states are collected at that same method, which is the
only place one is handed over. The strength is read there too, so moving the
slider and coming back applies on the same path as switching it on.

Earlier versions put the switch in **Wallpaper & Style** and stored it in
`Settings.Secure` under `pixel_launcher_evolved_home_blur_wallpaper`, because
`com.google.android.apps.wallpaper` does not hold `QUERY_ALL_PACKAGES` and so
cannot resolve this module's content provider. That hook, that scope and that
key are all gone. A value left in secure settings by such a build is ignored and
never read.

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

## The home screen search bar (Android 17, verified September 16, 2026)

```
// CP3A.260905.009: the bar has no host class of its own. It is built by
// OseCustomWidget.createView on the workspace and QsbWidgetFactory.createView
// in the hotseat, and both hand the host view to this one call:
com.android.launcher3.qsb.OseWidgetController
  public static void applyTo(LauncherAppWidgetHostView, boolean)

// CP2A.260805.005:
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

## Naming a settings page

```
com.android.launcher3.settings.SettingsActivity$LauncherSettingsFragment
  public void onCreatePreferences(Bundle, String)
```

The launcher names an open settings page by calling
`Activity.setTitle(preferenceScreen.mTitle)` at the end of `onCreatePreferences`,
once for the page it found under a root key and once for the screen it inflated.
Nothing else writes that title, so a page this module builds in place of that
call has to set both the screen's title and the activity's, or it opens under
the name of the screen it was opened from.

`res/layout-v31/settings_activity.xml` is a `CollapsingToolbarLayout` 226dp tall
over the fragment, so every page of Home settings — the launcher's own included
— shows its name as a large expanded title rather than beside the back arrow.

## Settings palette

Read off a Pixel 8 Pro on Android 17, in dark theme:

| Role | Value | Where the settings app uses it |
|---|---|---|
| `system_surface_bright_dark` | `#2F2B27` | The fill of a settings card |
| `system_surface_container_dark` | `#1C1917` | The screen behind the cards |
| `system_surface_container_high_dark` | `#221F1C` | — |

These are framework resources, so they read the same inside the launcher, which
is what lets this module's rows be filled with the colour the settings app fills
its own with rather than with a copy of it.

## Showing the Overview actions

```
com.android.quickstep.views.OverviewActionsView
  public void updateHiddenFlags(int, boolean)
  private int mHiddenFlags
  private void updateActionButtonsVisibility()
  R.id.action_buttons                 // the LinearLayout holding the buttons
com.google.android.apps.nexuslauncher.overview.NexusOverviewActionsView
  public void onFinishInflate()       // calls super, so a hook on the base runs
```

`mHiddenFlags` is not the edge where the buttons become visible: it is rewritten
on every frame of the Overview transition and reads 0 throughout, including
while Overview is closed. The actions view's own alpha is no better — it also
sits at 1 with Overview closed, because what is not visible then is the view
above it.

What does change is the alpha of `R.id.action_buttons`: the launcher ramps it
from 0 to 1 across the Overview opening, reaching 1 on the frame the task cards
settle — measured at about 340ms for a button press on a Pixel 8 Pro, and longer
or shorter for a gesture depending on how it was thrown.

That ramp is both the signal and the thing worth replacing. It is read as the
progress of the opening and the buttons are placed from it, so their arrival ends
exactly when it does whatever the transition's length; a clock of this module's
own would have to guess that length in advance and would be wrong every time
someone scrubbed the gesture. The alpha is watched with a pre-draw listener
registered in `onFinishInflate`.

Placing them every frame, rather than starting an animator, also covers what the
launcher does in the middle of that ramp: `updateActionButtonsVisibility` adds and
removes buttons there — this module's own Clear all among them — and one arriving
at full opacity beside the others would flicker.

The ramp only happens coming from the home screen. Coming to Overview from an app
the row is put up with its alpha already at 1 on the first frame it is shown, and
late: measured on a Pixel 8 Pro, `TaskView.setFullscreenProgress` reaches 0 — the
card has landed — about 215ms before the row is shown at all. That gap belongs to
the launcher and cannot be closed from here; the row's ancestors are not visible
until it ends.

So the two openings are told apart by `setFullscreenProgress` falling to zero. A
card that has just landed says this is the opening from an app, and the arrival
starts on the first frame the row can be drawn rather than waiting to find out
whether a fade is coming. Without that signal a few opaque frames are waited out
instead, because from the home screen the row is briefly opaque before the
launcher starts its fade.

One more thing about the fade: a button's opacity is its own times the row's. An
arrival spread evenly over the fade therefore does its moving while the row is
still too faint to see it, and what is left reads as a plain fade. The arrival is
mapped onto the part of the fade above 0.3, and each button reaches full opacity
in the first part of its own span, so the movement happens where it can be seen.

## Bringing a card's own buttons up with the app chip

The bubble and split buttons this module injects are children of the task card,
added at inflation and placed on every layout, so they were on screen from the
first frame a card existed — while the launcher's own app chip was still fading
in. The two are the same kind of thing on the same card and should arrive
together.

Nothing needs timing for that. The chip's opacity **is** the launcher's account
of how far Overview has arrived, and it is composed onto the view, so the view's
own alpha is the one place that sees the result of every property writing to it:

```
com.android.quickstep.views.IconAppChipView     // R.id.icon on a task card
  private MultiValueAlpha multiValueAlpha       // composes onto View.setAlpha
```

so a button is given `chip.getAlpha()`, times the fade it already had for a card
growing back into its app. Logged across one swipe into Overview, the chip ramps

```
0.0  0.067  0.142  0.208  0.275  0.35  0.417  0.483  0.558
0.625  0.692  0.767  0.833  0.908  0.975
```

and the buttons now follow it frame for frame, at whatever speed the gesture ran.

`TaskView.setAnimateToIconAlpha` looks like the property to hook instead and is
not: its only callers are `SwipeUpAnimationLogic$SpringAnimationRunner` and
`TaskView.resetViewTransforms`, so it sees the spring path and nothing else.

A card with no chip leaves its buttons as opaque as the card. The follow is a
pre-draw listener, because the opacity moves every frame with no layout to hang
it off — one float read per card per frame, only while the card is in the window
— and a button is created at alpha zero so it cannot flash for the frame before
the chip is first asked about.

## Starting split screen from a card

```
com.android.quickstep.views.TaskView
  public List getTaskContainers()
  public RecentsView getRecentsView()
com.android.quickstep.views.RecentsView
  public void initiateSplitSelect(TaskContainer)
  public void initiateSplitSelect(TaskContainer, int stagePosition, StatsLogManager$EventEnum)
  public RecentsPagedOrientationHandler getPagedOrientationHandler()
com.android.quickstep.orientation.RecentsPagedOrientationHandler
  // CP3A.260905.009
  SplitPositionOption getSplitPositionOption(com.android.launcher3.DeviceProfile)
  // CP2A.260805.005
  List getSplitPositionOptions(com.android.launcher3.DeviceProfile)
com.android.launcher3.util.SplitConfigurationOptions$SplitPositionOption
  public int stagePosition
```

The one-argument overload asks the orientation handler for a default position,
and `PortraitPagedViewHandler` throws `IllegalStateException: Default position
available only for large screens` — so on a phone it is unusable. The launcher's
own menu never calls it: `TaskShortcutFactory$SplitSelectSystemShortcut.onClick`
passes a stage position read from the handler's offered options, along with
`StatsLogManager$LauncherEvent.LAUNCHER_APP_ICON_MENU_SPLIT_LEFT_TOP` or
`…_RIGHT_BOTTOM`. This module does the same, and falls back to the one-argument
call when a build answers with no position. `CP2A.260805.005` answered a list to
take the first of; `CP3A.260905.009` answers the one position directly.

The device profile the handler wants comes from
`RecentsViewContainer.containerFromContext(Context).getDeviceProfile()`, the same
entry point the clear-all action uses to find the recents view.
