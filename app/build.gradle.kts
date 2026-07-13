import java.util.Properties
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.baselineprofile)
}

fun resolveSigningSecret(vararg keys: String): String? {
    return keys.firstNotNullOfOrNull { key ->
        System.getenv(key)?.trim()?.takeIf { it.isNotEmpty() }
    }
}

configurations.configureEach {
    // Dagger 2.58's lint AAR crashes AGP 8.7.3 lint jar migration with an
    // ASM NegativeArraySizeException before ClearCut findings are reported.
    exclude(group = "com.google.dagger", module = "dagger-lint-aar")
}

android {
    namespace = "com.novacut.editor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.novacut.editor"
        minSdk = 26
        targetSdk = 36
        versionCode = 263
        versionName = "3.74.130"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Passive, opt-in update check for sideload / GitHub-release installs.
        // A privacy-store fork (e.g. F-Droid) can override this to `false` to
        // compile the network version check out entirely; the Settings toggle
        // then never appears and UpdateChecker short-circuits to Unavailable.
        buildConfigField("boolean", "UPDATE_CHECK_AVAILABLE", "true")
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
                    storeFile = rootProject.file(storePath)
                    storePassword = storePass
                    keyAlias = alias
                    keyPassword = keyPass
                }
            } else {
                val storePath = resolveSigningSecret("CLEARCUT_STORE_FILE")
                val storePass = resolveSigningSecret("CLEARCUT_STORE_PASSWORD", "CLEARCUT_KS_PASS")
                val alias = resolveSigningSecret("CLEARCUT_KEY_ALIAS")
                val keyPass = resolveSigningSecret("CLEARCUT_KEY_PASSWORD", "CLEARCUT_KEY_PASS")
                if (!storePath.isNullOrBlank() && !storePass.isNullOrBlank() && !alias.isNullOrBlank() && !keyPass.isNullOrBlank()) {
                    storeFile = rootProject.file(storePath)
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

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    lint {
        // androidx.lifecycle's NullSafeMutableLiveData detector crashes under
        // the current Kotlin 2.1 / AGP 8.7 lint stack, and ClearCut has no
        // LiveData call sites for this detector to inspect. The Compose
        // detectors below currently crash in UAST with the same stack rather
        // than reporting actionable source findings.
        disable += listOf(
            "NullSafeMutableLiveData",
            "FrequentlyChangingValue",
            "FlowOperatorInvokedInComposition",
            "RememberInComposition",
            "AutoboxingStateCreation",
            "UnrememberedMutableState"
        )
        baseline = file("lint-baseline.xml")
        abortOnError = true
        htmlReport = true
        sarifReport = true
    }
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_metrics")
    metricsDestination = layout.buildDirectory.dir("compose_metrics")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

val ffmpegKitAar = rootProject.file("third_party/ffmpeg-kit-next/ffmpeg-kit-next-8.1.0.aar")
val ffmpegKitAarSha256 = "4b7654925340bb4a5eb0c4e50350a6f664f4568a228d46e9e128eb032406fd00"

fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val verifyFfmpegKitAar by tasks.registering {
    group = "verification"
    description = "Verifies the source-pinned FFmpegKitNext AAR before compilation."
    inputs.file(ffmpegKitAar)
    doLast {
        require(ffmpegKitAar.isFile) { "Missing vendored FFmpegKitNext AAR: $ffmpegKitAar" }
        val actual = ffmpegKitAar.sha256()
        require(actual == ffmpegKitAarSha256) {
            "FFmpegKitNext AAR checksum mismatch: expected=$ffmpegKitAarSha256 actual=$actual"
        }
    }
}

tasks.named("preBuild").configure { dependsOn(verifyFfmpegKitAar) }

val generateNativeSbom by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates native dependency provenance/advisories and writes deterministic SBOMs."
    workingDir(rootProject.projectDir)
    commandLine("python", "scripts/verify_native_supply_chain.py")
    inputs.file(rootProject.file("third_party/ffmpeg-kit-next/native-lock.json"))
    inputs.file(ffmpegKitAar)
    outputs.files(
        layout.buildDirectory.file("reports/native-sbom/cyclonedx.json"),
        layout.buildDirectory.file("reports/native-sbom/spdx.json"),
    )
}

tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn(generateNativeSbom)
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
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.window)
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
    implementation(libs.media3.effect.lottie)
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
    implementation(libs.androidx.profileinstaller)

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // ONNX Runtime (Whisper speech-to-text)
    implementation(libs.onnxruntime.android)

    // MediaPipe (selfie segmentation for BG removal)
    implementation(libs.mediapipe.tasks.vision)

    // Tier 2: Lottie animated titles
    implementation(libs.lottie.compose)

    // Source-pinned GPL build: FFmpegKitNext 8.1.0 / FFmpeg 8.1.2, five Android ABIs.
    implementation(files(ffmpegKitAar))
    implementation("com.arthenica:smart-exception-java:0.2.1")

    // Tier A.2 / R6.6: DeepFilterNet Android noise reduction
    implementation(libs.android.deepfilternet)

    // Tier 4: OkHttp (cloud inpainting API)
    implementation(libs.okhttp)

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)

    baselineProfile(project(":baselineprofile"))
}

baselineProfile {
    automaticGenerationDuringBuild = false
}
