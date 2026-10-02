plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "eu.mickaben.shareinspector"
    compileSdk = 35

    defaultConfig {
        applicationId = "eu.mickaben.shareinspector"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}
