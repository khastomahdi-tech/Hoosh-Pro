plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hooshpro.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hooshpro.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("hoosh") {
            storeFile = file("hoosh.keystore")
            storePassword = "dgDemon2026"
            keyAlias = "dg"
            keyPassword = "dgDemon2026"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("hoosh") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("hoosh")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
}
