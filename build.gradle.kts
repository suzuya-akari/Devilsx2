plugins {
    id("com.android.application") version "8.5.2"
    id("org.jetbrains.kotlin.android") version "1.9.24"
}

// Build output goes outside the repo so the "." source folder below never picks up generated files.
layout.buildDirectory.set(file("${rootDir}/../dmc3-out"))

android {
    namespace = "com.example.dmc3launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.dmc3launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.setSrcDirs(listOf("."))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
