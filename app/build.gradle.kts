plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.omega28.proclimit"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.omega28.proclimit"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
