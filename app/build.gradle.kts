plugins {
    alias(libs.plugins.agp.app)
}

val appVersion = "0.0.7"

val envKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
val envKeystoreAlias = System.getenv("ANDROID_KEYSTORE_ALIAS")
val envKeystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val envKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")

android {
    namespace = "my.github.MrxSiN.pixellauncherevolved"
    compileSdk = 37

    val releaseSigningConfig = if (
        !envKeystorePath.isNullOrBlank() &&
        !envKeystoreAlias.isNullOrBlank() &&
        !envKeystorePassword.isNullOrBlank() &&
        !envKeyPassword.isNullOrBlank() &&
        file(envKeystorePath).isFile
    ) {
        signingConfigs.create("release") {
            storeFile = file(envKeystorePath)
            storePassword = envKeystorePassword
            keyAlias = envKeystoreAlias
            keyPassword = envKeyPassword
        }
    } else {
        null
    }

    defaultConfig {
        applicationId = "io.github.mrxsin.pixellauncherevolved"
        // The launcher surfaces these features hook into are Android 17 (API 37).
        minSdk = 37
        targetSdk = 37
        versionCode = 7
        versionName = appVersion
    }

    buildTypes {
        release {
            // The entry class is kept by name in proguard-rules.pro because the
            // framework resolves it from META-INF/xposed rather than from any
            // call site.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            releaseSigningConfig?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }

    packaging {
        resources {
            // The Xposed framework discovers modern modules through these files.
            merges += "META-INF/xposed/*"
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "DebugProbesKt.bin",
            )
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("PixelLauncherEvolved-v$appVersion.apk")
        }
    }
}

dependencies {
    // Hook side: provided by the framework at runtime, never packaged.
    compileOnly(libs.libxposed.api)

    // The only runtime dependency left. The settings rows are built from the
    // launcher's own androidx.preference classes by reflection, and the page
    // previews are drawn with plain views, so nothing else is packaged.
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
