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

# The framework binder arrives before any screen exists, so it is caught in the app.
grep -q 'registerListener' "$SRC/settings/ModuleConnection.kt"
grep -q 'ModuleConnection.register' "$SRC/PixelLauncherEvolvedApp.kt"

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

# What is left of the app says whether a framework picked the module up.
grep -q "ACTION_APPLICATION_PREFERENCES" "$SRC/ui/HomeSettings.kt"
grep -q "ModuleStatus" "$SRC/ui/SettingsViewModel.kt"

# Clear all in the action row delegates the destructive operation to one action.
grep -q 'ClearAllAction' "$SRC/feature/overview/OverviewClearAllButtonFeature.kt"
grep -q 'dismissAllTasks' "$SRC/feature/overview/ClearAllAction.kt"
grep -q 'fun method' "$SRC/core/Reflect.kt"

# A failing feature is contained.
grep -q 'runCatching' "$SRC/hook/FeatureRegistry.kt"
grep -q 'isEnabled(context.settings)' "$SRC/hook/FeatureRegistry.kt"

# Bubble feature: the three TaskView members the button depends on.
grep -q 'com.android.quickstep.views.TaskView' "$SRC/feature/overview/OverviewBubbleFeature.kt"
grep -q '"onFinishInflate"' "$SRC/feature/overview/OverviewBubbleFeature.kt"
grep -q '"onLayout"' "$SRC/feature/overview/OverviewBubbleFeature.kt"
grep -q '"setFullscreenProgress"' "$SRC/feature/overview/OverviewBubbleFeature.kt"

# Bubble transport: the platform binder path, with a nullable bar location.
grep -q 'com.android.quickstep.SystemUiProxy' "$SRC/feature/overview/bubble/SystemUiProxyBubbleLauncher.kt"
grep -q 'showAppBubble' "$SRC/feature/overview/bubble/SystemUiProxyBubbleLauncher.kt"
grep -q 'com.android.launcher3.util.DaggerSingletonObject' "$SRC/feature/overview/bubble/SystemUiProxyBubbleLauncher.kt"
grep -q 'com.android.wm.shell.shared.bubbles.logging.EntryPoint' "$SRC/feature/overview/bubble/SystemUiProxyBubbleLauncher.kt"

# Overview is put away before the bubble is raised, back onto what it covered.
grep -q 'getRunningTaskView' "$SRC/feature/overview/OverviewCloser.kt"
grep -q 'launchWithAnimation' "$SRC/feature/overview/OverviewCloser.kt"
grep -q 'startHome' "$SRC/feature/overview/OverviewCloser.kt"
grep -q 'overviewCloser.close' "$SRC/feature/overview/bubble/OverviewBubbleDecorator.kt"

# Task resolution reads the recorded launch intent rather than guessing one.
grep -q '"baseIntent"' "$SRC/feature/overview/TaskTargetResolver.kt"
grep -q '"userId"' "$SRC/feature/overview/TaskTargetResolver.kt"
grep -q 'getFirstTask' "$SRC/feature/overview/TaskTargetResolver.kt"

# Placement anchors to the thumbnail, not to the whole card.
grep -q 'getThumbnailBounds' "$SRC/feature/overview/TaskViewGeometry.kt"

# Button styling is borrowed from the launcher, at Material 3 medium FAB size.
grep -q 'ic_bubble_button' "$SRC/feature/overview/bubble/BubbleButtonFactory.kt"
grep -q 'CIRCLE_DP = 56' "$SRC/feature/overview/bubble/BubbleButtonFactory.kt"
grep -q 'GLYPH_DP = 24' "$SRC/feature/overview/bubble/BubbleButtonFactory.kt"

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
grep -q 'Binder.getCallingUid' "$SRC/lock/ScreenLockProvider.kt"
grep -q 'ProcessBuilder("su"' "$SRC/lock/ScreenLocker.kt"
grep -q 'KEYCODE_POWER' "$SRC/lock/ScreenLocker.kt"

# Blur Wallpaper: switched from Wallpaper & Style, applied by raising the floor
# under the launcher's own wallpaper depth rather than blurring its window, and
# shared through secure settings because package visibility hides this module's
# provider from an app without QUERY_ALL_PACKAGES.
grep -q '^com.google.android.apps.wallpaper$' "$META/scope.list"
grep -q 'com.google.android.apps.wallpaper' "$SRC/PixelLauncherEvolvedModule.kt"
grep -q 'Settings.Secure' "$SRC/wallpaper/WallpaperBlur.kt"
grep -q 'pixel_launcher_evolved_home_blur_wallpaper' "$SRC/wallpaper/WallpaperBlur.kt"
grep -q 'com.android.quickstep.util.BaseDepthControllerImpl' "$SRC/feature/wallpaper/LauncherWallpaperBlurFeature.kt"
grep -q 'SET_DEPTH = "setDepth"' "$SRC/feature/wallpaper/LauncherWallpaperBlurFeature.kt"
grep -q 'DEPTH_FIELD = "mDepth"' "$SRC/feature/wallpaper/LauncherWallpaperBlurFeature.kt"
grep -q 'deoptimize' "$SRC/feature/wallpaper/LauncherWallpaperBlurFeature.kt"
grep -q 'registerContentObserver' "$SRC/feature/wallpaper/LauncherWallpaperBlurFeature.kt"

# The switch is appended under Layout, which is the last Home screen entry, and
# takes over the background that closes off the list.
grep -q 'ThemePickerCustomizationOptionsBinder' "$SRC/feature/wallpaper/WallpaperStyleFeature.kt"
# Read once and kept: a module update replaces the APK these come from.
grep -q 'private var labels' "$SRC/feature/wallpaper/WallpaperStyleFeature.kt"
grep -q 'home_customization_option_container' "$SRC/feature/wallpaper/WallpaperStyleFeature.kt"
grep -q 'customization_option_entry_grid' "$SRC/feature/wallpaper/WallpaperStyleFeature.kt"
grep -q 'customization_option_entry_bottom_background' "$SRC/feature/wallpaper/WallpaperStyleFeature.kt"
grep -q 'customization_option_entry_singleton_background' "$SRC/feature/wallpaper/WallpaperStyleFeature.kt"

# Release build: shrunk, with the entry class kept by the name the framework reads.
grep -q 'val appVersion = "0.0.1"' "$ROOT/app/build.gradle.kts"
grep -q 'versionCode = 1' "$ROOT/app/build.gradle.kts"
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
