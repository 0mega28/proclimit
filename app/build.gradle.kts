plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.omega28.proclimit"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.omega28.proclimit"
        minSdk = 33
        targetSdk = 37
        versionCode = 3
        versionName = "1.2"
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

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
}
