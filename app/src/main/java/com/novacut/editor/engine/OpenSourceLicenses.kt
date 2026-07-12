package com.novacut.editor.engine

data class OpenSourceLicenseNotice(
    val name: String,
    val version: String,
    val artifact: String,
    val licenseName: String,
    val licenseText: String,
    val licenseUrl: String,
    val projectUrl: String,
    val sourceOfferText: String? = null,
    val complianceNote: String? = null,
)

object OpenSourceLicenses {
    private const val APACHE_2_TEXT =
        "Apache License 2.0. Redistribution must keep the license and required notices, " +
            "and the software is provided without warranties under the Apache terms."
    private const val MIT_TEXT =
        "MIT License. Redistribution must keep the copyright notice and permission notice, " +
            "and the software is provided without warranties."

    val notices: List<OpenSourceLicenseNotice> = listOf(
        OpenSourceLicenseNotice(
            name = "FFmpegKitNext / FFmpeg",
            version = "8.1.0 (FFmpeg 8.1.2)",
            artifact = "third_party/ffmpeg-kit-next/ffmpeg-kit-next-8.1.0.aar",
            licenseName = "GNU General Public License Version 3",
            licenseText = "This source-pinned build enables GPL components including x264. " +
                "Redistribution must preserve the packaged licenses and corresponding-source offer.",
            licenseUrl = "https://www.gnu.org/licenses/gpl-3.0.txt",
            projectUrl = "https://github.com/arthenica/ffmpeg-kit-next",
            sourceOfferText = """
                Exact corresponding-source recipe for this GPL v3.0 build:
                https://github.com/arthenica/ffmpeg-kit-next/tree/3e223118e6e8fb6208693ecf3952e77cd096f587
                Command: ./nix-android.sh -p android-r27d --enable-gpl --enable-x264 --enable-libass --jobs=8
                The complete recipe, checksums, enabled components, and source revisions are
                recorded in third_party/ffmpeg-kit-next/native-lock.json and the installed
                application resource res/raw/ffmpeg_kit_next_source_offer.txt.
            """.trimIndent(),
            complianceNote = "The AAR packages res/raw/license.txt, res/raw/license_*.txt, " +
                "and res/raw/source.txt; ClearCut additionally packages " +
                "res/raw/ffmpeg_kit_next_source_offer.txt with its exact source recipe.",
        ),
        OpenSourceLicenseNotice(
            name = "Android DeepFilterNet",
            version = "0.0.8",
            artifact = "io.github.kaleyravideo:android-deepfilternet",
            licenseName = "The Apache License, Version 2.0",
            licenseText = APACHE_2_TEXT,
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0.txt",
            projectUrl = "https://github.com/KaleyraVideo/AndroidDeepFilterNet",
            complianceNote = "Bundled model variant derived from DeepFilterNet. Keep the " +
                "relevant Apache/MIT notices with redistributed builds that include the " +
                "native library and bundled model.",
        ),
        OpenSourceLicenseNotice(
            name = "MediaPipe Tasks Vision",
            version = "0.10.35",
            artifact = "com.google.mediapipe:tasks-vision",
            licenseName = "The Apache Software License, Version 2.0",
            licenseText = APACHE_2_TEXT,
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0.txt",
            projectUrl = "https://mediapipe.dev/",
        ),
        OpenSourceLicenseNotice(
            name = "Lottie Compose",
            version = "6.7.1",
            artifact = "com.airbnb.android:lottie-compose",
            licenseName = "Apache-2.0",
            licenseText = APACHE_2_TEXT,
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            projectUrl = "https://github.com/airbnb/lottie-android",
        ),
        OpenSourceLicenseNotice(
            name = "OkHttp",
            version = "5.3.2",
            artifact = "com.squareup.okhttp3:okhttp",
            licenseName = "The Apache Software License, Version 2.0",
            licenseText = APACHE_2_TEXT,
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0.txt",
            projectUrl = "https://github.com/square/okhttp",
        ),
        OpenSourceLicenseNotice(
            name = "Media3",
            version = "1.10.1",
            artifact = "androidx.media3",
            licenseName = "The Apache Software License, Version 2.0",
            licenseText = APACHE_2_TEXT,
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0.txt",
            projectUrl = "https://github.com/androidx/media",
        ),
        OpenSourceLicenseNotice(
            name = "ONNX Runtime Android",
            version = "1.26.0",
            artifact = "com.microsoft.onnxruntime:onnxruntime-android",
            licenseName = "MIT License",
            licenseText = MIT_TEXT,
            licenseUrl = "https://opensource.org/licenses/MIT",
            projectUrl = "https://microsoft.github.io/onnxruntime/",
        ),
        OpenSourceLicenseNotice(
            name = "Coil Compose",
            version = "2.7.0",
            artifact = "io.coil-kt:coil-compose",
            licenseName = "The Apache License, Version 2.0",
            licenseText = APACHE_2_TEXT,
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0.txt",
            projectUrl = "https://github.com/coil-kt/coil",
        ),
        OpenSourceLicenseNotice(
            name = "Hilt / Dagger",
            version = "2.58",
            artifact = "com.google.dagger:hilt-android",
            licenseName = "Apache 2.0",
            licenseText = APACHE_2_TEXT,
            licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0.txt",
            projectUrl = "https://github.com/google/dagger",
        ),
    )

    fun noticesForDisplay(): List<OpenSourceLicenseNotice> = notices.sortedBy { it.name.lowercase() }

    fun noticeForArtifact(artifact: String): OpenSourceLicenseNotice? =
        notices.firstOrNull { it.artifact == artifact }

    fun ffmpegKitNotice(): OpenSourceLicenseNotice =
        requireNotNull(noticeForArtifact("third_party/ffmpeg-kit-next/ffmpeg-kit-next-8.1.0.aar"))

    fun dependenciesWithSourceOffers(): List<OpenSourceLicenseNotice> =
        notices.filter { !it.sourceOfferText.isNullOrBlank() }
}
