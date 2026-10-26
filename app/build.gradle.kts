plugins {
    id("com.android.application")
}

android {
    namespace = "com.leslie.tvhome"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.leslie.tvhome"
        minSdk = 28
        targetSdk = 31
        versionCode = 16
        versionName = "2.0.10"
    }

    // standard declares itself a home screen. guard does not: TCL's package verifier refuses any
    // APK that declares HOME unless TCL signed it, so there Tigris is opened as an app and kept in
    // front by the home guard (Guard.kt).
    flavorDimensions += "home"
    productFlavors {
        create("standard") { dimension = "home" }
        create("guard") { dimension = "home" }
    }

    signingConfigs {
        if (System.getenv("TIGRIS_KEYSTORE") != null) {
            create("stable") {
                storeFile = file(System.getenv("TIGRIS_KEYSTORE"))
                storePassword = System.getenv("TIGRIS_STORE_PASSWORD")
                keyAlias = System.getenv("TIGRIS_KEY_ALIAS")
                keyPassword = System.getenv("TIGRIS_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("stable") ?: signingConfigs.getByName("debug")
        }
    }

    lint {
        // Sideloaded onto one TV, never published to Play.
        disable += "ExpiredTargetSdkVersion"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.palette:palette:1.0.0")
    implementation("dev.mobile:dadb:2.0.0")
    // Apple TV aerial screensaver.
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    // The page a phone opens from the QR code (wallpapers, uploads).
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.google.zxing:core:3.5.3")
}
