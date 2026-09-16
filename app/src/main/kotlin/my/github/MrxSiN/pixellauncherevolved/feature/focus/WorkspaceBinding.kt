package my.github.MrxSiN.pixellauncherevolved.feature.focus

import java.lang.reflect.Method

/**
 * The one call that hands the launcher its whole workspace.
 *
 * ```
 * com.android.launcher3.ModelCallbacks
 *   public void bindModelWithAsyncInflation(
 *       WorkspaceData$MutableWorkspaceData, boolean, String)   // CP3A.260905.009
 *   public void bindCompleteModelAsync(
 *       WorkspaceData$MutableWorkspaceData, boolean)           // CP2A.260805.005
 * ```
 *
 * Up to Android 17 `CP2A.260805.005` the loader's binder called
 * `bindCompleteModelAsync`, and that method built the workspace. On
 * `CP3A.260905.009` it is still declared and still called, but its body is a
 * bare `return-void`: the workspace is built by `bindModelWithAsyncInflation`,
 * reached from `Launcher.onCreate`, from a configuration change, and from the
 * home screen repository's `FullRefresh` event, which is what `forceReload`
 * ends in. Hooking the old name there installs cleanly and never runs.
 *
 * The newer method is taken when it exists, and the older one only when it
 * does not, so no build filters one bind twice. Both take the model first,
 * which is the only argument a caller needs to replace.
 */
internal object WorkspaceBinding {

    private const val BIND_WITH_INFLATION = "bindModelWithAsyncInflation"
    private const val BIND_COMPLETE = "bindCompleteModelAsync"

    /** The complete bind on [callbacks], or null when this launcher has neither. */
    fun find(callbacks: Class<*>): Method? =
        callbacks.declared(BIND_WITH_INFLATION, parameters = 3) ?: callbacks.declared(BIND_COMPLETE, parameters = 2)

    private fun Class<*>.declared(name: String, parameters: Int): Method? =
        declaredMethods.firstOrNull { it.name == name && it.parameterTypes.size == parameters }
}
