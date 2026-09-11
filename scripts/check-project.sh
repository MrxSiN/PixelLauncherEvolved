#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SRC="$ROOT/app/src/main/kotlin/my/github/MrxSiN/pixellauncherevolved"
META="$ROOT/app/src/main/resources/META-INF/xposed"

# Modern Xposed module metadata: the framework finds the module through these.
grep -q '^minApiVersion=' "$META/module.prop"
grep -q '^staticScope=true$' "$META/module.prop"
grep -q '^my.github.MrxSiN.pixellauncherevolved.PixelLauncherEvolvedModule$' "$META/java_init.list"
grep -q '^com.google.android.apps.nexuslauncher$' "$META/scope.list"

# Entry point: launcher only, first package only, no hooking logic inline.
grep -q 'com.google.android.apps.nexuslauncher' "$SRC/PixelLauncherEvolvedModule.kt"
grep -q 'isFirstPackage' "$SRC/PixelLauncherEvolvedModule.kt"
grep -q 'XposedModule' "$SRC/PixelLauncherEvolvedModule.kt"
grep -q 'FeatureRegistry.install' "$SRC/PixelLauncherEvolvedModule.kt"

# Installation waits for the launcher application, because the settings file it
# reads lives in that application own data directory.
grep -q "LauncherStartup" "$SRC/PixelLauncherEvolvedModule.kt"
grep -q "com.android.launcher3.LauncherApplication" "$SRC/hook/LauncherStartup.kt"
grep -q "onApplicationCreated" "$SRC/hook/LauncherStartup.kt"

# Settings live beside the launcher that reads them, in one file this module owns.
grep -q "pixel_launcher_evolved" "$SRC/settings/LauncherSettings.kt"
grep -q "MODE_PRIVATE" "$SRC/settings/LauncherSettings.kt"

# Choices made before the move are carried across once, then never again.
grep -q "SETTINGS_GROUP" "$SRC/catalog/FeatureCatalog.kt"
grep -q "getRemotePreferences" "$SRC/PixelLauncherEvolvedModule.kt"
grep -q "SettingsMigration" "$SRC/PixelLauncherEvolvedModule.kt"
grep -q "MARKER" "$SRC/settings/SettingsMigration.kt"

# Tweaks reach a running launcher, and a feature that cannot say so is gated.
grep -q "val isLive" "$SRC/hook/LauncherFeature.kt"
grep -q "it.isLive || it.isEnabled" "$SRC/hook/FeatureRegistry.kt"

# Hiding a button must be reversible, or switching a tweak off would do nothing.
grep -q 'originalVisibility' "$SRC/feature/overview/OverviewActionsFeature.kt"

# The settings are a section of the launcher own Home settings, appended so it
# lands under the launcher own last preference, and drawn with the launcher own
# preference classes because this module cannot link against them.
grep -q "SettingsActivity..LauncherSettingsFragment" "$SRC/feature/settings/LauncherSettingsFeature.kt"
grep -q "onCreatePreferences" "$SRC/feature/settings/LauncherSettingsFeature.kt"
grep -q "getResourcesForApplication" "$SRC/feature/settings/LauncherSettingsFeature.kt"
grep -q "androidx.preference.SwitchPreference" "$SRC/feature/settings/PreferenceApi.kt"
grep -q "androidx.preference.PreferenceScreen" "$SRC/feature/settings/PreferenceApi.kt"
grep -q "mOnChangeListener" "$SRC/feature/settings/PreferenceApi.kt"
grep -q "mPersistent" "$SRC/feature/settings/PreferenceApi.kt"
grep -q "CatalogPage.entries" "$SRC/feature/settings/LauncherSettingsFeature.kt"

# The restart is the launcher ending its own process, from that same section.
grep -q "killProcess" "$SRC/feature/settings/LauncherSettingsFeature.kt"

# The module has no screen of its own: no activity, no application class, and
# nothing of Compose left in the build. What runs in this app is the two
# providers, and they need neither.
[ ! -d "$SRC/ui" ]
[ ! -e "$SRC/PixelLauncherEvolvedApp.kt" ]
[ ! -e "$SRC/settings/ModuleConnection.kt" ]
! grep -q '<activity' "$ROOT/app/src/main/AndroidManifest.xml"
! grep -q 'android:name=".PixelLauncherEvolvedApp"' "$ROOT/app/src/main/AndroidManifest.xml"
! grep -qi 'compose' "$ROOT/app/build.gradle.kts"
! grep -qi 'compose' "$ROOT/gradle/libs.versions.toml"

# Clear all in the action row delegates the destructive operation to one action.
grep -q 'ClearAllAction' "$SRC/feature/overview/OverviewClearAllButtonFeature.kt"
grep -q 'dismissAllTasks' "$SRC/feature/overview/ClearAllAction.kt"
grep -q 'fun method' "$SRC/core/Reflect.kt"

# A failing feature is contained.
grep -q 'runCatching' "$SRC/hook/FeatureRegistry.kt"
grep -q 'isEnabled(context.settings)' "$SRC/hook/FeatureRegistry.kt"

# Card buttons: the three TaskView members every one of them depends on, hooked
# once for all of them.
grep -q 'com.android.quickstep.views.TaskView' "$SRC/feature/overview/OverviewBubbleFeature.kt"
grep -q 'com.android.quickstep.views.TaskView' "$SRC/feature/overview/OverviewSplitButtonFeature.kt"
grep -q '"onFinishInflate"' "$SRC/feature/overview/card/TaskCardHooks.kt"
grep -q '"onLayout"' "$SRC/feature/overview/card/TaskCardHooks.kt"
grep -q '"setFullscreenProgress"' "$SRC/feature/overview/card/TaskCardHooks.kt"

# Bubble transport: the platform binder path, with a nullable bar location.
grep -q 'com.android.quickstep.SystemUiProxy' "$SRC/feature/overview/bubble/SystemUiProxyBubbleLauncher.kt"
grep -q 'showAppBubble' "$SRC/feature/overview/bubble/SystemUiProxyBubbleLauncher.kt"
grep -q 'com.android.launcher3.util.DaggerSingletonObject' "$SRC/feature/overview/bubble/SystemUiProxyBubbleLauncher.kt"
grep -q 'com.android.wm.shell.shared.bubbles.logging.EntryPoint' "$SRC/feature/overview/bubble/SystemUiProxyBubbleLauncher.kt"

# Overview is put away before the bubble is raised, back onto what it covered.
grep -q 'getRunningTaskView' "$SRC/feature/overview/OverviewCloser.kt"
grep -q 'launchWithAnimation' "$SRC/feature/overview/OverviewCloser.kt"
grep -q 'startHome' "$SRC/feature/overview/OverviewCloser.kt"
grep -q 'overviewCloser.close' "$SRC/feature/overview/bubble/BubbleAction.kt"

# Task resolution reads the recorded launch intent rather than guessing one.
grep -q '"baseIntent"' "$SRC/feature/overview/TaskTargetResolver.kt"
grep -q '"userId"' "$SRC/feature/overview/TaskTargetResolver.kt"
grep -q 'getFirstTask' "$SRC/feature/overview/TaskTargetResolver.kt"

# Placement anchors to the thumbnail, not to the whole card.
grep -q 'getThumbnailBounds' "$SRC/feature/overview/TaskViewGeometry.kt"

# Button styling is borrowed from the launcher, at Material 3 medium FAB size.
grep -q 'ic_bubble_button' "$SRC/feature/overview/OverviewBubbleFeature.kt"
grep -q 'CIRCLE_DP = 56' "$SRC/feature/overview/card/TaskCardButtonFactory.kt"
grep -q 'GLYPH_DP = 24' "$SRC/feature/overview/card/TaskCardButtonFactory.kt"

# Split screen goes through the launcher's own selection, started from the card's
# task container the way the card's own menu starts it.
grep -q 'initiateSplitSelect' "$SRC/feature/overview/split/SplitScreenAction.kt"
grep -q 'getTaskContainers' "$SRC/feature/overview/split/SplitScreenAction.kt"
grep -q 'ic_split_horizontal' "$SRC/feature/overview/OverviewSplitButtonFeature.kt"

# The action row is recomputed, and its clear-all button uses the launcher label.
grep -q 'updateActionButtonsVisibility' "$SRC/feature/overview/OverviewActionsFeature.kt"
grep -q 'recents_clear_all' "$SRC/feature/overview/OverviewClearAllButtonFeature.kt"

# Layout: cached profiles, so neither mode can apply to a running launcher.
grep -q 'override val isLive: Boolean = false' "$SRC/feature/layout/TabletModeFeature.kt"
grep -q 'override val isLive: Boolean = false' "$SRC/feature/layout/TaskbarOnlyFeature.kt"

# Tablet mode changes the one classification the grid and the taskbar share.
grep -q 'com.android.launcher3.display.LauncherDisplayInfo' "$SRC/feature/layout/TabletModeFeature.kt"
grep -q '"isLargeScreen"' "$SRC/feature/layout/TabletModeFeature.kt"

# Taskbar only changes the boolean that classification is recorded as, so the
# grid stays on phone measurements and every profile agrees a taskbar exists.
grep -q 'com.android.launcher3.deviceprofile.TaskbarConfiguration' "$SRC/feature/layout/TaskbarOnlyFeature.kt"
grep -q 'deoptimize' "$SRC/feature/layout/TaskbarOnlyFeature.kt"

# The taskbar aligns onto a hotseat the launcher's own profile lays out, so the
# offset has to come from that profile rather than the taskbar window's.
grep -q 'getTaskbarOffsetY' "$SRC/feature/layout/TaskbarOnlyFeature.kt"
grep -q 'com.android.launcher3.taskbar.TaskbarLauncherStateController' "$SRC/feature/layout/TaskbarOnlyFeature.kt"

# Every hotseat icon needs a taskbar icon to morph out of, or it appears at the end.
grep -q 'calculateMaxNumIcons' "$SRC/feature/layout/TaskbarOnlyFeature.kt"
grep -q 'STATIC_TASKBAR_VIEWS' "$SRC/feature/layout/TaskbarOnlyFeature.kt"

# The taskbar is left out of the app own transition into Overview, because the
# window manager lends that window to the app for the length of it.
grep -q "com.android.quickstep.RecentsAnimationCallbacks" "$SRC/feature/layout/TaskbarTransitionFeature.kt"
grep -q "onAnimationStart" "$SRC/feature/layout/TaskbarTransitionFeature.kt"
grep -q "applyState" "$SRC/feature/layout/TaskbarTransitionFeature.kt"
grep -q "isTaskbarAlignedWithHotseat" "$SRC/feature/layout/TaskbarTransitionFeature.kt"

# The taskbar comes back on the later of two moments, because the launcher
# settles about 75 ms before the window manager hands the window back.
grep -q "com.android.systemui.shared.system.RecentsAnimationControllerCompat" \
    "$SRC/feature/layout/TaskbarTransitionFeature.kt"
grep -q "detachNavigationBarFromApp" "$SRC/feature/layout/TaskbarTransitionFeature.kt"
grep -q "launcherSettled && windowReturned" "$SRC/feature/layout/TaskbarTransitionFeature.kt"

# Icons leave the stashed pill whole rather than splitting across the middle,
# and the launcher's own band height is put back afterwards.
grep -q 'animateIconsForReveal' "$SRC/feature/layout/TaskbarOnlyFeature.kt"
grep -q 'mStashedHandleHeight' "$SRC/feature/layout/TaskbarOnlyFeature.kt"
grep -q 'finally' "$SRC/feature/layout/TaskbarOnlyFeature.kt"

# The taskbar's view of the launcher being in front is repaired for the one
# stale report a launcher restart delivers, and left alone otherwise, because
# that answer also drives the taskbar's animation between home and an app.
grep -q 'onLauncherVisibilityChanged' "$SRC/feature/layout/TaskbarHomeVisibilityFeature.kt"
grep -q 'hasBeenResumed' "$SRC/feature/layout/TaskbarHomeVisibilityFeature.kt"
grep -q 'GRACE_MILLIS' "$SRC/feature/layout/TaskbarHomeVisibilityFeature.kt"
grep -q 'TaskbarHomeVisibilityFeature' "$SRC/hook/FeatureRegistry.kt"

# Overview can remove the taskbar app drawer button and its matching divider,
# while preserving the visibility the launcher wanted outside Overview.
grep -q 'OVERVIEW_HIDE_TASKBAR_ALL_APPS' "$SRC/catalog/Settings.kt"
grep -q 'TaskbarAllAppsButtonFeature' "$SRC/hook/FeatureRegistry.kt"
grep -q 'mAllAppsButtonContainer' "$SRC/feature/layout/TaskbarAllAppsButtonFeature.kt"
grep -q 'mTaskbarDividerContainer' "$SRC/feature/layout/TaskbarAllAppsButtonFeature.kt"
grep -q 'originalVisibility' "$SRC/feature/layout/TaskbarAllAppsButtonFeature.kt"

# The row is sized by counting children, so what is left has to be re-centred.
grep -q 'getIconLayoutWidth' "$SRC/feature/layout/TaskbarAllAppsButtonFeature.kt"
grep -q 'mItemMarginLeftRight' "$SRC/feature/layout/TaskbarAllAppsButtonFeature.kt"
grep -q 'mIconTouchSize' "$SRC/feature/layout/TaskbarAllAppsButtonFeature.kt"
# The row is changed between transitions, never during one, or the morph jumps.
grep -q 'whenSettled' "$SRC/feature/layout/TaskbarAllAppsButtonFeature.kt"
grep -q 'ValueAnimator' "$SRC/feature/layout/TaskbarAllAppsButtonFeature.kt"

# The module app has no launcher entry; every setting is somewhere else.
! grep -q 'android.intent.category.LAUNCHER' "$ROOT/app/src/main/AndroidManifest.xml"

# Empty-workspace double taps stay in the launcher; privileged power-key work
# crosses a caller-checked provider and runs in the module app through root.
grep -q 'mLongPressState' "$SRC/feature/gesture/DoubleTapToSleepFeature.kt"

# The search bar is a widget, so its tap is claimed at the launcher's host view,
# and only where the widget has no narrower button of its own.
grep -q 'com.android.launcher3.qsb.OseWidgetView' "$SRC/feature/search/HomeSearchBarFeature.kt"
grep -q 'onInterceptTouchEvent' "$SRC/feature/search/HomeSearchBarFeature.kt"
grep -q 'mHasPerformedLongPress' "$SRC/feature/search/HomeSearchBarFeature.kt"
# The long press is cleared on the way up, so it is read before proceeding.
grep -q 'fun read' "$SRC/feature/search/HomeSearchBarFeature.kt"
grep -q 'requestFocusExplicitly' "$SRC/feature/search/HomeSearchBarFeature.kt"
grep -q 'HOME_SEARCH_OPENS_DRAWER' "$SRC/catalog/Settings.kt"
grep -q 'HomeSearchBarFeature' "$SRC/hook/FeatureRegistry.kt"
grep -q 'Binder.getCallingUid' "$SRC/lock/ScreenLockProvider.kt"

# App drawer search results are filtered where the launcher merges its two
# sources, so both the platform's service and the Google app's web suggestions
# are covered by one hook.
grep -q 'com.android.launcher3.allapps.ActivityAllAppsContainerView'     "$SRC/feature/search/AppDrawerSearchFeature.kt"
grep -q '"setSearchResults"' "$SRC/feature/search/AppDrawerSearchFeature.kt"
grep -q 'AppDrawerSearchFeature' "$SRC/hook/FeatureRegistry.kt"
grep -q 'APP_DRAWER_SEARCH_HIDE_WEB' "$SRC/catalog/Settings.kt"
grep -q 'APP_DRAWER_SEARCH_HIDE_PLAY_STORE' "$SRC/catalog/Settings.kt"
grep -q 'APP_DRAWER_SEARCH_HIDE_SEARCH_IN_APPS' "$SRC/catalog/Settings.kt"
grep -q 'APP_DRAWER(' "$SRC/catalog/FeatureCatalog.kt"

# Every page row carries a line saying what the page holds.
grep -q 'val summaryRes: Int' "$SRC/catalog/FeatureCatalog.kt"
grep -q 'summary = resources.getString(page.summaryRes)' "$SRC/feature/settings/LauncherSettingsFeature.kt"

# Apps hidden from the drawer go through the launcher's own per-tab predicate,
# which is wrapped rather than replaced so a work app is still a work app. The
# predicate asks the store on every call, and the rebuild that shows a change is
# the launcher coming back to the front.
grep -q 'ActivityAllAppsContainerView..AdapterHolder' "$SRC/feature/apps/HiddenAppsFeature.kt"
grep -q '"setup"' "$SRC/feature/apps/HiddenAppsFeature.kt"
grep -q 'getTargetPackage' "$SRC/feature/apps/HiddenAppsFeature.kt"
grep -q 'onResume' "$SRC/feature/apps/HiddenAppsFeature.kt"
grep -q 'notifyUpdate' "$SRC/feature/apps/AppDrawerList.kt"
grep -q 'HiddenAppsFeature' "$SRC/hook/FeatureRegistry.kt"

# Apps are picked in the drawer itself: the icon draws its own tick, because the
# drawer recycles views; the tap is taken where a view acts on being clicked, so
# the launcher's own listener is never replaced; and settling into another state
# is what says the choosing was left.
grep -q 'com.android.launcher3.BubbleTextView' "$SRC/feature/apps/HideAppsPickerFeature.kt"
grep -q '"onDraw"' "$SRC/feature/apps/HideAppsPickerFeature.kt"
grep -q '"getIconBounds"' "$SRC/feature/apps/HideAppsPickerFeature.kt"
grep -q '"performClick"' "$SRC/feature/apps/HideAppsPickerFeature.kt"
grep -q '"onStateSetEnd"' "$SRC/feature/apps/HideAppsPickerFeature.kt"
grep -q 'HideAppsPickerFeature' "$SRC/hook/FeatureRegistry.kt"

# Every app has to be on screen while choosing, or a hidden one could never be
# recovered, and nothing is written until the button is pressed.
grep -q 'HideAppsSelection.isSelecting' "$SRC/feature/apps/HiddenAppsFeature.kt"
grep -q 'fun confirm' "$SRC/feature/apps/HideAppsSelection.kt"

# The drag layer rebuilds layout parameters from ours, so where the button sits
# is said on the ones it ends up with.
grep -q 'button.layoutParams as? FrameLayout.LayoutParams' "$SRC/feature/apps/HideAppsButton.kt"

# A hidden app must not come back the moment its name is typed.
grep -q 'class HiddenSearchResults' "$SRC/feature/search/SearchResultKind.kt"
grep -q 'HiddenAppsStore' "$SRC/feature/search/AppDrawerSearchFeature.kt"

# A tapped Web Search result can be handed to another app. The launcher builds
# one action for that tap and no other, so the action is what identifies it, and
# the words searched for are the result's own title rather than what was typed.
grep -q 'com.android.launcher3.uioverrides.QuickstepLauncher' "$SRC/feature/search/WebSearchAppFeature.kt"
grep -q '"startActivitySafely"' "$SRC/feature/search/WebSearchAppFeature.kt"
grep -q 'com.google.android.PIXEL_SEARCH' "$SRC/feature/search/WebSearchAppFeature.kt"
grep -q '"title"' "$SRC/feature/search/WebSearchAppFeature.kt"
grep -q 'WebSearchAppFeature' "$SRC/hook/FeatureRegistry.kt"

# The candidates are what the device offers, not a list of package names, and a
# chosen app that is gone falls back to the launcher's own answer. A link is
# opened rather than a query handed over, so one tap reaches the results.
grep -q 'ACTION_VIEW' "$SRC/feature/search/WebSearchApps.kt"
grep -q 'CATEGORY_BROWSABLE' "$SRC/feature/search/WebSearchApps.kt"
# Both halves, or the browsers never appear: a scheme with no address, asked
# without the narrowing the platform applies to web links.
grep -q 'Uri.parse("http:")' "$SRC/feature/search/WebSearchApps.kt"
grep -q 'PackageManager.MATCH_ALL' "$SRC/feature/search/WebSearchApps.kt"
# The Google app is the first choice already, so it is never also one of the rest.
grep -q 'com.google.android.googlequicksearchbox' "$SRC/feature/search/WebSearchApps.kt"
grep -q 'resolveActivity' "$SRC/feature/search/WebSearchApps.kt"
grep -q 'WebSearchAppDialog' "$SRC/feature/settings/LauncherSettingsFeature.kt"

# The adapter item that carries a target is renamed by the shrinker; its field
# is found by type instead, and a row without one is never hidden.
grep -q 'android.app.search.SearchTarget' "$SRC/feature/search/SearchTargets.kt"
grep -q 'isTarget(it.type)' "$SRC/feature/search/SearchResultItems.kt"

# Deciding which results to hide has no Android in it, so it can be tested.
! grep -qE '^import android' "$SRC/feature/search/SearchResultKind.kt"
grep -q 'ProcessBuilder("su"' "$SRC/lock/ScreenLocker.kt"
grep -q 'KEYCODE_POWER' "$SRC/lock/ScreenLocker.kt"

# Wallpaper blur changes what the home state reports and lets the launcher draw
# it, so the effect is the launcher's own and a deeper state deepens it rather
# than stacking a second blur. The answer is given at the state, never at the
# depth the launcher ends up applying: the launcher animates a state change from
# the depth it believes it is at, so a floor held under the applied depth alone
# drops the blur out at the start of every transition.
grep -q 'com.android.launcher3.LauncherState' "$SRC/feature/wallpaper/LauncherDepth.kt"
grep -q '"getDepth"' "$SRC/feature/wallpaper/LauncherDepth.kt"
grep -q 'com.android.launcher3.statehandlers.DepthController' "$SRC/feature/wallpaper/LauncherDepth.kt"
grep -q '"setState"' "$SRC/feature/wallpaper/LauncherDepth.kt"
! grep -rq 'mDepth' "$SRC"

# The launcher pauses its own window blurs for the length of an animation home.
# The back gesture's pause must stand: its animation follows an app's window off
# the screen, which puts the launcher's content under the transition leash,
# where a blur behind that leash blurs the icons with the wallpaper. The swipe
# up to home is handed no window animation and needs no pause, and that is what
# tells the two apart. Everything here fails towards leaving the pause alone.
grep -q 'pauseBlursOnWindows' "$SRC/feature/wallpaper/LauncherDepth.kt"
grep -q 'installPausedBlur' "$SRC/feature/wallpaper/HomeWallpaperBlurFeature.kt"
# The pause IS skipped while the tweak is on, which keeps the wallpaper blurred
# through the animation home at the documented cost: a back gesture blurs the
# icons and the search bar with it. HOOK_NOTES.md records why the obvious gates
# on that do not hold. The reveal is watched so a narrower gate has something to
# be built on, and nothing is gated on it yet.
grep -q 'ScalingWorkspaceRevealAnim' "$SRC/feature/wallpaper/LauncherDepth.kt"
grep -q 'return@intercept null' "$SRC/feature/wallpaper/HomeWallpaperBlurFeature.kt"
# RectFSpringAnim stays out: a swipe up to home is handed one only sometimes, so
# gating on it leaves the flicker in place for most real swipes.
! grep -rq 'RectFSpringAnim' "$SRC"
# What is done instead is clearing the workspace effect the pause leaves behind.
grep -q 'refreshBlur' "$SRC/feature/wallpaper/HomeWallpaperBlurFeature.kt"

# The strength is a stored number with its range, and one law turns it into a
# depth: the middle is what the tweak did before it could be changed.
grep -q 'class IntSetting' "$SRC/catalog/Setting.kt"
grep -q 'HOME_BLUR_STRENGTH' "$SRC/catalog/Settings.kt"
grep -q 'fun depthFor' "$SRC/wallpaper/HomeBlurDepth.kt"
grep -q 'FULL_DEPTH' "$SRC/wallpaper/HomeBlurDepth.kt"
grep -q 'coerceIn(setting.range)' "$SRC/settings/SharedPreferencesSettings.kt"

# The slider row is greyed out with the switch above it rather than hidden, and
# every member of the launcher's SeekBarPreference is renamed, so its bounds are
# the ones it builds itself and its value is set through a method found by shape.
grep -q 'androidx.preference.SeekBarPreference' "$SRC/feature/settings/PreferenceApi.kt"
grep -q 'fun valueSetter' "$SRC/feature/settings/PreferenceApi.kt"
grep -q 'mEnabled' "$SRC/feature/settings/PreferenceApi.kt"
grep -q 'hasSlider' "$SRC/feature/settings/PreferenceApi.kt"
grep -q 'fun companionOf' "$SRC/feature/settings/LauncherSettingsFeature.kt"
grep -q 'api.setEnabled' "$SRC/feature/settings/LauncherSettingsFeature.kt"

# The slider row is aligned with the switches, and drawn the way Material 3
# Expressive draws a slider: a tall track, a handle that is a bar, and a gap.
# A SeekBar clips one drawable over another, so both halves are drawn by one
# drawable of this module's own, which reads its progress as its own level.
grep -q 'mIconSpaceReserved' "$SRC/feature/settings/PreferenceApi.kt"
grep -q 'onBindViewHolder' "$SRC/feature/settings/PreferenceApi.kt"
grep -q 'ExpressiveSlider' "$SRC/feature/settings/LauncherSettingsFeature.kt"
grep -q 'TRACK_HEIGHT_DP' "$SRC/feature/settings/ExpressiveSlider.kt"
grep -q 'HANDLE_WIDTH_DP' "$SRC/feature/settings/ExpressiveSlider.kt"
grep -q 'GAP_DP' "$SRC/feature/settings/ExpressiveSlider.kt"
grep -q 'colorAccent' "$SRC/feature/settings/ExpressiveSlider.kt"
grep -q 'expressive.level = levelOf' "$SRC/feature/settings/ExpressiveSlider.kt"

# The ask is an interface call to a five-unit method, so the two callers that
# decide the resting and the animated depth are deoptimized.
grep -q 'deoptimize' "$SRC/feature/wallpaper/LauncherDepth.kt"
grep -q '"setStateWithAnimation"' "$SRC/feature/wallpaper/LauncherDepth.kt"

# Only the home state is answered for, so no other state is touched and this
# module's number is never one the app drawer's depth has to stay above.
grep -q 'chain.thisObject === depth.home' "$SRC/feature/wallpaper/HomeWallpaperBlurFeature.kt"
grep -q 'NORMAL' "$SRC/feature/wallpaper/LauncherDepth.kt"

# The launcher blurs its own workspace as well as the wallpaper while the drawer
# is the state being left, and recomputes that only when the depth moves. A home
# screen that rests at a depth stops it moving, so the launcher is asked for that
# answer again once the state has settled. It is the launcher's own answer.
grep -q 'blurWorkspaceDepthTargets' "$SRC/feature/wallpaper/LauncherDepth.kt"
grep -q 'LauncherDepthController' "$SRC/feature/wallpaper/LauncherDepth.kt"
grep -q 'installSettled' "$SRC/feature/wallpaper/HomeWallpaperBlurFeature.kt"
grep -q 'onStateSetEnd' "$SRC/feature/wallpaper/HomeWallpaperBlurFeature.kt"
grep -q 'refreshBlur' "$SRC/feature/wallpaper/HomeWallpaperBlurFeature.kt"

# A change reaches the screen by applying the state again, which is the
# launcher's own path to the wallpaper rather than a second one.
grep -q 'depth.reapply' "$SRC/feature/wallpaper/HomeWallpaperBlurFeature.kt"
grep -q 'applyState.invoke' "$SRC/feature/wallpaper/LauncherDepth.kt"
grep -q 'HOME_BLUR_WALLPAPER' "$SRC/catalog/Settings.kt"
grep -q 'HomeWallpaperBlurFeature' "$SRC/hook/FeatureRegistry.kt"

# Deciding the depth has no Android in it, so it can be tested.
! grep -qE '^import android' "$SRC/wallpaper/HomeBlurDepth.kt"

# The switch is in the launcher's own Home settings. The secure-settings key,
# the Wallpaper & Style hook and the live-wallpaper render models stay gone, and
# the module stays scoped to the launcher alone.
! grep -rq 'pixel_launcher_evolved_home_blur_wallpaper' "$SRC"
! grep -rq 'magicportrait' "$SRC"
! grep -rq 'com.google.android.apps.wallpaper' "$SRC"
[ ! -d "$SRC/feature/magicportrait" ]
grep -q '^com.google.android.apps.nexuslauncher$' "$META/scope.list"
[ "$(wc -l < "$META/scope.list")" -eq 1 ]

# Pages are assigned from Home settings, by number. The launcher's long press
# menu is a Compose dialog in classes its shrinker renames, so there is no view
# list to add a row to; nothing here reaches for one.
grep -q 'FocusPagesDialog' "$SRC/feature/settings/LauncherSettingsFeature.kt"
! grep -rq 'OptionsPopupView' "$SRC"
! grep -rq 'showForSystemShortcuts' "$SRC"
[ ! -e "$SRC/feature/focus/FocusAssignFeature.kt" ]

# Page numbers are read off the unfiltered order, which is only seen at the
# filter, so settings cannot number them differently while a Mode is on.
grep -q 'FocusPages.remember(all)' "$SRC/feature/focus/FocusHomeFeature.kt"
grep -q 'FocusPages.include(added)' "$SRC/feature/focus/FocusHomeFeature.kt"
grep -q 'commitExtraEmptyScreens' "$SRC/feature/focus/FocusHomeFeature.kt"

# Focus home screens: the launcher is handed a filtered list of screens and
# nothing else. The database is never touched.
grep -q 'bindCompleteModelAsync' "$SRC/feature/focus/FocusHomeFeature.kt"
grep -q 'bindAddScreens' "$SRC/feature/focus/FocusHomeFeature.kt"
grep -q 'bindItems' "$SRC/feature/focus/FocusHomeFeature.kt"

# The launcher keeps no list of screens: collectWorkspaceScreens walks the items
# and collects the screens they name. So the items are what is filtered, and the
# model handed over is a second one around a second map, never the launcher's
# own — its copy() shares the map it copies.
grep -q 'itemsIdMap' "$SRC/feature/focus/FocusHomeFeature.kt"
grep -q 'SparseArray<Any?>(items.size())' "$SRC/feature/focus/FocusHomeFeature.kt"

# The one write that would turn a hidden page into a deleted one is held off,
# and the feature refuses to install at all if it cannot be found.
grep -q 'stripEmptyScreens' "$SRC/feature/focus/FocusHomeFeature.kt"
grep -q 'isFiltering()) null else chain.proceed()' "$SRC/feature/focus/FocusHomeFeature.kt"

# Deciding which screens to show has no Android in it, so it can be reasoned about.
! grep -qE '^import android' "$SRC/focus/FocusPlan.kt"

# Modes are read in this module's own app, which is the process that holds root,
# and answered to the launcher alone. The platform API is not used: since Android
# 15 it reports only the rules the calling app owns, so it returns nothing.
grep -q 'dumpsys notification --zen' "$SRC/focus/ZenModes.kt"
grep -q 'ProcessBuilder("su"' "$SRC/focus/ZenModes.kt"
grep -q 'callingPackage != LAUNCHER_PACKAGE' "$SRC/focus/FocusProvider.kt"

# A Mode that comes on by itself has to be noticed by itself. The interruption
# filter is not enough on its own: Driving and Transit never move it, so the
# configuration tag is watched as well, and it is written for every rule change.
grep -q 'zen_mode_config_etag' "$SRC/focus/FocusSource.kt"

# Reading the Modes is a root shell in another process, so it happens once per
# prompt, on the thread that asked to look, and never again while binding.
grep -q 'activeModes ?: read()' "$SRC/feature/focus/FocusHomeFeature.kt"
[ "$(grep -c 'source.modes()' "$SRC/feature/focus/FocusHomeFeature.kt")" = 1 ]

# The page swap is Material 3 Expressive: content leaves before it is replaced,
# and what arrives is sprung into place rather than wiped in.
! grep -q 'createCircularReveal' "$SRC/feature/focus/FocusRevealMotion.kt"
# The spring itself is shared: the page swap and the Overview action buttons are
# two animations of one system, and two copies of those numbers would be two
# things to keep in agreement.
grep -q 'class SpringInterpolator' "$SRC/core/ExpressiveMotion.kt"
grep -q 'ExpressiveMotion.spatialSpring' "$SRC/feature/focus/FocusRevealMotion.kt"
grep -q 'fun exit(view: View): Animator' "$SRC/feature/focus/FocusRevealMotion.kt"
! grep -q 'ViewAnimationUtils' "$SRC/feature/focus/FocusPageReveal.kt"

# The provider authority is the module's own package with a suffix, asked for at
# runtime rather than written out: a copy of the package here outlives a rename
# of the package itself, which is how the launcher once ended up asking an
# authority that no longer existed.
grep -q 'AUTHORITY_SUFFIX: String = ".focus"' "$SRC/focus/FocusContract.kt"
! grep -q 'content://my\.' "$SRC/focus/FocusContract.kt"
grep -q 'moduleApplicationInfo.packageName' "$SRC/feature/focus/FocusHomeFeature.kt"
grep -q '${applicationId}.focus' "$ROOT/app/src/main/AndroidManifest.xml"

# Release build: shrunk, with the entry class kept by the name the framework reads.
grep -q 'val appVersion = "0.0.5"' "$ROOT/app/build.gradle.kts"
grep -q 'versionCode = 5' "$ROOT/app/build.gradle.kts"
grep -q 'isMinifyEnabled = true' "$ROOT/app/build.gradle.kts"
grep -q 'envKeystorePath' "$ROOT/app/build.gradle.kts"
grep -q 'envKeyPassword' "$ROOT/app/build.gradle.kts"
grep -q 'io.github.libxposed' "$ROOT/gradle/libs.versions.toml"
grep -q 'PixelLauncherEvolvedModule' "$ROOT/app/proguard-rules.pro"
grep -q 'adaptresourcefilecontents META-INF/xposed/java_init.list' "$ROOT/app/proguard-rules.pro"

# Release automation.
grep -q 'workflow_dispatch:' "$ROOT/.github/workflows/android.yml"
grep -q 'Build release APK' "$ROOT/.github/workflows/android.yml"
grep -q 'actions/upload-artifact@v4' "$ROOT/.github/workflows/android.yml"
grep -q 'softprops/action-gh-release@v2' "$ROOT/.github/workflows/android.yml"
grep -qi 'apksigner.*verify' "$ROOT/.github/workflows/android.yml"

echo "Static project checks passed."
