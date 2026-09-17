package my.github.MrxSiN.pixellauncherevolved.diagnostics

import androidx.annotation.StringRes

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.APP_DRAWER_SEARCH
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.BLUR_WALLPAPER
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.BUBBLE_LAUNCHER
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.DOUBLE_TAP_TO_SLEEP
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.FOCUS_HOME_SCREENS
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.FULL_TABLET_LAYOUT
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.HIDDEN_APPS
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.HOME_SEARCH_BAR
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.ORGANIZE_PAGES
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.OVERVIEW_ACTIONS
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.OVERVIEW_ONLY
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.SPLIT_SCREEN
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.TASKBAR_APP_DRAWER_BUTTON
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.TASKBAR_ONLY

/** A tweak as the compatibility page names it. */
enum class CompatibilityFeature(@param:StringRes val titleRes: Int) {
    BLUR_WALLPAPER(R.string.feature_home_blur_wallpaper_title),
    FOCUS_HOME_SCREENS(R.string.feature_focus_home_screens_title),
    ORGANIZE_PAGES(R.string.pages_reorganize_title),
    DOUBLE_TAP_TO_SLEEP(R.string.feature_home_double_tap_to_sleep_title),
    HOME_SEARCH_BAR(R.string.settings_page_search_bar),
    HIDDEN_APPS(R.string.feature_hidden_apps_title),
    APP_DRAWER_SEARCH(R.string.settings_group_search_results),
    OVERVIEW_ACTIONS(R.string.settings_group_overview_actions),
    BUBBLE_LAUNCHER(R.string.compatibility_bubble_launcher),
    SPLIT_SCREEN(R.string.feature_overview_split_title),
    OVERVIEW_ONLY(R.string.layout_mode_overview_only_title),
    TASKBAR_ONLY(R.string.layout_mode_taskbar_only_title),
    FULL_TABLET_LAYOUT(R.string.layout_mode_full_tablet_title),
    TASKBAR_APP_DRAWER_BUTTON(R.string.feature_taskbar_app_drawer_button_title),
}

/**
 * The launcher members every tweak stands on, as read off `CP3A.260905.009`.
 *
 * Each entry names what a feature class looks up, and `LauncherContractsTest`
 * holds each name to the feature sources, so a hook that moves in code and not
 * here fails the build rather than the report. A monthly update that renames
 * one of these shows up on the compatibility page before anyone has to find it
 * by a tweak silently doing nothing. See HOOK_NOTES.md for what each one is.
 */
object LauncherContracts {

    private const val BOOLEAN = "boolean"
    private const val INT = "int"
    private const val FLOAT = "float"
    private const val LONG = "long"
    private const val CONTEXT = "android.content.Context"
    private const val VIEW = "android.view.View"

    private const val LAUNCHER = "com.android.launcher3.Launcher"
    private const val QUICKSTEP_LAUNCHER = "com.android.launcher3.uioverrides.QuickstepLauncher"
    private const val LAUNCHER_STATE = "com.android.launcher3.LauncherState"
    private const val BASE_STATE = "com.android.launcher3.statemanager.BaseState"
    private const val ITEM_INFO = "com.android.launcher3.model.data.ItemInfo"
    private const val MODEL_CALLBACKS = "com.android.launcher3.ModelCallbacks"
    private const val WORKSPACE = "com.android.launcher3.Workspace"
    private const val ALL_APPS_VIEW = "com.android.launcher3.allapps.ActivityAllAppsContainerView"
    private const val WIDGET_HOST = "com.android.launcher3.widget.LauncherAppWidgetHostView"
    private const val TASK_VIEW = "com.android.quickstep.views.TaskView"
    private const val RECENTS_VIEW = "com.android.quickstep.views.RecentsView"
    private const val ACTIONS_VIEW = "com.android.quickstep.views.OverviewActionsView"
    private const val SYSTEM_UI_PROXY = "com.android.quickstep.SystemUiProxy"
    private const val DEVICE_PROFILE = "com.android.launcher3.DeviceProfile"
    private const val DEVICE_PROPERTIES = "com.android.launcher3.deviceprofile.DeviceProperties"
    private const val CONTAINER_INTERFACE = "com.android.quickstep.BaseContainerInterface"
    private const val TASKBAR_VIEW = "com.android.launcher3.taskbar.TaskbarView"
    private const val EXECUTORS = "com.android.launcher3.util.Executors"

    private val TASK_CARD = setOf(BUBBLE_LAUNCHER, SPLIT_SCREEN)
    private val TASKBAR = setOf(TASKBAR_ONLY, FULL_TABLET_LAYOUT)

    val all: List<LauncherContract> = listOf(
        // Blur wallpaper
        contract(LAUNCHER_STATE, method("getDepth", "com.android.launcher3.views.ActivityContext"), BLUR_WALLPAPER),
        contract("com.android.launcher3.statehandlers.DepthController", method("setState", "java.lang.Object"), BLUR_WALLPAPER),
        contract(QUICKSTEP_LAUNCHER, method("onStateSetEnd", BASE_STATE), BLUR_WALLPAPER, HIDDEN_APPS),
        optional("com.android.launcher3.statehandlers.LauncherDepthController", method("blurWorkspaceDepthTargets"), BLUR_WALLPAPER),
        optional("com.android.quickstep.util.BaseDepthControllerImpl", method("pauseBlursOnWindows", BOOLEAN), BLUR_WALLPAPER),

        // Focus home screens
        LauncherContract(
            Signature(MODEL_CALLBACKS, Member.Method("bindModelWithAsyncInflation")),
            setOf(FOCUS_HOME_SCREENS),
            alternatives = listOf(Signature(MODEL_CALLBACKS, Member.Method("bindCompleteModelAsync"))),
        ),
        contract(MODEL_CALLBACKS, Member.Method("bindAddScreens"), FOCUS_HOME_SCREENS),
        optional(MODEL_CALLBACKS, Member.Method("bindItems"), FOCUS_HOME_SCREENS),
        contract(
            "com.android.launcher3.model.data.WorkspaceData\$MutableWorkspaceData",
            method("collectWorkspaceScreens"),
            FOCUS_HOME_SCREENS,
        ),
        contract(WORKSPACE, Member.Field("mScreenOrder"), FOCUS_HOME_SCREENS),
        optional(WORKSPACE, Member.Method("stripEmptyScreens"), FOCUS_HOME_SCREENS),
        optional(WORKSPACE, Member.Method("insertNewWorkspaceScreen"), FOCUS_HOME_SCREENS),
        optional("com.android.launcher3.model.WorkspaceItemSpaceFinder", Member.Method("findSpaceForItem"), FOCUS_HOME_SCREENS),
        contract(LAUNCHER, Member.Field("mModel"), FOCUS_HOME_SCREENS),

        // Organize home screens
        contract("com.android.launcher3.LauncherAppState", Member.Field("model"), ORGANIZE_PAGES),
        contract(EXECUTORS, Member.Field("MODEL_EXECUTOR"), ORGANIZE_PAGES),

        // Double tap to sleep
        contract("com.android.launcher3.touch.WorkspaceTouchListener", method("onTouch", VIEW, "android.view.MotionEvent"), DOUBLE_TAP_TO_SLEEP),
        contract("com.android.launcher3.touch.WorkspaceTouchListener", Member.Field("mLongPressState"), DOUBLE_TAP_TO_SLEEP),

        // Home screen search bar
        contract("com.android.launcher3.qsb.OseWidgetController", method("applyTo", WIDGET_HOST, BOOLEAN), HOME_SEARCH_BAR),
        contract(WIDGET_HOST, method("onInterceptTouchEvent", "android.view.MotionEvent"), HOME_SEARCH_BAR),
        contract("com.android.launcher3.statemanager.StateManager", Member.Method("goToState"), HOME_SEARCH_BAR, HIDDEN_APPS),

        // Hidden apps
        contract("$ALL_APPS_VIEW\$AdapterHolder", method("setup", VIEW, "java.util.function.Predicate"), HIDDEN_APPS),
        contract(ITEM_INFO, method("getTargetPackage"), HIDDEN_APPS),
        contract("com.android.launcher3.BubbleTextView", method("onDraw", "android.graphics.Canvas"), HIDDEN_APPS),

        // App drawer search results
        contract(ALL_APPS_VIEW, method("setSearchResults", "java.util.ArrayList", BOOLEAN), APP_DRAWER_SEARCH),
        contract(QUICKSTEP_LAUNCHER, method("startActivitySafely", VIEW, "android.content.Intent", ITEM_INFO), APP_DRAWER_SEARCH),

        // Overview actions
        contract(ACTIONS_VIEW, method("onFinishInflate"), OVERVIEW_ACTIONS),
        contract(ACTIONS_VIEW, method("updateForGroupedTask", BOOLEAN), OVERVIEW_ACTIONS),
        contract(RECENTS_VIEW, method("dismissAllTasks", VIEW), OVERVIEW_ACTIONS),
        contract("com.android.quickstep.views.RecentsViewContainer", method("containerFromContext", CONTEXT), OVERVIEW_ACTIONS, SPLIT_SCREEN),

        // Buttons on a task card
        contract(TASK_VIEW, method("onFinishInflate"), TASK_CARD),
        contract(TASK_VIEW, method("onLayout", BOOLEAN, INT, INT, INT, INT), TASK_CARD),
        contract(TASK_VIEW, method("setFullscreenProgress", FLOAT), TASK_CARD),
        contract(TASK_VIEW, method("getThumbnailBounds", "android.graphics.Rect"), TASK_CARD),
        contract(SYSTEM_UI_PROXY, Member.Field("INSTANCE"), BUBBLE_LAUNCHER),
        contract(
            SYSTEM_UI_PROXY,
            method(
                "showAppBubble",
                "android.content.Intent",
                "android.os.UserHandle",
                "com.android.wm.shell.shared.bubbles.logging.EntryPoint",
                "com.android.wm.shell.shared.bubbles.BubbleBarLocation",
            ),
            BUBBLE_LAUNCHER,
        ),
        contract("com.android.launcher3.util.DaggerSingletonObject", method("get", CONTEXT), BUBBLE_LAUNCHER, ORGANIZE_PAGES),
        contract(TASK_VIEW, method("getTaskContainers"), SPLIT_SCREEN),
        contract(RECENTS_VIEW, Member.Method("initiateSplitSelect"), SPLIT_SCREEN),

        // Overview only
        contract(RECENTS_VIEW, method("showAsGrid"), OVERVIEW_ONLY),
        contract(DEVICE_PROFILE, Member.Field("deviceProperties"), OVERVIEW_ONLY, TASKBAR_ONLY),
        contract(DEVICE_PROFILE, Member.Field("overviewProfile"), OVERVIEW_ONLY),
        contract(DEVICE_PROPERTIES, Member.Field("isLargeScreen"), OVERVIEW_ONLY),
        contract(CONTAINER_INTERFACE, Member.Method("calculateGridSize"), OVERVIEW_ONLY),
        contract(CONTAINER_INTERFACE, Member.Method("calculateLargeTileSize"), OVERVIEW_ONLY),
        contract("com.android.quickstep.views.IconAppChipView", method("onFinishInflate"), OVERVIEW_ONLY),
        optional(
            "com.android.launcher3.views.Snackbar",
            method("show", "com.android.launcher3.views.ActivityContext", "java.lang.CharSequence", INT, "java.lang.Runnable", "java.lang.Runnable"),
            OVERVIEW_ONLY,
        ),

        // Taskbar only and full tablet layout
        contract("$DEVICE_PROPERTIES\$Factory", Member.Method("createDeviceProperties"), TASKBAR_ONLY),
        contract(DEVICE_PROPERTIES, Member.Field("taskbarConfiguration"), TASKBAR_ONLY),
        contract(DEVICE_PROFILE, Member.Field("hotseatProfile"), TASKBAR_ONLY),
        contract(TASKBAR_VIEW, method("calculateMaxNumIcons"), TASKBAR_ONLY),
        contract("com.android.launcher3.taskbar.LauncherTaskbarUIController", Member.Method("onLauncherVisibilityChanged"), TASKBAR),
        contract("com.android.launcher3.taskbar.TaskbarStashController", method("updateStateForFlag", LONG, BOOLEAN), TASKBAR),
        contract(EXECUTORS, Member.Field("TASKBAR_UI_THREAD"), TASKBAR),
        contract(
            "com.android.launcher3.display.LauncherDisplayInfo",
            method("isLargeScreen", "com.android.launcher3.util.WindowBounds"),
            FULL_TABLET_LAYOUT,
        ),

        // Taskbar app drawer button
        contract(TASKBAR_VIEW, Member.Field("mAllAppsButtonContainer"), TASKBAR_APP_DRAWER_BUTTON),
        contract(TASKBAR_VIEW, Member.Field("mTaskbarDividerContainer"), TASKBAR_APP_DRAWER_BUTTON),
    )

    private fun method(name: String, vararg parameters: String) = Member.Method(name, parameters.toList())

    private fun contract(owner: String, member: Member, vararg features: CompatibilityFeature) =
        contract(owner, member, features.toSet())

    private fun contract(owner: String, member: Member, features: Set<CompatibilityFeature>) =
        LauncherContract(Signature(owner, member), features)

    private fun optional(owner: String, member: Member, vararg features: CompatibilityFeature) =
        LauncherContract(Signature(owner, member), features.toSet(), isRequired = false)
}
