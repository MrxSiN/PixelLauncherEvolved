package my.github.MrxSiN.pixellauncherevolved.bridge

/**
 * How Wallpaper & style asks the launcher about icon packs.
 *
 * The icon pack choice lives in Wallpaper & style, beside the launcher's own
 * Icons and Layout options, but the setting, the pack index and the icon cache
 * belong to the launcher, and Wallpaper & style cannot even see most icon
 * packs: its package visibility is a short list. So the picker asks, and the
 * launcher answers, over the provider the picker already uses for the home
 * screen preview and the app grid (`grid_control`): this module adds its own
 * `call` methods to it.
 *
 * Every picture crosses as PNG bytes rendered by the launcher's own icon
 * factory, so a tile in the picker is shaped exactly as the home screen draws
 * that icon. Answers are kept small (a page of apps, a page of drawables),
 * well under a Binder transaction.
 *
 * Only Wallpaper & style, the launcher itself and the shell may call these
 * ([callerAllowed] in the launcher).
 */
object IconsBridge {

    const val PICKER_PACKAGE: String = "com.google.android.apps.wallpaper"
    const val AUTHORITY: String = "com.google.android.apps.nexuslauncher.grid_control"

    /** Every method this module adds starts with this, and no other is touched. */
    const val PREFIX: String = "ple_icons_"

    /** Source, chosen pack, and every installed pack with its tile. */
    const val STATE: String = "ple_icons_state"

    /** Makes `arg` the icon source: a pack's package, or empty for System. Answers once icons are redrawn. */
    const val APPLY: String = "ple_icons_apply"

    /** Every launchable app: labels and keys, no pictures. */
    const val APPS: String = "ple_icons_apps"

    /** Pictures of the apps whose keys are in [KEYS], as they are drawn now. */
    const val APP_ICONS: String = "ple_icons_app_icons"

    /** Drawables of the chosen pack matching `arg`, a page at a time, with pictures. */
    const val DRAWABLES: String = "ple_icons_drawables"

    /** Gives the app [KEY] the icon [CHOICE]: [CHOICE_PACK], [CHOICE_SYSTEM] or a drawable name. */
    const val OVERRIDE: String = "ple_icons_override"

    /** Gives every app back to the pack. */
    const val RESET: String = "ple_icons_reset"

    const val USES_PACK = "uses_pack"
    const val PACK = "pack"
    const val PACK_LABEL = "pack_label"
    const val PACKAGES = "packages"
    const val LABELS = "labels"
    const val TILE_PREFIX = "tile:"
    const val OVERRIDES = "overrides"
    const val AVAILABLE = "available"

    const val KEYS = "keys"
    const val KEY = "key"
    const val CHOICES = "choices"
    const val ICON_PREFIX = "icon:"
    const val NAMES = "names"
    const val OFFSET = "offset"
    const val CHOICE = "choice"
    const val CHOICE_PACK = ""
    const val CHOICE_SYSTEM = "\u0000system"
    const val OK = "ok"

    /** Home's resting wallpaper blur, in full-screen pixels; 0 when it is off. */
    const val BLUR_RADIUS = "blur_radius"

    /**
     * Put in a `get_preview_bitmap` request: the pack the preview draws its icons
     * from, without choosing it; empty for System.
     */
    const val PREVIEW_PACK = "ple_icons_preview_pack"

    /** How many pictures one answer carries. */
    const val PAGE = 24

    /** Whether the caller of a `grid_control` call is Wallpaper & style, the launcher itself or the shell. */
    fun callerAllowed(context: android.content.Context): Boolean {
        val uid = android.os.Binder.getCallingUid()
        if (uid == android.os.Process.myUid() || uid == android.os.Process.SHELL_UID || uid == android.os.Process.ROOT_UID) return true
        return context.packageManager.getPackagesForUid(uid)?.contains(PICKER_PACKAGE) == true
    }

    /** One app in one profile, as the picker names it: `user|package/class`. */
    fun keyOf(packageName: String, className: String, userId: Int): String = "$userId|$packageName/$className"
}
