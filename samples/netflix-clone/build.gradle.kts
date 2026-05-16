plugins {
    kotlin("multiplatform") version "2.1.0"
}

repositories {
    mavenCentral()
}

kotlin {
    wasmWasi {
        nodejs()
        binaries.executable()
    }
    sourceSets {
        val wasmWasiMain by getting {
            dependencies {
                implementation(project(":sdk"))
            }
        }
    }
}
