package com.novacut.editor.model

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * The project's color intent: how clips are read, the space effects run in,
 * and what the export delivers. Preview and export both turn this into one
 * Media3 HDR mode through ColorRenderPlanner, so the two can't disagree.
 *
 * Media3 ties the working space to the output. Keeping HDR processes HDR
 * clips in BT.2020; an SDR output tone-maps HDR clips to BT.709 as they are
 * decoded, so effects run in SDR. [normalized] enforces that pairing, which is
 * why a loaded policy never carries a working space its output can't honor.
 */
@Immutable
data class ProjectColorPolicy(
    val input: InputColor = InputColor.FROM_SOURCE,
    val working: WorkingColorSpace = WorkingColorSpace.SDR_BT709,
    val output: OutputColor = OutputColor.SDR,
) {
    enum class InputColor {
        /** Read each clip's container color tags. */
        FROM_SOURCE,

        /** Ignore HDR tags. For devices that can't process HDR in OpenGL; colors look washed out. */
        INTERPRET_HDR_AS_SDR,
    }

    enum class WorkingColorSpace { SDR_BT709, HDR_BT2020 }

    enum class OutputColor {
        /** Rec. 709 delivery; HDR clips are tone-mapped. */
        SDR,

        /** HDR delivery in the transfer of the HDR footage (HLG or PQ). */
        KEEP_HDR,
    }

    fun normalized(): ProjectColorPolicy = when {
        input == InputColor.INTERPRET_HDR_AS_SDR ->
            copy(working = WorkingColorSpace.SDR_BT709, output = OutputColor.SDR)
        output == OutputColor.KEEP_HDR -> copy(working = WorkingColorSpace.HDR_BT2020)
        else -> copy(working = WorkingColorSpace.SDR_BT709)
    }

    val keepsHdr: Boolean
        get() = input == InputColor.FROM_SOURCE && output == OutputColor.KEEP_HDR

    fun withKeepHdr(keep: Boolean): ProjectColorPolicy =
        if (keep) KEEP_HDR else copy(output = OutputColor.SDR).normalized()

    fun toJson(): JSONObject = JSONObject()
        .put("input", input.name)
        .put("working", working.name)
        .put("output", output.name)

    companion object {
        val DEFAULT = ProjectColorPolicy()
        val KEEP_HDR = ProjectColorPolicy(
            input = InputColor.FROM_SOURCE,
            working = WorkingColorSpace.HDR_BT2020,
            output = OutputColor.KEEP_HDR,
        )

        /**
         * Reads a saved policy. Null means the file predates color intent; callers
         * migrate that case with [legacy]. Unknown values from a newer build fall
         * back per field, then [normalized] restores a pairing the renderer honors.
         */
        fun fromJson(json: JSONObject?): ProjectColorPolicy? {
            json ?: return null
            return ProjectColorPolicy(
                input = enumOrDefault(json.optString("input"), InputColor.FROM_SOURCE),
                working = enumOrDefault(json.optString("working"), WorkingColorSpace.SDR_BT709),
                output = enumOrDefault(json.optString("output"), OutputColor.SDR),
            ).normalized()
        }

        /**
         * Projects saved before the policy existed carried no color intent; the only
         * HDR signal was a per-export "keep HDR" switch, which batch plans persisted.
         */
        fun legacy(hdrRequested: Boolean = false): ProjectColorPolicy =
            if (hdrRequested) KEEP_HDR else DEFAULT

        private inline fun <reified T : Enum<T>> enumOrDefault(name: String, fallback: T): T =
            enumValues<T>().firstOrNull { it.name == name } ?: fallback
    }
}
