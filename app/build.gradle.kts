import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.util.Properties
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Locale
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.android.application)
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

val runtimeNoticeGeneratedDir = layout.buildDirectory.dir("generated/source/runtimeNotices")

android {
    namespace = "com.novacut.editor"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.novacut.editor"
        minSdk = 26
        targetSdk = 37
        versionCode = 296
        versionName = "3.78.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testBuildType = "qa"

        // Passive, opt-in update check for sideload / GitHub-release installs.
        // A privacy-store fork (e.g. F-Droid) can override this to `false` to
        // compile the network version check out entirely; the Settings toggle
        // then never appears and UpdateChecker short-circuits to Unavailable.
        buildConfigField("boolean", "UPDATE_CHECK_AVAILABLE", "true")
        buildConfigField("boolean", "LOCAL_NETWORK_STREAMING_ENABLED", "false")
        buildConfigField("boolean", "QA_TIMELINE_HARNESS_ENABLED", "false")
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
            // Never fall back to the debug key. Android refuses an in-place update
            // when the signing certificate changes, and ClearCut's projects live in
            // app-private storage -- signing a release with a different key strands
            // every installed user with no migration path. The release build fails
            // loudly instead, and `verifyReleaseSigningIdentity` proves the resolved
            // key is the one every published release already carries.
            signingConfig = signingConfigs.getByName("release")
        }
        create("streaming") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".streaming"
            versionNameSuffix = "-streaming"
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "LOCAL_NETWORK_STREAMING_ENABLED", "true")
        }
        create("qa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-qa"
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "QA_TIMELINE_HARNESS_ENABLED", "true")
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
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        getByName("main").java.directories.add(runtimeNoticeGeneratedDir.get().asFile.absolutePath)
        getByName("androidTest").assets.directories += "$projectDir/schemas"
    }

    lint {
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
val ffmpegKitAarSha256 = "7971240aff84ce59a4ab28400bb4af59d24c20ce68c25525d41d910246ccff62"

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

data class RuntimeLicenseInfo(
    val licenseName: String,
    val licenseText: String,
    val licenseUrl: String,
    val projectUrl: String,
)

data class RuntimeLicensePolicy(
    val groupPrefixes: List<String>,
    val license: RuntimeLicenseInfo,
)

data class GeneratedRuntimeNotice(
    val name: String,
    val version: String,
    val artifact: String,
    val license: RuntimeLicenseInfo,
    val sourceOfferText: String? = null,
    val complianceNote: String? = null,
    val origin: String,
)

val apacheLicense = RuntimeLicenseInfo(
    licenseName = "Apache License 2.0",
    licenseText = "Apache License 2.0. Redistribution must preserve the license and required notices; the software is provided without warranties under the Apache terms.",
    licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0.txt",
    projectUrl = "https://www.apache.org/",
)
val mitLicense = RuntimeLicenseInfo(
    licenseName = "MIT License",
    licenseText = "MIT License. Redistribution must keep the copyright and permission notices; the software is provided without warranties.",
    licenseUrl = "https://opensource.org/license/mit/",
    projectUrl = "https://opensource.org/license/mit/",
)
val bsd2License = RuntimeLicenseInfo(
    licenseName = "BSD 2-Clause License",
    licenseText = "BSD 2-Clause License. Redistribution must preserve the copyright notice, conditions, and disclaimer.",
    licenseUrl = "https://opensource.org/license/bsd-2-clause/",
    projectUrl = "https://opensource.org/license/bsd-2-clause/",
)
val bsd3License = RuntimeLicenseInfo(
    licenseName = "BSD 3-Clause License",
    licenseText = "BSD 3-Clause License. Redistribution must preserve the copyright notice, conditions, and disclaimer.",
    licenseUrl = "https://opensource.org/license/bsd-3-clause/",
    projectUrl = "https://opensource.org/license/bsd-3-clause/",
)
val iscLicense = RuntimeLicenseInfo(
    licenseName = "ISC License",
    licenseText = "ISC License. Permission to use, copy, modify, and distribute is granted subject to the included copyright and disclaimer.",
    licenseUrl = "https://opensource.org/license/isc-license-txt/",
    projectUrl = "https://opensource.org/license/isc-license-txt/",
)
val lgpl3License = RuntimeLicenseInfo(
    licenseName = "GNU Lesser General Public License Version 3",
    licenseText = "GNU LGPL v3. Redistribution must preserve the license and corresponding notices.",
    licenseUrl = "https://www.gnu.org/licenses/lgpl-3.0.txt",
    projectUrl = "https://www.gnu.org/licenses/lgpl-3.0.html",
)
val lgpl21License = RuntimeLicenseInfo(
    licenseName = "GNU Lesser General Public License Version 2.1 or later",
    licenseText = "GNU LGPL v2.1 or later. Redistribution must preserve the license and corresponding notices.",
    licenseUrl = "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt",
    projectUrl = "https://www.gnu.org/licenses/lgpl-2.1.html",
)
val freetypeLicense = RuntimeLicenseInfo(
    licenseName = "FreeType License",
    licenseText = "FreeType License. Redistribution must preserve the license and its notices.",
    licenseUrl = "https://freetype.org/license.html",
    projectUrl = "https://freetype.org/",
)
val libpngLicense = RuntimeLicenseInfo(
    licenseName = "libpng License",
    licenseText = "libpng License. Redistribution must preserve the copyright notice and disclaimer.",
    licenseUrl = "http://www.libpng.org/pub/png/src/libpng-LICENSE.txt",
    projectUrl = "http://www.libpng.org/pub/png/libpng.html",
)
val llvmExceptionLicense = RuntimeLicenseInfo(
    licenseName = "Apache License 2.0 with LLVM exception",
    licenseText = "Apache License 2.0 with the LLVM exception. Redistribution must preserve the license and exception text.",
    licenseUrl = "https://llvm.org/LICENSE.txt",
    projectUrl = "https://llvm.org/",
)

fun RuntimeLicenseInfo.forProject(projectUrl: String): RuntimeLicenseInfo = copy(projectUrl = projectUrl)

val runtimeLicensePolicies = listOf(
    RuntimeLicensePolicy(listOf("com.google.protobuf"), bsd3License),
    RuntimeLicensePolicy(listOf("com.google.code.findbugs"), bsd3License),
    RuntimeLicensePolicy(listOf("com.microsoft.onnxruntime"), mitLicense.forProject("https://onnxruntime.ai/")),
    RuntimeLicensePolicy(listOf("org.checkerframework"), mitLicense.forProject("https://checkerframework.org/")),
    RuntimeLicensePolicy(listOf("androidx"), apacheLicense.forProject("https://developer.android.com/jetpack")),
    RuntimeLicensePolicy(listOf("org.jetbrains.androidx"), apacheLicense.forProject("https://github.com/JetBrains")),
    RuntimeLicensePolicy(listOf("org.jetbrains.compose"), apacheLicense.forProject("https://github.com/JetBrains/compose-multiplatform")),
    RuntimeLicensePolicy(listOf("org.jetbrains.kotlinx"), apacheLicense.forProject("https://github.com/Kotlin")),
    RuntimeLicensePolicy(listOf("org.jetbrains.kotlin"), apacheLicense.forProject("https://kotlinlang.org/")),
    RuntimeLicensePolicy(listOf("org.jetbrains"), apacheLicense.forProject("https://github.com/JetBrains")),
    RuntimeLicensePolicy(listOf("com.airbnb.android"), apacheLicense.forProject("https://github.com/airbnb/lottie-android")),
    RuntimeLicensePolicy(listOf("com.arthenica"), apacheLicense.forProject("https://github.com/arthenica/ffmpeg-kit")),
    RuntimeLicensePolicy(listOf("com.google.dagger"), apacheLicense.forProject("https://dagger.dev/")),
    RuntimeLicensePolicy(listOf("com.google.mediapipe"), apacheLicense.forProject("https://mediapipe.dev/")),
    RuntimeLicensePolicy(listOf("com.google.firebase"), apacheLicense.forProject("https://firebase.google.com/")),
    RuntimeLicensePolicy(listOf("com.google.android.datatransport"), apacheLicense.forProject("https://github.com/firebase/firebase-android-sdk")),
    RuntimeLicensePolicy(listOf("com.google.flogger"), apacheLicense.forProject("https://google.github.io/flogger/")),
    RuntimeLicensePolicy(listOf("com.google.guava"), apacheLicense.forProject("https://github.com/google/guava")),
    RuntimeLicensePolicy(listOf("com.google.j2objc"), apacheLicense.forProject("https://github.com/google/j2objc")),
    RuntimeLicensePolicy(listOf("com.google.errorprone"), apacheLicense.forProject("https://errorprone.info/")),
    RuntimeLicensePolicy(listOf("com.google"), apacheLicense.forProject("https://developers.google.com/")),
    RuntimeLicensePolicy(listOf("com.squareup.okhttp3"), apacheLicense.forProject("https://square.github.io/okhttp/")),
    RuntimeLicensePolicy(listOf("com.squareup.okio"), apacheLicense.forProject("https://square.github.io/okio/")),
    RuntimeLicensePolicy(listOf("io.coil-kt.coil3"), apacheLicense.forProject("https://github.com/coil-kt/coil")),
    RuntimeLicensePolicy(listOf("io.github.kaleyravideo"), apacheLicense.forProject("https://github.com/KaleyraVideo/AndroidDeepFilterNet")),
    RuntimeLicensePolicy(listOf("jakarta.inject"), apacheLicense.forProject("https://github.com/jakartaee/inject")),
    RuntimeLicensePolicy(listOf("javax.inject"), apacheLicense.forProject("https://github.com/javax-inject/javax-inject")),
    RuntimeLicensePolicy(listOf("org.jspecify"), apacheLicense.forProject("https://jspecify.dev/")),
)

fun runtimeLicenseFor(group: String): RuntimeLicenseInfo? = runtimeLicensePolicies
    .asSequence()
    .filter { policy ->
        policy.groupPrefixes.any { prefix -> group == prefix || group.startsWith("$prefix.") }
    }
    .maxByOrNull { policy -> policy.groupPrefixes.maxOf { it.length } }
    ?.license

fun nativeLicenseFor(expression: String): RuntimeLicenseInfo = when (expression) {
    "Apache-2.0" -> apacheLicense
    "Apache-2.0-with-LLVM-exception" -> llvmExceptionLicense
    "BSD-2-Clause" -> bsd2License
    "BSD-3-Clause" -> bsd3License
    "FTL" -> freetypeLicense
    "ISC" -> iscLicense
    "LGPL-2.1-or-later" -> lgpl21License
    "LGPL-3.0-or-later" -> lgpl3License
    "Libpng" -> libpngLicense
    "MIT" -> mitLicense
    else -> error("No native notice license mapping for $expression")
}

fun String.kotlinLiteral(): String = buildString {
    append('"')
    for (character in this@kotlinLiteral) {
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
    append('"')
}

fun GeneratedRuntimeNotice.toKotlin(): String = buildString {
    appendLine("        OpenSourceLicenseNotice(")
    appendLine("            name = ${name.kotlinLiteral()},")
    appendLine("            version = ${version.kotlinLiteral()},")
    appendLine("            artifact = ${artifact.kotlinLiteral()},")
    appendLine("            licenseName = ${license.licenseName.kotlinLiteral()},")
    appendLine("            licenseText = ${license.licenseText.kotlinLiteral()},")
    appendLine("            licenseUrl = ${license.licenseUrl.kotlinLiteral()},")
    appendLine("            projectUrl = ${license.projectUrl.kotlinLiteral()},")
    appendLine("            sourceOfferText = ${sourceOfferText?.kotlinLiteral() ?: "null"},")
    appendLine("            complianceNote = ${complianceNote?.kotlinLiteral() ?: "null"},")
    appendLine("        ),")
}

fun nativeProjectUrl(purl: String): String {
    val githubPath = purl.removePrefix("pkg:github/").substringBefore('@')
    return if (githubPath != purl && githubPath.contains('/')) {
        "https://github.com/$githubPath"
    } else if (purl.contains("ffmpeg")) {
        "https://ffmpeg.org/"
    } else {
        "https://developer.android.com/ndk"
    }
}

val generateRuntimeOpenSourceNotices by tasks.registering {
    group = "verification"
    description = "Generates a complete, resolved-runtime open-source notice inventory."
    val registryFile = rootProject.file("scripts/capability_registry.json")
    val nativeLockFile = rootProject.file("third_party/ffmpeg-kit-next/native-lock.json")
    val generatedFile = runtimeNoticeGeneratedDir.map { it.file("RuntimeOpenSourceLicensesGenerated.kt") }
    val reportFile = layout.buildDirectory.file("reports/runtime-notices/runtime-notices.json")
    inputs.files(
        registryFile,
        nativeLockFile,
        rootProject.file("gradle/libs.versions.toml"),
        project.buildFile,
    )
    outputs.dir(runtimeNoticeGeneratedDir)
    outputs.file(reportFile)
    doLast {
        val resolved = configurations.getByName("releaseRuntimeClasspath")
            .incoming.resolutionResult.allComponents
            .mapNotNull { component -> component.moduleVersion }
            .filterNot { module -> module.group == rootProject.name && module.name == project.name }
            .distinctBy { module -> "${module.group}:${module.name}:${module.version}" }
            .sortedWith(compareBy({ it.group }, { it.name }, { it.version }))

        val unresolvedGroups = resolved
            .map { it.group }
            .distinct()
            .filter { group -> runtimeLicenseFor(group) == null }
        require(unresolvedGroups.isEmpty()) {
            "Runtime notice policy has no license mapping for groups: ${unresolvedGroups.joinToString(", ")}"
        }

        val registry = JsonSlurper().parse(registryFile) as Map<*, *>
        val registryDependencies = registry["dependencies"] as? List<*> ?: error("Capability registry has no dependencies list")
        registryDependencies.forEach { rawDependency ->
            val dependency = rawDependency as? Map<*, *> ?: error("Capability registry dependency is not an object")
            val notice = dependency["notice"] as? Map<*, *> ?: return@forEach
            val coordinate = dependency["coordinate"] as? String ?: error("Notice dependency has no coordinate")
            val coordinateParts = coordinate.split(':')
            if (coordinateParts.size == 2) {
                val resolvedModule = resolved.firstOrNull {
                    it.group == coordinateParts[0] && it.name == coordinateParts[1]
                } ?: error("Curated notice $coordinate is absent from releaseRuntimeClasspath")
                val declaredVersion = dependency["version"] as? String ?: error("Notice dependency $coordinate has no version")
                require(resolvedModule.version == declaredVersion) {
                    "Curated notice $coordinate is stale: registry=$declaredVersion resolved=${resolvedModule.version}"
                }
                require(notice["licenseUrl"] is String && (notice["licenseUrl"] as String).isNotBlank()) {
                    "Curated notice $coordinate has no license URL"
                }
            }
        }

        val runtimeNotices = resolved.map { module ->
            val coordinate = "${module.group}:${module.name}"
            GeneratedRuntimeNotice(
                name = coordinate,
                version = module.version,
                artifact = coordinate,
                license = requireNotNull(runtimeLicenseFor(module.group)),
                origin = "releaseRuntimeClasspath",
            )
        }

        val nativeLock = JsonSlurper().parse(nativeLockFile) as Map<*, *>
        val nativeComponents = nativeLock["components"] as? List<*> ?: error("Native lock has no components list")
        val nativeNotices = nativeComponents.map { rawComponent ->
            val component = rawComponent as? Map<*, *> ?: error("Native lock component is not an object")
            val name = component["name"] as? String ?: error("Native lock component has no name")
            val version = component["version"] as? String ?: error("Native lock component $name has no version")
            val purl = component["purl"] as? String ?: error("Native lock component $name has no purl")
            val licenseExpression = component["license"] as? String ?: error("Native lock component $name has no license")
            val artifact = "native:" + name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')
            GeneratedRuntimeNotice(
                name = name,
                version = version,
                artifact = artifact,
                license = nativeLicenseFor(licenseExpression).forProject(nativeProjectUrl(purl)),
                complianceNote = "Vendored native component recorded in third_party/ffmpeg-kit-next/native-lock.json.",
                origin = "native-lock.json",
            )
        }

        val notices = (runtimeNotices + nativeNotices).sortedBy { it.artifact }
        val generatedText = buildString {
            appendLine("package com.novacut.editor.engine")
            appendLine()
            appendLine("// Generated by the releaseRuntimeClasspath and native-lock notice task; do not edit.")
            appendLine("internal object RuntimeOpenSourceLicensesGenerated {")
            appendLine("    val notices: List<OpenSourceLicenseNotice> = listOf(")
            notices.forEach { append(it.toKotlin()) }
            appendLine("    )")
            appendLine("}")
        }
        val generatedOutput = generatedFile.get().asFile
        generatedOutput.parentFile.mkdirs()
        generatedOutput.writeText(generatedText)

        val reportEntries = notices.map { notice ->
            linkedMapOf<String, Any?>(
                "artifact" to notice.artifact,
                "name" to notice.name,
                "version" to notice.version,
                "license" to notice.license.licenseName,
                "licenseUrl" to notice.license.licenseUrl,
                "projectUrl" to notice.license.projectUrl,
                "origin" to notice.origin,
            )
        }
        val report = linkedMapOf<String, Any?>(
            "schemaVersion" to 1,
            "releaseConfiguration" to "releaseRuntimeClasspath",
            "runtimeComponentCount" to runtimeNotices.size,
            "nativeComponentCount" to nativeNotices.size,
            "notices" to reportEntries,
        )
        val reportOutput = reportFile.get().asFile
        reportOutput.parentFile.mkdirs()
        reportOutput.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(report)) + "\n")
        logger.lifecycle("Runtime notice inventory generated: ${runtimeNotices.size} resolved + ${nativeNotices.size} native components")
    }
}

tasks.named("preBuild").configure {
    dependsOn(verifyFfmpegKitAar)
    dependsOn(generateRuntimeOpenSourceNotices)
}

tasks.configureEach {
    if (name.contains("Kotlin") && name.contains("Compile")) {
        dependsOn(generateRuntimeOpenSourceNotices)
    }
}

tasks.withType<KotlinCompile>().configureEach {
    source(runtimeNoticeGeneratedDir.get().asFile)
}

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
    "org.jetbrains.kotlin:kotlin-stdlib" to "2.4.10",
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

verifyResolvedAdvisoryFloors.configure {
    dependsOn(generateRuntimeOpenSourceNotices)
}

// The public-claim and repository-contract tests read files that live outside any
// compiled source set: the README, the version catalog, the Fastlane listing copy,
// the wrapper pin. Gradle cannot infer those as task inputs, so editing one left the
// test task UP-TO-DATE and the assertion silently never ran -- a claim validator that
// cannot fail. Declare them so a change to public copy re-runs the checks that police it.
// The signing identity is not a build detail: it is the only thing that lets an
// installed user receive an update. Losing or changing it is unrecoverable for
// them, so the release lane proves the resolved key before it produces anything.
val verifyReleaseSigningIdentity = tasks.register("verifyReleaseSigningIdentity") {
    group = "verification"
    description = "Fail the release build unless the signing key matches the published certificate."
    val identityFile = file("release-signing-identity.json")
    inputs.file(identityFile)
    val signing = android.signingConfigs.getByName("release")
    val storeFile = signing.storeFile
    val storePassword = signing.storePassword
    val keyAlias = signing.keyAlias
    doFirst {
        val expected = Regex("\"certificateSha256\"\\s*:\\s*\"([0-9a-fA-F]+)\"")
            .find(identityFile.readText())
            ?.groupValues?.get(1)
            ?.lowercase()
            ?: throw GradleException(
                "release-signing-identity.json has no certificateSha256. " +
                    "Restore it from a published release's .signing-cert-sha256 sidecar."
            )

        if (storeFile == null || !storeFile.exists()) {
            throw GradleException(
                "No release keystore resolved. Create keystore.properties (gitignored) with " +
                    "storeFile/storePassword/keyAlias/keyPassword, or export CLEARCUT_STORE_FILE, " +
                    "CLEARCUT_STORE_PASSWORD, CLEARCUT_KEY_ALIAS and CLEARCUT_KEY_PASSWORD. " +
                    "The release build no longer falls back to the debug key: a release signed " +
                    "with a different certificate cannot update any existing install."
            )
        }

        val keyStore = KeyStore.getInstance("PKCS12")
        storeFile.inputStream().use { stream -> keyStore.load(stream, storePassword?.toCharArray()) }
        val alias = keyAlias ?: throw GradleException("keystore.properties defines no keyAlias.")
        val certificate = keyStore.getCertificate(alias)
            ?: throw GradleException("Alias '$alias' is not present in ${storeFile.name}.")
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(certificate.encoded)
            .joinToString("") { byte -> "%02x".format(byte) }

        if (actual != expected) {
            throw GradleException(
                "Release signing certificate mismatch.\n" +
                    "  expected: $expected (every published release since v3.74.108)\n" +
                    "  resolved: $actual (alias '$alias' in ${storeFile.name})\n" +
                    "Signing with this key would strand every installed user, because Android " +
                    "refuses an in-place update across a certificate change and ClearCut's " +
                    "projects live in app-private storage. Point keystore.properties at the " +
                    "correct keystore, or -- if the break is deliberate -- update " +
                    "release-signing-identity.json and document the clean-install migration."
            )
        }
        logger.lifecycle("Release signing identity verified: $actual")
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") {
        dependsOn(verifyReleaseSigningIdentity)
    }
}

tasks.withType<Test>().configureEach {
    listOf(
        "README.md",
        "LICENSE",
        "scripts/package_identity.json",
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
    implementation(libs.androidx.room3.runtime)
    implementation(libs.androidx.sqlite.framework)
    ksp(libs.androidx.room3.compiler)

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

    // Source-pinned LGPL build: FFmpegKitNext 8.1.0 / FFmpeg 8.1.2, five Android ABIs.
    implementation(files(ffmpegKitAar))
    implementation("com.arthenica:smart-exception-java:0.2.1")

    // Tier A.2 / R6.6: DeepFilterNet Android noise reduction
    implementation(libs.android.deepfilternet)

    // Tier 4: OkHttp (cloud inpainting API)
    implementation(libs.okhttp)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    add("qaImplementation", libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    androidTestImplementation(libs.androidx.room3.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)

    baselineProfile(project(":baselineprofile"))
}

baselineProfile {
    automaticGenerationDuringBuild = false
}

// The screenshot lane intentionally uses the QA unit-test runtime: it contains the
// same generated resources and Hilt graph as the normal JVM gate, while filtering to
// the small visual verification class. Keeping record/compare/verify as explicit
// tasks makes the golden update operation reviewable and prevents a plain unit-test
// invocation from writing binary artifacts.
afterEvaluate {
    val qaUnitTestTask = tasks.named<Test>("testQaUnitTest")
    val visualGoldenDirectory = layout.projectDirectory.dir("src/test/screenshots").asFile
    val visualComparisonDirectory = layout.buildDirectory.dir("outputs/roborazzi-comparison")
    val visualResultDirectory = layout.buildDirectory.dir("test-results/roborazzi")

    fun registerJvmVisualVerificationTask(
        name: String,
        roborazziProperty: String,
    ) = tasks.register<Test>(name) {
        group = "verification"
        description = "Run the JVM visual and accessibility verification lane in $roborazziProperty mode."
        dependsOn(
            "compileQaUnitTestKotlin",
            "compileQaUnitTestJavaWithJavac",
            "processQaUnitTestJavaRes",
            "transformQaUnitTestClassesWithAsm",
        )
        testClassesDirs = qaUnitTestTask.get().testClassesDirs
        classpath = qaUnitTestTask.get().classpath
        useJUnit()
        include("**/JvmVisualVerificationTest.class")
        include("**/JvmAccessibilityFailureContractTest.class")
        maxParallelForks = 1
        outputs.upToDateWhen { false }
        systemProperty("clearcut.visual.capture", "true")
        systemProperty("roborazzi.test.$roborazziProperty", "true")
        systemProperty("roborazzi.output.dir", visualGoldenDirectory.absolutePath)
        systemProperty("roborazzi.compare.output.dir", visualComparisonDirectory.get().asFile.absolutePath)
        systemProperty("roborazzi.result.dir", visualResultDirectory.get().asFile.absolutePath)
    }

    registerJvmVisualVerificationTask("recordJvmVisualVerification", "record")
    registerJvmVisualVerificationTask("compareJvmVisualVerification", "compare")
    registerJvmVisualVerificationTask("verifyJvmVisualVerification", "verify")
}
