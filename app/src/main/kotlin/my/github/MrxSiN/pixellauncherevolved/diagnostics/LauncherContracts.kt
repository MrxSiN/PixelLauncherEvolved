package my.github.MrxSiN.pixellauncherevolved.diagnostics

import androidx.annotation.StringRes

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.APP_DRAWER_COLUMNS
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.APP_DRAWER_SEARCH
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.BLUR_WALLPAPER
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.BUBBLE_LAUNCHER
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.DOCK
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.DOCK_ICONS
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.DOUBLE_TAP_TO_SLEEP
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.FOCUS_HOME_SCREENS
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.FULL_TABLET_LAYOUT
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.GRID
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.GRID_ICON_SIZE
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.GRID_SPACING
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.HIDDEN_APPS
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.HOME_SEARCH_BAR
import my.github.MrxSiN.pixellauncherevolved.diagnostics.CompatibilityFeature.ICON_PACK
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
    DOCK(R.string.feature_dock_show_title),
    DOCK_ICONS(R.string.feature_dock_icons_title),
    GRID(R.string.compatibility_grid),
    GRID_ICON_SIZE(R.string.feature_grid_icon_size_title),
    GRID_SPACING(R.string.compatibility_grid_spacing),
    APP_DRAWER_COLUMNS(R.string.compatibility_app_drawer_columns),
    HIDDEN_APPS(R.string.feature_hidden_apps_title),
    ICON_PACK(R.string.settings_page_icons),
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
    private const val HOTSEAT = "com.android.launcher3.Hotseat"
    private const val CELL_LAYOUT = "com.android.launcher3.CellLayout"
    private const val DISPLAY_OPTION_SPEC = "com.android.launcher3.deviceprofile.parser.DisplayOptionSpec"
    private const val DISPLAY_OPTION = "com.android.launcher3.deviceprofile.parser.DisplayOption"
    private const val GRID_OPTION = "com.android.launcher3.deviceprofile.parser.GridOption"
    private const val MIGRATION_OPTION = "com.android.launcher3.model.GridMigrationOption"
    private const val GRID_STATE = "com.android.launcher3.model.DeviceGridState"
    private const val WORKSPACE_PROFILE = "com.android.launcher3.deviceprofile.WorkspaceProfile"
    private const val CELL_SPECS_PROVIDER = "com.android.launcher3.responsive.ResponsiveCellSpecsProvider"
    private const val SPECS_PROVIDER = "com.android.launcher3.responsive.ResponsiveSpecsProvider"
    private const val SPEC_GROUP = "com.android.launcher3.responsive.ResponsiveSpecGroup"
    private const val SIZE_SPEC = "com.android.launcher3.responsive.SizeSpec"
    private const val DEVICE_PROPERTIES = "com.android.launcher3.deviceprofile.DeviceProperties"
    private const val CONTAINER_INTERFACE = "com.android.quickstep.BaseContainerInterface"
    private const val TASKBAR_VIEW = "com.android.launcher3.taskbar.TaskbarView"
    private const val EXECUTORS = "com.android.launcher3.util.Executors"
    private const val APP_STATE = "com.android.launcher3.LauncherAppState"
    private const val LAUNCHER_MODEL = "com.android.launcher3.LauncherModel"
    private const val ICON_PROVIDER = "com.android.launcher3.icons.IconProvider"
    private const val PERSISTED_ITEM_STATE = "com.android.launcher3.icons.PersistedItemState"
    private const val PACKAGE_ITEM_INFO = "android.content.pm.PackageItemInfo"
    private const val APPLICATION_INFO = "android.content.pm.ApplicationInfo"
    private const val STRING_ARRAY = "[Ljava.lang.String;"

    private val TASK_CARD = setOf(BUBBLE_LAUNCHER, SPLIT_SCREEN)
    private val TASKBAR = setOf(TASKBAR_ONLY, FULL_TABLET_LAYOUT)

    val all: List<LauncherContract> = listOf(
        // Blur wallpaper
        contract(LAUNCHER_STATE, method("getDepth", "com.android.launcher3.views.ActivityContext"), BLUR_WALLPAPER),
        contract("com.android.launcher3.statehandlers.DepthController", method("setState", "java.lang.Object"), BLUR_WALLPAPER),
        contract(QUICKSTEP_LAUNCHER, method("onStateSetEnd", BASE_STATE), BLUR_WALLPAPER, HIDDEN_APPS),
        optional("com.android.launcher3.statehandlers.LauncherDepthController", method("blurWorkspaceDepthTargets"), BLUR_WALLPAPER),
        optional("com.android.quickstep.util.BaseDepthControllerImpl", method("pauseBlursOnWindows", BOOLEAN), BLUR_WALLPAPER),
        optional("com.android.quickstep.util.BaseDepthControllerImpl", Member.Field("mCurrentBlur"), BLUR_WALLPAPER),
        optional("com.android.quickstep.util.BaseDepthControllerImpl", Member.Field("mMaxBlurRadius"), BLUR_WALLPAPER),

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

        // Icon packs
        contract(ICON_PROVIDER, method("getIcon", PACKAGE_ITEM_INFO, APPLICATION_INFO, INT), ICON_PACK),
        contract(ICON_PROVIDER, method("getStateForApp", APPLICATION_INFO), ICON_PACK),
        contract(PERSISTED_ITEM_STATE, method("withAdditionalValues", STRING_ARRAY), ICON_PACK),
        // A live change needs these; without them a chosen pack still applies,
        // on the next launcher start.
        optional(APP_STATE, Member.Field("model"), ICON_PACK),
        optional(LAUNCHER_MODEL, method("forceReload", "java.lang.String"), ICON_PACK),
        // Keeps themed icons off pack artwork; without it pack icons are themed too.
        optional(
            "com.android.launcher3.icons.mono.MonoIconThemeController",
            method(
                "createThemedBitmap",
                "android.graphics.drawable.AdaptiveIconDrawable",
                "com.android.launcher3.icons.BitmapInfo",
                "com.android.launcher3.icons.BaseIconFactory",
                "com.android.launcher3.icons.SourceHint",
            ),
            ICON_PACK,
        ),
        optional("com.android.launcher3.icons.SourceHint", Member.Field("key"), ICON_PACK),
        // Wallpaper & style asks over grid_control, and its pictures are drawn by the launcher's own factory.
        optional(
            "com.android.launcher3.util.ContentProviderProxy",
            method("call", "java.lang.String", "java.lang.String", "android.os.Bundle"),
            ICON_PACK,
        ),
        optional("com.android.launcher3.icons.LauncherIcons\$Companion", method("obtain", CONTEXT), ICON_PACK),
        optional(
            "com.android.launcher3.icons.BitmapInfo",
            method("newIcon", CONTEXT, INT, "com.android.launcher3.icons.IconShape"),
            ICON_PACK,
        ),

        // Dock: the hidden dock's row given to the Home screen, without a grid migration.
        contract("com.android.launcher3.InvariantDeviceProfile", Member.Field("numRows"), DOCK),
        contract("com.android.launcher3.InvariantDeviceProfile", Member.Field("numColumns"), DOCK),
        contract("com.android.launcher3.InvariantDeviceProfile", method("initGrid", "java.lang.String"), DOCK),
        contract("com.android.launcher3.model.DeviceGridState", Member.Field("mGridSizeString"), DOCK),
        contract("com.android.launcher3.InvariantDeviceProfile", Member.Method("newDPBuilder"), DOCK),
        contract(HOTSEAT, method("resetLayout", BOOLEAN), DOCK),
        contract(DEVICE_PROPERTIES, method("isVerticalBarLayout"), DOCK),
        contract("com.android.launcher3.util.MultiValueAlpha", method("apply", FLOAT), DOCK),
        contract("com.android.launcher3.util.MultiPropertyFactory", Member.Field("mTarget"), DOCK),
        contract("com.android.launcher3.deviceprofile.HotseatProfile", Member.Field("isQsbInline"), DOCK),
        optional("com.android.launcher3.InvariantDeviceProfile", Member.Field("isFixedLandscape"), DOCK),
        contract("com.android.launcher3.deviceprofile.DeviceProfileBuilder", method("build"), DOCK),
        contract("com.android.launcher3.deviceprofile.HotseatProfile", Member.Field("barSizePx"), DOCK),
        contract("com.android.launcher3.deviceprofile.HotseatProfile", Member.Field("cellHeightPx"), DOCK),
        contract("com.android.launcher3.deviceprofile.WorkspaceProfile", Member.Field("workspacePadding"), DOCK),
        optional("com.android.launcher3.deviceprofile.WorkspaceProfile", Member.Field("cellLayoutHeightSpecification"), DOCK),
        contract("com.android.launcher3.LauncherModel", Member.Field("mBgDataModel"), DOCK),
        contract("com.android.launcher3.model.BgDataModel", Member.Field("itemsIdMap"), DOCK),
        contract(ITEM_INFO, Member.Field("cellY"), DOCK),
        contract(ITEM_INFO, Member.Field("spanX"), DOCK),
        contract(ITEM_INFO, Member.Field("spanY"), DOCK),
        optional("com.android.launcher3.InvariantDeviceProfile", method("onConfigChanged"), DOCK),
        contract(HOTSEAT, method("setInsets", "android.graphics.Rect"), DOCK),
        contract(CELL_LAYOUT, Member.Field("mShortcutsAndWidgets"), DOCK),
        contract(CELL_LAYOUT, Member.Field("mActivity"), DOCK),
        contract("com.android.launcher3.deviceprofile.TaskbarConfiguration", Member.Field("isTaskbarPresent"), DOCK),
        contract(DISPLAY_OPTION_SPEC, method("mapTypeIndex", BOOLEAN, BOOLEAN), DOCK_ICONS),
        contract(DISPLAY_OPTION_SPEC, Member.Field("numShownHotseatIcons"), DOCK_ICONS),
        // The dock's database capacity, raised for a count above the grid's.
        contract("com.android.launcher3.InvariantDeviceProfile", method("initGrid", "java.lang.String"), DOCK_ICONS),
        // Applies a new count live, as a grid change from Wallpaper & style is.
        optional("com.android.launcher3.InvariantDeviceProfile", method("onConfigChanged"), DOCK_ICONS),
        contract("com.android.launcher3.InvariantDeviceProfile", Member.Field("numDatabaseHotseatIcons"), DOCK_ICONS),
        contract(DEVICE_PROFILE, Member.Field("inv"), DOCK_ICONS),
        // What fits across the dock.
        contract(DEVICE_PROFILE, Member.Field("hotseatProfile"), DOCK_ICONS),
        contract(DEVICE_PROFILE, Member.Field("workspaceProfile"), DOCK_ICONS),
        contract("com.android.launcher3.deviceprofile.HotseatProfile", Member.Field("widthPx"), DOCK_ICONS),
        contract("com.android.launcher3.deviceprofile.WorkspaceProfile", Member.Field("iconSizePx"), DOCK_ICONS),
        // Moving the apps a smaller count leaves out onto the Home screen, so the launcher never deletes them.
        contract(ITEM_INFO, Member.Field("container"), DOCK_ICONS),
        contract(ITEM_INFO, Member.Field("screenId"), DOCK_ICONS),
        optional(ITEM_INFO, Member.Field("title"), DOCK_ICONS),
        contract(HOTSEAT, Member.Field("mWorkspace"), DOCK_ICONS),
        contract(WORKSPACE, method("getScreenIdForPageIndex", INT), DOCK_ICONS),
        contract(CELL_LAYOUT, method("isOccupied", INT, INT), DOCK_ICONS),
        contract(CELL_LAYOUT, Member.Field("mCountX"), DOCK_ICONS),
        contract(CELL_LAYOUT, Member.Field("mCountY"), DOCK_ICONS),
        contract(LAUNCHER, method("getModelWriter"), DOCK_ICONS),
        contract("com.android.launcher3.model.ModelWriter", Member.Method("modifyItemInDatabase"), DOCK_ICONS),
        contract(LAUNCHER, Member.Field("mModel"), DOCK_ICONS),
        optional(LAUNCHER_MODEL, method("forceReload", "java.lang.String"), DOCK_ICONS),
        // More icons than fit at the Home screen's icon size: whole cells, smaller icons.
        contract("com.android.launcher3.deviceprofile.DeviceProfileBuilder", method("build"), DOCK_ICONS),
        contract("com.android.launcher3.deviceprofile.HotseatProfile", Member.Field("borderSpace"), DOCK_ICONS),
        contract("com.android.launcher3.deviceprofile.HotseatProfile", Member.Field("numShownIcons"), DOCK_ICONS),
        contract(CELL_LAYOUT, Member.Method("addViewToCellLayout"), DOCK_ICONS),
        contract("com.android.launcher3.BubbleTextView", Member.Field("mIconSize"), DOCK_ICONS),
        contract("com.android.launcher3.BubbleTextView", Member.Field("mIcon"), DOCK_ICONS),
        contract("com.android.launcher3.BubbleTextView", Member.Method("setIcon"), DOCK_ICONS),
        LauncherContract(Signature("com.android.launcher3.folder.FolderIcon"), setOf(DOCK_ICONS), isRequired = false),
        optional(DEVICE_PROFILE, Member.Field("folderProfile"), DOCK_ICONS),
        optional("com.android.launcher3.deviceprofile.FolderProfile", Member.Field("folderIconOffsetYPx"), DOCK_ICONS),
        optional("com.android.launcher3.views.PredictedAppIcon", Member.Field("mNormalizedIconSize"), DOCK_ICONS),
        optional("com.android.launcher3.views.PredictedAppIcon", method("updateRingPath"), DOCK_ICONS),

        // Grid & size: columns and rows, through the launcher's own grid option and migration
        contract(DISPLAY_OPTION, Member.Method("parseWeightedPredefinedDisplayOption"), GRID, GRID_ICON_SIZE),
        contract(DISPLAY_OPTION, Member.Field("grid"), GRID),
        contract(GRID_OPTION, Member.Field("numColumns"), GRID),
        contract(GRID_OPTION, Member.Field("numRows"), GRID),
        contract(GRID_OPTION, Member.Field("numSearchContainerColumns"), GRID),
        contract(GRID_OPTION, Member.Field("dbFile"), GRID),
        contract(GRID_OPTION, Member.Field("name"), GRID),
        // The Custom tile in Wallpaper & style → Layout, and the icon Size tab, over grid_control
        contract("com.android.launcher3.util.ContentProviderProxy", Member.Method("query"), GRID),
        contract("com.android.launcher3.dagger.LauncherBaseAppComponent", method("getGridCustomizationsProxy"), GRID),
        contract("com.android.launcher3.util.ContentProviderProxy", method("call", "java.lang.String", "java.lang.String", "android.os.Bundle"), GRID, GRID_ICON_SIZE),
        contract("com.android.launcher3.InvariantDeviceProfile", method("setCurrentGrid", "java.lang.String"), GRID),
        optional("com.android.launcher3.preview.PreviewSurfaceRenderer", method("recreatePreviewRenderer"), GRID, GRID_ICON_SIZE),
        optional("com.android.launcher3.preview.PreviewSurfaceRenderer", Member.Field("mPreviewContext"), GRID, GRID_ICON_SIZE),
        contract(GRID_OPTION, Member.Field("isFixedLandscape"), GRID),
        optional(GRID_OPTION, Member.Field("mIsDualGrid"), GRID),
        contract(GRID_OPTION, Member.Field("displayOptionSpec"), GRID),
        contract("com.android.launcher3.display.LauncherDisplayInfo", method("getDeviceType"), GRID),
        contract("$MIGRATION_OPTION\$Companion", method("from", INT, INT), GRID),
        contract(MIGRATION_OPTION, Member.Method("canMigrate"), GRID),
        contract("$MIGRATION_OPTION\$FourByFour", Member.Field("INSTANCE"), GRID),
        contract("com.android.launcher3.model.GridSizeMigrationLogic", Member.Method("migrateGrid"), GRID),
        contract("com.android.launcher3.model.GridSizeMigrationLogic", Member.Field("context"), GRID),
        contract(GRID_STATE, Member.Field("mDbFile"), GRID),
        contract(GRID_STATE, method("writeToPrefs", CONTEXT), GRID),
        contract("com.android.launcher3.InvariantDeviceProfile", Member.Field("supportedProfiles"), GRID),
        contract("com.android.launcher3.InvariantDeviceProfile", method("onConfigChanged"), GRID, GRID_ICON_SIZE, GRID_SPACING, APP_DRAWER_COLUMNS),
        contract(DEVICE_PROPERTIES, Member.Field("isLandscape"), GRID),
        contract(WORKSPACE_PROFILE, Member.Field("cellLayoutWidthSpecification"), GRID),
        contract(WORKSPACE_PROFILE, Member.Field("cellLayoutHeightSpecification"), GRID),
        contract(WORKSPACE_PROFILE, Member.Field("cellLayoutPaddingPx"), GRID),
        contract(WORKSPACE_PROFILE, Member.Field("cellLayoutBorderSpacePx"), GRID),
        contract(WORKSPACE_PROFILE, Member.Field("iconTextSizePx"), GRID),
        contract(APP_STATE, Member.Field("model"), GRID),
        contract(ITEM_INFO, Member.Field("minSpanX"), GRID),
        contract(ITEM_INFO, Member.Field("minSpanY"), GRID),
        contract("com.android.launcher3.model.GridSizeMigrationLogic", Member.Method("solveGridPlacement"), GRID),
        contract("com.android.launcher3.model.GridSizeMigrationLogic", Member.Field("extraItemsProvider"), GRID),
        contract("com.android.launcher3.model.GridSizeMigrationLogic", Member.Method("getItemsToBeAdded"), GRID),
        optional("com.android.launcher3.InvariantDeviceProfile", Member.Field("mPrefs"), GRID),
        contract("com.android.launcher3.model.GridSizeMigrationLogic", Member.Method("getItemsToBeRemoved"), GRID),
        contract("com.android.launcher3.model.DbEntry", Member.Field("mFolderItems"), GRID),
        contract(ITEM_INFO, Member.Field("id"), GRID),
        contract(ITEM_INFO, Member.Field("itemType"), GRID),
        contract(ITEM_INFO, Member.Field("screenId"), GRID),
        contract(ITEM_INFO, Member.Field("container"), GRID),
        contract(ITEM_INFO, Member.Field("cellX"), GRID),
        contract(ITEM_INFO, Member.Field("cellY"), GRID),
        contract(ITEM_INFO, Member.Field("spanX"), GRID),
        contract(ITEM_INFO, Member.Field("spanY"), GRID),
        // Icon size
        contract("$CELL_SPECS_PROVIDER\$Companion", Member.Method("create"), GRID_ICON_SIZE),
        contract(CELL_SPECS_PROVIDER, Member.Field("groupOfSpecs"), GRID_ICON_SIZE),
        contract(SPEC_GROUP, Member.Field("heightSpecs"), GRID_ICON_SIZE, GRID_SPACING),
        contract(SPEC_GROUP, Member.Field("widthSpecs"), GRID_ICON_SIZE, GRID_SPACING),
        contract("com.android.launcher3.responsive.CellSpec", Member.Field("iconSize"), GRID_ICON_SIZE),
        contract(DISPLAY_OPTION, Member.Field("iconSizes"), GRID_ICON_SIZE),
        contract(SIZE_SPEC, Member.Field("fixedSize"), GRID_ICON_SIZE, GRID_SPACING),
        contract(SIZE_SPEC, Member.Field("ofAvailableSpace"), GRID_ICON_SIZE, GRID_SPACING),
        contract(SIZE_SPEC, Member.Field("matchWorkspace"), GRID_ICON_SIZE, GRID_SPACING),
        contract(SIZE_SPEC, Member.Field("maxSize"), GRID_ICON_SIZE, GRID_SPACING),
        // Spacing
        contract("$SPECS_PROVIDER\$Companion", Member.Method("create"), GRID_SPACING),
        contract(SPECS_PROVIDER, Member.Field("groupOfSpecs"), GRID_SPACING),
        contract("com.android.launcher3.responsive.ResponsiveSpec", Member.Field("gutter"), GRID_SPACING),
        contract("com.android.launcher3.responsive.ResponsiveSpec", Member.Field("startPadding"), GRID_SPACING),
        contract("com.android.launcher3.responsive.ResponsiveSpec", Member.Field("endPadding"), GRID_SPACING),
        // All apps columns
        contract(DISPLAY_OPTION_SPEC, method("mapTypeIndex", BOOLEAN, BOOLEAN), APP_DRAWER_COLUMNS),
        contract(DISPLAY_OPTION_SPEC, Member.Field("numAllAppsColumns"), APP_DRAWER_COLUMNS),
        contract("com.android.launcher3.InvariantDeviceProfile", method("initGrid", "java.lang.String"), APP_DRAWER_COLUMNS),
        contract("com.android.launcher3.InvariantDeviceProfile", Member.Field("numDatabaseAllAppsColumns"), APP_DRAWER_COLUMNS),
        contract("com.android.launcher3.deviceprofile.AllAppsProfile", Member.Field("numShownAllAppsColumns"), APP_DRAWER_COLUMNS),
        contract("com.android.launcher3.deviceprofile.AllAppsProfile", Member.Field("cellWidthPx"), APP_DRAWER_COLUMNS),
        contract("com.android.launcher3.deviceprofile.AllAppsProfile", Member.Field("borderSpacePx"), APP_DRAWER_COLUMNS),

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
        contract(DEVICE_PROFILE, Member.Field("deviceProperties"), OVERVIEW_ONLY, TASKBAR_ONLY, DOCK),
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
        contract(DEVICE_PROPERTIES, Member.Field("taskbarConfiguration"), TASKBAR_ONLY, DOCK),
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
