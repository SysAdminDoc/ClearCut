plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.clearcut.baselineprofile"
    compileSdk = 37
    targetProjectPath = ":app"

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions.managedDevices {
        localDevices {
            create("pixel6Api37") {
                device = "Pixel 6"
                apiLevel = 37
                systemImageSource = "google"
            }
        }
    }
}

baselineProfile {
    managedDevices += "pixel6Api37"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.benchmark.junit4)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.uiautomator)
}
