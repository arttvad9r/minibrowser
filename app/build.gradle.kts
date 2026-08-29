plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}
android {
    namespace = "com.artt.minibrowser"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.artt.minibrowser"
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 1
        versionName = "0.1"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes {
        debug {
            // arm64 для телефона + x86_64 для локального/CI emulator smoke test.
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        }
        release {
            // Личное устройство arm64; не кладём остальные GeckoView ABI в release APK.
            ndk { abiFilters += "arm64-v8a" }
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    // Дефолтный паттерн AAPT вырезает каталоги, начинающиеся с "_"
    // (<dir>_*), из-за чего из APK пропадает _locales расширений.
    androidResources {
        ignoreAssetsPattern =
            "!.svn:!.git:!.gitignore:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}
dependencies {
    implementation("org.mozilla.geckoview:geckoview:154.0.20260814215756")
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.10")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
