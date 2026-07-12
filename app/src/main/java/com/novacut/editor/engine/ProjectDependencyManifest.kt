package com.novacut.editor.engine

import com.novacut.editor.model.Clip
import com.novacut.editor.model.EffectType

const val SEGMENTATION_MODEL_DEPENDENCY = "mediapipe-selfie-segmenter"

enum class ProjectDependencyKind {
    MEDIA,
    LUT,
    CUSTOM_FONT,
    WATERMARK,
    MODEL
}

enum class ProjectDependencyStatus {
    AVAILABLE,
    MISSING,
    UNREADABLE,
    INVALID
}

enum class ProjectDependencyArchivePolicy {
    INCLUDE,
    REFERENCE_ONLY,
    EXCLUDE
}

data class ProjectDependencyRequest(
    val kind: ProjectDependencyKind,
    val reference: String,
    val label: String = reference,
    val requested: Boolean = true,
    val fallbackAllowed: Boolean = false,
    val fallbackName: String? = null,
    val archivePolicy: ProjectDependencyArchivePolicy = defaultArchivePolicy(kind)
) {
    init {
        require(reference.isNotBlank()) { "Dependency reference cannot be blank" }
    }

    companion object {
        fun defaultArchivePolicy(kind: ProjectDependencyKind): ProjectDependencyArchivePolicy =
            when (kind) {
                ProjectDependencyKind.MODEL -> ProjectDependencyArchivePolicy.REFERENCE_ONLY
                else -> ProjectDependencyArchivePolicy.INCLUDE
            }
    }
}

data class ProjectDependency(
    val request: ProjectDependencyRequest,
    val status: ProjectDependencyStatus
) {
    val blocksRequestedOperation: Boolean
        get() = request.requested &&
            !request.fallbackAllowed &&
            status != ProjectDependencyStatus.AVAILABLE

    val shouldIncludeInArchive: Boolean
        get() = request.archivePolicy == ProjectDependencyArchivePolicy.INCLUDE &&
            status == ProjectDependencyStatus.AVAILABLE
}

data class ProjectDependencyEditorInputs(
    val watermarkReference: String? = null,
    val customFontReferencesByFamily: Map<String, String> = emptyMap(),
    val modelDependencies: List<ProjectDependencyRequest> = emptyList(),
    val fallbackAllowedReferences: Set<String> = emptySet()
)

fun interface ProjectDependencyProbe {
    fun probe(request: ProjectDependencyRequest): ProjectDependencyStatus
}

data class ProjectDependencyManifest(
    val dependencies: List<ProjectDependency>
) {
    val blockingDependencies: List<ProjectDependency>
        get() = dependencies.filter(ProjectDependency::blocksRequestedOperation)

    val archiveDependencies: List<ProjectDependency>
        get() = dependencies.filter(ProjectDependency::shouldIncludeInArchive)

    val canProceed: Boolean
        get() = blockingDependencies.isEmpty()

    companion object {
        fun collect(
            state: AutoSaveState,
            editorInputs: ProjectDependencyEditorInputs = ProjectDependencyEditorInputs(),
            probe: ProjectDependencyProbe
        ): ProjectDependencyManifest {
            val collected = mutableListOf<ProjectDependencyRequest>()

            state.tracks.forEach { track ->
                track.clips.forEach { clip -> collectClipDependencies(clip, collected) }
            }
            state.imageOverlays.forEach { overlay ->
                collected += request(ProjectDependencyKind.MEDIA, overlay.sourceUri.toString())
            }
            state.storyboardCards.mapNotNullTo(collected) { card ->
                card.mediaUri?.toString()?.takeIf(String::isNotBlank)?.let { reference ->
                    ProjectDependencyRequest(
                        kind = ProjectDependencyKind.MEDIA,
                        reference = reference,
                        requested = false,
                    )
                }
            }

            val fontFamilies = buildList {
                state.textOverlays.mapTo(this) { it.fontFamily }
                state.tracks.forEach { track ->
                    track.clips.forEach { clip -> collectCaptionFontFamilies(clip, this) }
                }
            }
            fontFamilies.filter { it.startsWith("custom:") }.forEach { family ->
                val reference = editorInputs.customFontReferencesByFamily[family] ?: family
                collected += request(
                    kind = ProjectDependencyKind.CUSTOM_FONT,
                    reference = reference,
                    label = family
                )
            }

            editorInputs.watermarkReference
                ?.takeIf(String::isNotBlank)
                ?.let { collected += request(ProjectDependencyKind.WATERMARK, it) }
            collected += editorInputs.modelDependencies.map { model ->
                require(model.kind == ProjectDependencyKind.MODEL) {
                    "modelDependencies may only contain MODEL requests"
                }
                model
            }

            val fallbackReferences = editorInputs.fallbackAllowedReferences
            val merged = LinkedHashMap<Pair<ProjectDependencyKind, String>, ProjectDependencyRequest>()
            collected.forEach { original ->
                val candidate = original.copy(
                    fallbackAllowed = original.fallbackAllowed || original.reference in fallbackReferences
                )
                val key = candidate.kind to candidate.reference
                merged[key] = merged[key]?.merge(candidate) ?: candidate
            }

            return ProjectDependencyManifest(
                dependencies = merged.values.map { dependency ->
                    ProjectDependency(dependency, probe.probe(dependency))
                }
            )
        }

        private fun collectClipDependencies(
            clip: Clip,
            destination: MutableList<ProjectDependencyRequest>
        ) {
            destination += request(ProjectDependencyKind.MEDIA, clip.sourceUri.toString())
            clip.colorGrade?.lutPath?.takeIf(String::isNotBlank)?.let { lutPath ->
                destination += request(ProjectDependencyKind.LUT, lutPath)
            }
            if (clip.effects.any { it.enabled && it.type == EffectType.BG_REMOVAL }) {
                destination += ProjectDependencyRequest(
                    kind = ProjectDependencyKind.MODEL,
                    reference = SEGMENTATION_MODEL_DEPENDENCY,
                    label = "Background-removal model",
                    archivePolicy = ProjectDependencyArchivePolicy.REFERENCE_ONLY,
                )
            }
            clip.compoundClips.forEach { child -> collectClipDependencies(child, destination) }
        }

        private fun collectCaptionFontFamilies(clip: Clip, destination: MutableList<String>) {
            clip.captions.mapTo(destination) { it.style.fontFamily }
            clip.compoundClips.forEach { child -> collectCaptionFontFamilies(child, destination) }
        }

        private fun request(
            kind: ProjectDependencyKind,
            reference: String,
            label: String = reference
        ) = ProjectDependencyRequest(kind = kind, reference = reference, label = label)
    }
}

private fun ProjectDependencyRequest.merge(other: ProjectDependencyRequest): ProjectDependencyRequest {
    check(kind == other.kind && reference == other.reference)
    return copy(
        requested = requested || other.requested,
        fallbackAllowed = fallbackAllowed && other.fallbackAllowed,
        fallbackName = fallbackName ?: other.fallbackName,
        archivePolicy = stricterArchivePolicy(archivePolicy, other.archivePolicy)
    )
}

private fun stricterArchivePolicy(
    first: ProjectDependencyArchivePolicy,
    second: ProjectDependencyArchivePolicy
): ProjectDependencyArchivePolicy {
    val priority = listOf(
        ProjectDependencyArchivePolicy.EXCLUDE,
        ProjectDependencyArchivePolicy.REFERENCE_ONLY,
        ProjectDependencyArchivePolicy.INCLUDE
    )
    return if (priority.indexOf(first) >= priority.indexOf(second)) first else second
}
