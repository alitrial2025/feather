plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.feather.host"
    compileSdk = 36
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "dev.feather.host"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionName = "1.0"
        val bundleKey = providers.gradleProperty("featherBundlePublicKey").orElse("").get()
        require(bundleKey.isEmpty() || Regex("[A-Za-z0-9+/]{43}=").matches(bundleKey)) {
            "featherBundlePublicKey must be a base64 raw Ed25519 public key"
        }
        resValue("string", "feather_bundle_public_key", bundleKey)

        ndk {
            abiFilters.addAll(providers.gradleProperty("featherAbis").orElse("arm64-v8a").get().split(','))
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
            providers.gradleProperty("featherJniLibs").orNull?.let { jniLibs.srcDir(it) }
            assets.srcDir(layout.buildDirectory.dir("generated/runtime-assets"))
        }
    }


    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("net.java.dev.jna:jna:5.19.1@aar")
    implementation("com.google.flatbuffers:flatbuffers-java:24.3.25")
    implementation("org.msgpack:jackson-dataformat-msgpack:0.9.8")

    // CameraX
    val cameraxVersion = "1.4.2"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
}

// Keep bundle output outside tracked sources and remove stale staged assets when
// switching back to a development host without a packaged application.
val runtimeBundle = providers.gradleProperty("featherBundleDir")
val runtimeAssets = layout.buildDirectory.dir("generated/runtime-assets")
val stageRuntimeBundle = tasks.register("stageRuntimeBundle") {
    inputs.property("bundlePath", runtimeBundle.orElse(""))
    if (runtimeBundle.isPresent) inputs.dir(runtimeBundle.get())
    outputs.dir(runtimeAssets)
    doLast {
        delete(runtimeAssets)
        runtimeBundle.orNull?.let { path ->
            require(file(path).resolve("manifest.json").isFile) { "featherBundleDir must contain a signed manifest.json" }
            copy { from(path); into(runtimeAssets.get().dir("feather")) }
        }
    }
}
tasks.named("preBuild").configure { dependsOn(stageRuntimeBundle) }
