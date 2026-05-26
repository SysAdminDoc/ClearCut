package com.novacut.editor.engine

/**
 * Project-level working colour space + display transform configuration.
 *
 * Stored separately from the Room-backed [com.novacut.editor.model.Project]
 * entity so adopting this surface does not force a schema migration today —
 * the editor holds it in memory and persists it through the autosave JSON
 * path, which is already future-schema-gated via
 * [AutoSaveState.peekSchemaVersion].
 *
 * Once the Room schema is bumped to v7 the [WorkingColorSpace] and
 * [DisplayTransform] enums move onto the entity directly; this class stays
 * as the in-memory companion that engine code consumes.
 *
 * The job of this object is *not* to do colour conversion. It is to record
 * the user's stated intent so [ExportColorConfidenceEngine] (and the future
 * preview-vs-export parity checks) can emit higher-value warnings when the
 * source HDR format, the working space, and the display transform disagree.
 *
 * See RESEARCH_FEATURE_PLAN_2026-05-25 Highest-Value #13 and ROADMAP R4.2.
 */
data class ProjectColorPolicy(
    val workingColorSpace: WorkingColorSpace = WorkingColorSpace.SDR_BT709,
    val displayTransform: DisplayTransform = DisplayTransform.NONE,
) {

    /** Coarse-grained working colour space. The names match common NLE labels. */
    enum class WorkingColorSpace(val displayName: String, val isHdr: Boolean) {
        SDR_BT709("SDR · Rec. 709", false),
        HDR10_BT2020_PQ("HDR10 · Rec. 2020 PQ", true),
        HDR_HLG("HLG · Rec. 2020", true),

        /**
         * ACES-inspired "Pro color" preset. Treated as HDR-class for warning
         * purposes; the actual GL/Media3 path is not on this code path yet.
         */
        ACES_AP1("ACES AP1 linear", true),
    }

    /**
     * Output-side transform to apply when the working space is HDR but the
     * delivery target is SDR. NONE means "let the encoder decide" — the
     * confidence engine will warn when that pairing actually matters.
     */
    enum class DisplayTransform(val displayName: String) {
        NONE("Pass through"),
        BT2390_TONEMAP("BT.2390 tone-map to SDR"),
        HABLE_TONEMAP("Hable tone-map to SDR"),
    }

    /**
     * Pure pairing-coherence check the export sheet and project-settings
     * panel can render as a chip.
     *
     * - SDR_BT709 + NONE              → COHERENT
     * - SDR_BT709 + tonemap           → SDR_TONEMAP_NOOP (warning)
     * - HDR working + NONE            → HDR_PASSTHROUGH (info; the encoder
     *   decides at render time; this is the "no opinion" pairing)
     * - HDR working + tonemap         → HDR_TO_SDR_TONEMAP (info; user
     *   explicitly downsampling for SDR delivery)
     */
    fun coherence(): Coherence = when {
        !workingColorSpace.isHdr && displayTransform == DisplayTransform.NONE ->
            Coherence.COHERENT
        !workingColorSpace.isHdr && displayTransform != DisplayTransform.NONE ->
            Coherence.SDR_TONEMAP_NOOP
        workingColorSpace.isHdr && displayTransform == DisplayTransform.NONE ->
            Coherence.HDR_PASSTHROUGH
        else -> Coherence.HDR_TO_SDR_TONEMAP
    }

    enum class Coherence {
        COHERENT,
        SDR_TONEMAP_NOOP,
        HDR_PASSTHROUGH,
        HDR_TO_SDR_TONEMAP,
    }

    /**
     * Whether the user has chosen a working-space + transform pairing that
     * the export sheet should foreground as an HDR delivery. Drives the
     * "HDR" badge in ExportSheet — once Project schema v7 ships, this same
     * predicate replaces the per-clip-source heuristic that
     * [ExportColorConfidenceEngine] uses today.
     */
    val deliversHdr: Boolean
        get() = workingColorSpace.isHdr && displayTransform == DisplayTransform.NONE

    companion object {
        /** Conservative default applied to brand-new projects. */
        val DEFAULT = ProjectColorPolicy()
    }
}
