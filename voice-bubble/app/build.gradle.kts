plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.voicebubble"
    compileSdk = 34
    ndkVersion = "26.3.11579264"

    defaultConfig {
        // Its own app id, so it installs next to Comboed instead of replacing it.
        applicationId = "com.voicebubble"
        minSdk = 26
        targetSdk = 34
        // Climbs every CI build so a newer APK installs over an older one as an update.
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.1"

        // Every phone from the last several years is 64-bit ARM. One ABI keeps the APK small.
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
                // A local whisper.cpp checkout can be used instead of downloading it at build time.
                System.getenv("WHISPER_SRC")?.takeIf { it.isNotBlank() }?.let { arguments += "-DWHISPER_SRC=$it" }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Same idea as Comboed: a fixed key (restored from a GitHub secret in CI) lets new builds
    // install as updates. Without it the normal debug key is used.
    val keystoreFile = file("voice.keystore")
    val hasKeystore = keystoreFile.exists()
    if (hasKeystore) {
        val signPass = System.getenv("SIGNING_PASSWORD").takeUnless { it.isNullOrBlank() } ?: "combopass"
        val signAlias = System.getenv("SIGNING_ALIAS").takeUnless { it.isNullOrBlank() } ?: "combo"
        signingConfigs {
            create("app") {
                storeFile = keystoreFile
                storePassword = signPass
                keyAlias = signAlias
                keyPassword = signPass
            }
        }
    }

    buildTypes {
        debug {
            if (hasKeystore) signingConfig = signingConfigs.getByName("app")
            // The speech engine is far too slow without compiler optimisations.
            externalNativeBuild { cmake { arguments += "-DCMAKE_BUILD_TYPE=Release" } }
        }
        release {
            isMinifyEnabled = false
            if (hasKeystore) signingConfig = signingConfigs.getByName("app")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
