import java.security.SecureRandom
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// A fresh random hash per build, like desktop's -X main.buildHash: "сборка a1b2c3d4" on the About page.
val buildHash: String = SecureRandom().let { rnd -> ByteArray(4).also(rnd::nextBytes).joinToString("") { "%02x".format(it) } }

android {
    namespace = "com.beamvpn.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.beamvpn.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        // Closed-beta builds: `gradlew assembleDebug -PbetaTag=beta` gives "1.0.0-beta". No tag, a stable build.
        (findProperty("betaTag") as String?)?.takeIf { it.isNotBlank() }?.let { versionNameSuffix = "-$it" }
        buildConfigField("String", "BUILD_HASH", "\"$buildHash\"")
    }

    // Release signing comes from keystore.properties next to the project (never committed):
    //   storeFile=/path/to/beam.jks
    //   storePassword=...
    //   keyAlias=...
    //   keyPassword=...
    // Without it the release APK is built unsigned.
    val keystore = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { file ->
        Properties().apply { file.inputStream().use { load(it) } }
    }
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
        }
        // A release build (R8, no debuggable flag, baseline profile) signed with the debug key: installs over the
        // debug APK for testing real-world speed. A debug build runs Compose several times slower, which on a
        // mid-range phone reads as an app that lags.
        create("preview") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    // One APK per architecture: the Go core is most of the size, and a phone needs only its own.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // beam-bridge AAR from `gomobile bind` (see README). Optional until it is built.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    // QR scanner: CameraX frames decoded by ZXing (no Google Play services needed).
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)
    debugImplementation(libs.androidx.ui.tooling)
}
