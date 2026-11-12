import java.util.Properties

// Compile-only signatures of framework classes this module calls directly but
// the public SDK leaves out. Never packaged: the device's own classes are used.
plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// The stubs name framework types (ComponentName, Icon and the like), so they compile against the
// platform the app itself compiles against. Compile-only here too: nothing of it is packaged.
val localProperties = Properties()
rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { localProperties.load(it) }
val sdkDir: String = localProperties.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME").orEmpty()

dependencies {
    compileOnly(files(fileTree("$sdkDir/platforms") { include("android-37*/android.jar") }))
}
