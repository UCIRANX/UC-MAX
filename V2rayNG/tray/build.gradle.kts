plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "net.grandcentrix.tray"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        resValue("string", "tray__authority", "com.example.preferences")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
