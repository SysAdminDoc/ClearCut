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
    /**
     * Runtime rows are generated from the resolved release graph and native lock.
     * The curated registry supplies reviewed names, license text, and source-offer
     * obligations; generated versions always win so the Settings panel cannot go
     * stale when dependency resolution changes.
     */
    val notices: List<OpenSourceLicenseNotice> = run {
        val generatedByArtifact = RuntimeOpenSourceLicensesGenerated.notices.associateBy { it.artifact }
        val refreshedCurated = CapabilityRegistry.notices.map { curated ->
            generatedByArtifact[curated.artifact]?.let { current ->
                current.copy(
                    name = curated.name,
                    licenseName = curated.licenseName,
                    licenseText = curated.licenseText,
                    licenseUrl = curated.licenseUrl,
                    projectUrl = curated.projectUrl,
                    sourceOfferText = curated.sourceOfferText,
                    complianceNote = curated.complianceNote,
                )
            } ?: curated
        }
        refreshedCurated + RuntimeOpenSourceLicensesGenerated.notices.filter { generated ->
            CapabilityRegistry.notices.none { curated -> curated.artifact == generated.artifact }
        }
    }

    fun noticesForDisplay(): List<OpenSourceLicenseNotice> = notices.sortedBy { it.name.lowercase() }

    fun noticeForArtifact(artifact: String): OpenSourceLicenseNotice? =
        notices.firstOrNull { it.artifact == artifact }

    fun ffmpegKitNotice(): OpenSourceLicenseNotice =
        requireNotNull(noticeForArtifact("third_party/ffmpeg-kit-next/ffmpeg-kit-next-8.1.0.aar"))

    fun dependenciesWithSourceOffers(): List<OpenSourceLicenseNotice> =
        notices.filter { !it.sourceOfferText.isNullOrBlank() }
}
