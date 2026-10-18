plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "es.costalceleste.tareas"
    compileSdk = 35

    defaultConfig {
        applicationId = "es.costalceleste.tareas"
        minSdk = 26
        targetSdk = 35
        versionCode = runNumber
        versionName = "1.0.$runNumber"
    }

    // Misma firma siempre: así cada nueva versión se instala encima sin perder las tareas.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/tareas.keystore")
            storePassword = "despacho"
            keyAlias = "despacho"
            keyPassword = "despacho"
        }
    }

    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("debug") }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
}
