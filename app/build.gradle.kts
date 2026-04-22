import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

fun resolveSigningSecret(vararg keys: String): String? {
    return keys.firstNotNullOfOrNull { key ->
        System.getenv(key)?.trim()?.takeIf { it.isNotEmpty() }
    }
}

android {
    namespace = "com.novacut.editor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.novacut.editor"
        minSdk = 26
        targetSdk = 36
        versionCode = 129
        versionName = "3.68.0"
    }

    signingConfigs {
        create("release") {
            val props = Properties()
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.exists()) {
                props.load(propsFile.inputStream())
                val storePath = (props["storeFile"] as? String)?.trim()
                val storePass = (props["storePassword"] as? String)?.trim()
                val alias = (props["keyAlias"] as? String)?.trim()
                val keyPass = (props["keyPassword"] as? String)?.trim()
                if (!storePath.isNullOrBlank() && !storePass.isNullOrBlank() && !alias.isNullOrBlank() && !keyPass.isNullOrBlank()) {
                    storeFile = file(storePath)
                    storePassword = storePass
                    keyAlias = alias
                    keyPassword = keyPass
                }
            } else {
                val storePath = resolveSigningSecret("NOVACUT_STORE_FILE")
                val storePass = resolveSigningSecret("NOVACUT_STORE_PASSWORD", "NOVACUT_KS_PASS")
                val alias = resolveSigningSecret("NOVACUT_KEY_ALIAS")
                val keyPass = resolveSigningSecret("NOVACUT_KEY_PASSWORD", "NOVACUT_KEY_PASS")
                if (!storePath.isNullOrBlank() && !storePass.isNullOrBlank() && !alias.isNullOrBlank() && !keyPass.isNullOrBlank()) {
                    storeFile = file(storePath)
                    storePassword = storePass
                    keyAlias = alias
                    keyPassword = keyPass
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile?.exists() == true) {
                releaseSigning
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Return default values (0/null/false) for un-mocked Android framework methods
    // in plain JVM unit tests instead of throwing `Method X not mocked. See ...`.
    // Matches the pragmatic testing approach used across the engine -- we test
    // pure Kotlin logic on the JVM rather than standing up Robolectric for every
    // small unit test. Instrumentation tests remain the path for anything that
    // legitimately needs the Android runtime.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Workaround: VMware HGFS cannot delete files whose names contain '$' via standard
// Java/Windows APIs (ERROR_INVALID_NAME). Ensure output dirs exist before AGP tasks
// that call FileUtils.deleteDirectoryContents (which asserts isDirectory).
tasks.configureEach {
    if (name.contains("ClassesWithAsm") || name.contains("dexBuilder")) {
        doFirst {
            outputs.files.forEach { output ->
                val targetDir = if (output.extension.isNotEmpty()) output.parentFile else output
                targetDir?.mkdirs()
            }
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Media3 (ExoPlayer + Transformer + Effect)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    implementation(libs.media3.common)
    implementation(libs.media3.ui)
    implementation(libs.media3.muxer)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // WorkManager + Hilt integration
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // ONNX Runtime (Whisper speech-to-text)
    implementation(libs.onnxruntime.android)

    // MediaPipe (selfie segmentation for BG removal)
    implementation(libs.mediapipe.tasks.vision)

    // Tier 2: Lottie animated titles
    implementation(libs.lottie.compose)

    // Tier 4: OkHttp (cloud inpainting API)
    implementation(libs.okhttp)

    testImplementation(libs.junit4)
    testImplementation(libs.org.json)
}
