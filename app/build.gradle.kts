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

    // Release signing comes from the environment (CI secrets); without it, release builds are unsigned.
    val keystorePath = System.getenv("PROCLIMIT_KEYSTORE")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("PROCLIMIT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PROCLIMIT_KEY_ALIAS")
                keyPassword = System.getenv("PROCLIMIT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
