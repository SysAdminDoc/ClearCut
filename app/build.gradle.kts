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
        versionCode = 294
        versionName = "3.77.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Passive, opt-in update check for sideload / GitHub-release installs.
        // A privacy-store fork (e.g. F-Droid) can override this to `false` to
        // compile the network version check out entirely; the Settings toggle
        // then never appears and UpdateChecker short-circuits to Unavailable.
        buildConfigField("boolean", "UPDATE_CHECK_AVAILABLE", "true")
        buildConfigField("boolean", "LOCAL_NETWORK_STREAMING_ENABLED", "false")
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
        debug {
            isPseudoLocalesEnabled = true
        }
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
        create("streaming") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".streaming"
            versionNameSuffix = "-streaming"
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "LOCAL_NETWORK_STREAMING_ENABLED", "true")
        }
    }

    // The bundled FFmpeg, ONNX Runtime, MediaPipe, and DeepFilterNet native
    // libraries dominate the package: a single universal APK carries every ABI's
    // copy of all four, which is why the published artifact is ~350 MB while a
    // device only ever loads one ABI. Per-ABI APKs cut what a user downloads by
    // roughly three quarters; the universal APK is still produced for anyone who
    // cannot determine their ABI or who sideloads across devices.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
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
        // Independently re-probed on 2026-07-12 with AGP 8.7.3, Kotlin 2.1.21,
        // lifecycle 2.10.0, and Compose BOM 2026.06.00. Each detector throws an
        // IncompatibleClassChangeError while scanning Kotlin sources because its lint jar
        // expects a different Kotlin analysis API. Pass -PcleancutLintProbe=<id>
        // to enable exactly one workaround detector after a dependency upgrade.
        val sourceDetectorCrashWorkarounds = listOf(
            "NullSafeMutableLiveData",
            "FrequentlyChangingValue",
            "FlowOperatorInvokedInComposition",
            "RememberInComposition",
            "AutoboxingStateCreation",
            "UnrememberedMutableState"
        )
        val probeDetector = providers.gradleProperty("cleancutLintProbe").orNull
        require(probeDetector == null || probeDetector in sourceDetectorCrashWorkarounds) {
            "Unknown cleancutLintProbe detector: $probeDetector"
        }
        disable += sourceDetectorCrashWorkarounds.filterNot { it == probeDetector }
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

/**
 * Minimum resolved versions for coordinates with a known applicable advisory.
 * Declared-version pins (the version catalog) do not cover transitives, which is
 * how GHSA-735f-pc8j-v9w8 reached the release graph below MediaPipe. This gate
 * reads the *resolved* graph instead.
 */
val advisoryFloors = mapOf(
    // GHSA-735f-pc8j-v9w8 — malformed-input denial of service.
    "com.google.protobuf:protobuf-javalite" to "4.27.5",
    "com.google.protobuf:protobuf-java" to "4.27.5",
)

fun compareVersions(a: String, b: String): Int {
    val left = a.split('.', '-').mapNotNull { it.toIntOrNull() }
    val right = b.split('.', '-').mapNotNull { it.toIntOrNull() }
    for (i in 0 until maxOf(left.size, right.size)) {
        val diff = (left.getOrElse(i) { 0 }).compareTo(right.getOrElse(i) { 0 })
        if (diff != 0) return diff
    }
    return 0
}

/**
 * Modules whose *resolved* version is policy, not incidental. Declared-version
 * pins in the version catalog say nothing about what conflict resolution
 * actually picked — the Kotlin stdlib resolves well past the Kotlin plugin
 * version, and that drift has to be a deliberate, recorded decision.
 */
val resolvedVersionPolicy = mapOf(
    "org.jetbrains.kotlin:kotlin-stdlib" to "2.2.21",
)

val verifyResolvedAdvisoryFloors by tasks.registering {
    group = "verification"
    description = "Fails on advisory-floor violations, resolved-version drift, or a missing Gradle wrapper checksum."
    val wrapperProperties = rootProject.file("gradle/wrapper/gradle-wrapper.properties")
    val sbomFile = layout.buildDirectory.file("reports/resolved-sbom/cyclonedx.json")
    inputs.file(wrapperProperties)
    outputs.file(sbomFile)
    doLast {
        val resolved = configurations.getByName("releaseRuntimeClasspath")
            .incoming.resolutionResult.allComponents
            .mapNotNull { it.moduleVersion }
            .sortedWith(compareBy({ it.group }, { it.name }, { it.version }))

        val problems = mutableListOf<String>()

        resolved.forEach { module ->
            val coordinate = "${module.group}:${module.name}"
            advisoryFloors[coordinate]?.let { floor ->
                if (compareVersions(module.version, floor) < 0) {
                    problems += "$coordinate:${module.version} is below the advisory floor $floor"
                }
            }
            resolvedVersionPolicy[coordinate]?.let { expected ->
                if (module.version != expected) {
                    problems += "$coordinate resolved to ${module.version} but policy pins $expected; " +
                        "update resolvedVersionPolicy deliberately or fix the drift"
                }
            }
        }

        resolvedVersionPolicy.keys.forEach { coordinate ->
            val present = resolved.any { "${it.group}:${it.name}" == coordinate }
            if (!present) {
                problems += "$coordinate is pinned by resolvedVersionPolicy but is absent from the release graph"
            }
        }

        // The wrapper download is the first supply-chain step of any build, and
        // it runs before every other gate here.
        val wrapperText = if (wrapperProperties.isFile) wrapperProperties.readText() else ""
        val wrapperSha = Regex("""distributionSha256Sum=([0-9a-fA-F]{64})""").find(wrapperText)
        if (wrapperSha == null) {
            problems += "gradle/wrapper/gradle-wrapper.properties is missing a 64-hex distributionSha256Sum"
        }

        // Deterministic resolved SBOM: sorted, no timestamps, so two clean
        // builds of the same commit produce byte-identical output.
        val components = resolved.joinToString(",\n") { module ->
            """    {
      "type": "library",
      "group": "${module.group}",
      "name": "${module.name}",
      "version": "${module.version}",
      "purl": "pkg:maven/${module.group}/${module.name}@${module.version}"
    }"""
        }
        val output = sbomFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            """{
  "bomFormat": "CycloneDX",
  "specVersion": "1.5",
  "metadata": {
    "component": {
      "type": "application",
      "name": "clearcut",
      "version": "${android.defaultConfig.versionName}"
    }
  },
  "components": [
$components
  ]
}
"""
        )

        require(problems.isEmpty()) {
            "Resolved dependency graph failed the supply-chain gate:\n" + problems.joinToString("\n")
        }
        logger.lifecycle("Resolved SBOM written to ${output.relativeTo(rootProject.projectDir)} (${resolved.size} components)")
    }
}

// The public-claim and repository-contract tests read files that live outside any
// compiled source set: the README, the version catalog, the Fastlane listing copy,
// the wrapper pin. Gradle cannot infer those as task inputs, so editing one left the
// test task UP-TO-DATE and the assertion silently never ran -- a claim validator that
// cannot fail. Declare them so a change to public copy re-runs the checks that police it.
tasks.withType<Test>().configureEach {
    listOf(
        "README.md",
        "LICENSE",
        "gradle/libs.versions.toml",
        "gradle/wrapper/gradle-wrapper.properties",
        "fastlane/metadata/android/en-US/full_description.txt",
    ).forEach { relative ->
        val declared = rootProject.file(relative)
        if (declared.isFile) {
            inputs.file(declared)
                .withPropertyName("publicSurface_" + relative.replace('/', '_').replace('.', '_'))
                .withPathSensitivity(PathSensitivity.RELATIVE)
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") {
        dependsOn(generateNativeSbom)
        dependsOn(verifyResolvedAdvisoryFloors)
    }
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
    constraints {
        // MediaPipe tasks-core 0.10.35 still resolves protobuf-javalite 4.26.1, which
        // carries GHSA-735f-pc8j-v9w8 (unbounded recursion on malformed input -> DoS).
        // 4.27.5 is the first fixed release on the 4.27 line and stays gencode-compatible
        // with MediaPipe's 4.26 generated classes (runtime may lead gencode within v4).
        implementation(libs.protobuf.javalite) {
            because("GHSA-735f-pc8j-v9w8: protobuf-javalite < 4.27.5 is vulnerable to a malformed-input DoS")
        }
    }

    // Room 2.8.4's migration bundle serializers are generated against 1.8.1.
    // Lifecycle otherwise constrains Android tests to 1.7.3, which crashes
    // MigrationTestHelper before migrations can run (AbstractMethodError).
    implementation(platform("org.jetbrains.kotlinx:kotlinx-serialization-bom:1.8.1"))
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
