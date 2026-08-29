plugins {
    id("com.android.test")
}

android {
    namespace = "com.artt.minibrowser.benchmark"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    // Macrobenchmark must run in a process separate from the measured browser process.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("androidx.benchmark:benchmark-macro-junit4:1.4.1")
    implementation("androidx.test:core:1.6.1")
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test:runner:1.6.2")
    implementation("androidx.test.uiautomator:uiautomator:2.4.0")
}
