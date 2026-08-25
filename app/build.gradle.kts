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
        versionCode = 1
        versionName = "0.1"
    }
    buildFeatures { compose = true }
    // Релиз подписываем debug-ключом: ставится поверх debug-сборки на личном телефоне.
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
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
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.10")
}
