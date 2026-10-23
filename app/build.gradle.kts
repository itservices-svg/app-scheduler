plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.appscheduler"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.appscheduler"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "0.4"
    }

    // Har build par wohi key use hoti hai, taake nayi APK purani ke upar install ho
    // (aur permissions bani rahein). Ye sirf prototype ke liye hai, Play Store release key nahi.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
