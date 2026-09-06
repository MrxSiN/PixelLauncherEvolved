# The Xposed API is provided by the framework at runtime and is absent from the
# APK, so R8 must not treat the missing superclass as an error.
-dontwarn io.github.libxposed.api.**
-dontwarn io.github.libxposed.annotation.**

# The framework loads the entry class by the name written in this file, and R8
# cannot see the XposedModule hierarchy to infer that it matters.
-keep class my.github.MrxSiN.pixellauncherevolved.PixelLauncherEvolvedModule { *; }
-adaptresourcefilecontents META-INF/xposed/java_init.list
