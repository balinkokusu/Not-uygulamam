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
        versionCode = 97
        versionName = "0.97"
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
