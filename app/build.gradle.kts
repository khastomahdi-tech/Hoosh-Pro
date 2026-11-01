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
        versionCode = 2
        versionName = "1.1"
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
    // ورودی صوتی — تشخیص گفتار آفلاین فارسی (Vosk)
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    // تحلیل عکس روی خود دستگاه — OCR لاتین + برچسب‌گذاری تصویر (آفلاین)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:image-labeling:17.0.9")
    // OCR فارسی روی خود دستگاه (Tesseract — مدل در اولین استفاده دانلود می‌شود)
    implementation("com.rmtheis:tess-two:9.0.0")
}
