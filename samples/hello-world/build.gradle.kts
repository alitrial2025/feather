plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("com.google.devtools.ksp")
}

kotlin {
    androidTarget()

    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            implementation(project(":sdk"))
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", project(":compiler:ksp"))
}

android {
    namespace = "com.example.helloworld"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }
}
