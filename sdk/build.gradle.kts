plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "2.1.10"
    id("com.android.library")
}

kotlin {
    androidTarget()
    wasmWasi()

    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
        }
    }
}

android {
    namespace = "dev.feather.sdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }
}
