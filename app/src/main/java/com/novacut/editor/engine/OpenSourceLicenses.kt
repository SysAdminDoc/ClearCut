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
            name = "FFmpegKit 16 KB / FFmpeg",
            version = "6.1.1",
            artifact = "com.moizhassan.ffmpeg:ffmpeg-kit-16kb",
            licenseName = "LGPL-3.0 POM with bundled GPLv3 obligation material",
            licenseText = "The Maven POM declares GNU Lesser General Public License Version 3. " +
                "The packaged Android archive also includes GPLv3 license text and a GPLv3 " +
                "source-offer resource. Redistributed builds that include this artifact must " +
                "preserve those resources and comply with the bundled FFmpegKit / FFmpeg terms.",
            licenseUrl = "https://www.gnu.org/licenses/lgpl-3.0.txt",
            projectUrl = "https://github.com/moizhassankh/ffmpeg-kit-android-16KB",
            sourceOfferText = """
                The source code of "FFmpegKit", "FFmpeg" and external libraries enabled within
                "FFmpeg" for this release can be downloaded from
                https://github.com/arthenica/ffmpeg-kit/wiki/Source page.

                If you want to receive the source code on physical media submit your request
                to "open-source@arthenica.com" email address.

                Your request should include "FFmpegKit" version, "FFmpegKit" platform, your
                name, your company name, your mailing address, the phone number and the date
                you started using "FFmpegKit".

                Note that we may charge you a fee to cover physical media printing and
                shipping costs. Your request must be sent within the first three years of the
                date you received "FFmpegKit" with "GPL v3.0" license.
            """.trimIndent(),
            complianceNote = "NovaCut treats this AAR as carrying FFmpegKit / FFmpeg GPLv3 " +
                "notice and source-offer obligations unless it is replaced by a verified " +
                "LGPL-only build flavor. The matching packaged resources are " +
                "res/raw/license.txt, res/raw/license_*.txt, and res/raw/source.txt.",
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
            licenseUrl = "http://www.apache.org/licenses/LICENSE-2.0.txt",
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
            licenseUrl = "http://www.apache.org/licenses/LICENSE-2.0.txt",
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
        requireNotNull(noticeForArtifact("com.moizhassan.ffmpeg:ffmpeg-kit-16kb"))

    fun dependenciesWithSourceOffers(): List<OpenSourceLicenseNotice> =
        notices.filter { !it.sourceOfferText.isNullOrBlank() }
}
