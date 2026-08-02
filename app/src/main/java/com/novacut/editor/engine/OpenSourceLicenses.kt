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
    /** Notice rows are generated from scripts/capability_registry.json. */
    val notices: List<OpenSourceLicenseNotice> = CapabilityRegistry.notices

    fun noticesForDisplay(): List<OpenSourceLicenseNotice> = notices.sortedBy { it.name.lowercase() }

    fun noticeForArtifact(artifact: String): OpenSourceLicenseNotice? =
        notices.firstOrNull { it.artifact == artifact }

    fun ffmpegKitNotice(): OpenSourceLicenseNotice =
        requireNotNull(noticeForArtifact("third_party/ffmpeg-kit-next/ffmpeg-kit-next-8.1.0.aar"))

    fun dependenciesWithSourceOffers(): List<OpenSourceLicenseNotice> =
        notices.filter { !it.sourceOfferText.isNullOrBlank() }
}
