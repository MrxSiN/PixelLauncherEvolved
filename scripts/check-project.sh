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
grep -q 'FeatureRegistry.installEnabled' "$SRC/PixelLauncherEvolvedModule.kt"

# A settings outage must disable tweaks rather than the launcher.
grep -q 'DefaultSettings' "$SRC/PixelLauncherEvolvedModule.kt"

# Both sides share one preference group and one description of each tweak.
grep -q 'SETTINGS_GROUP' "$SRC/catalog/FeatureCatalog.kt"
grep -q 'getRemotePreferences' "$SRC/PixelLauncherEvolvedModule.kt"
grep -q 'getRemotePreferences' "$SRC/ui/SettingsViewModel.kt"

# The framework binder arrives before any screen exists, so it is caught in the app.
grep -q 'registerListener' "$SRC/settings/ModuleConnection.kt"
grep -q 'ModuleConnection.register' "$SRC/PixelLauncherEvolvedApp.kt"

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

# The action row is recomputed, and its clear-all button carries no id here.
grep -q 'updateActionButtonsVisibility' "$SRC/feature/overview/OverviewActionsFeature.kt"
grep -q 'recents_clear_all' "$SRC/feature/overview/OverviewActionsFeature.kt"

# Release build: shrunk, with the entry class kept by the name the framework reads.
grep -q 'val appVersion = "1.0.0"' "$ROOT/app/build.gradle.kts"
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
