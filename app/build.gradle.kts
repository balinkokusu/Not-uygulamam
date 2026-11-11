plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.notuygulamam.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.notuygulamam.app"
        minSdk = 24
        targetSdk = 34
        // yalnız 64 bit ARM (MatePad dahil güncel tabletler): ses tanıma kütüphanesinin diğer işlemci kopyaları gereksiz yer kaplamasın
        ndk { abiFilters += listOf("arm64-v8a") }
        versionCode = 121
        versionName = "1.21"
    }

    // Her derlemede aynı anahtarla imzalanır; böylece yeni sürüm eskisinin üstüne güncelleme olarak kurulur.
    signingConfigs {
        getByName("debug") {
            storeFile = file("not-uygulamam.jks")
            storePassword = "notuygulamam"
            keyAlias = "not"
            keyPassword = "notuygulamam"
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

dependencies {
    // Ses kaydını cihazda yazıya çevirmek için (Vosk); model ilk kullanımda indirilir
    implementation("com.alphacephei:vosk-android:0.3.32+")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
}
