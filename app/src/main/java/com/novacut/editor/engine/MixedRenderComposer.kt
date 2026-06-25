package com.novacut.editor.engine

/**
 * B.5 mixed-segment composer plan.
 *
 * `SmartRenderEngine.analyzeTimeline` produces per-clip [SmartRenderEngine.RenderSegment]s
 * and `planRuns` groups them into contiguous same-flag [SmartRenderEngine.RenderRun]s.
 * This composer plans the export-orchestration step: for each run, which engine
 * to use (`StreamCopy` for pass-through, `Transformer` / `FFmpeg` for re-encode),
 * which temp file to write to, and how the resulting per-run outputs concatenate
 * into the final delivery file.
 *
 * Pure data — no I/O, no Android. The actual orchestration that consumes a
 * [CompositionPlan] lives in `VideoEngine.exportMixed(...)` (a follow-up
 * commit), and that path runs each [RunExecution] through the corresponding
 * engine and finishes with [CompositionPlan.concat] via `FFmpegEngine.concat()`.
 *
 * Design intent (documented in `ROADMAP.md` Tier B.5):
 *  - **Don't pay the cost** of FFmpeg concat when the whole timeline is one run.
 *    A single-run plan returns the run output as the final output and a null
 *    [CompositionPlan.concat] — the caller writes straight to the user's
 *    destination.
 *  - **Don't pay the cost** of mixed rendering when no run is pass-through.
 *    An all-re-encode timeline is just a single Transformer pass; the plan
 *    flags that with [CompositionPlan.benefit] = `NoBenefit` so the caller
 *    can shortcut to the existing whole-timeline path.
 *  - **Surface validation** before the user starts a 10-minute render. Runs
 *    that are too short to keyframe-align reliably (< 250 ms by default) are
 *    flagged; the orchestrator can either widen the boundary or fall back to
 *    re-encoding that run.
 */
object MixedRenderComposer {

    /**
     * Which export engine a run should be handed to. Maps 1:1 to the
     * [SmartRenderEngine.RenderRun.needsReEncode] flag today; the abstraction
     * lets future composers route a re-encode run through FFmpeg (libass burn-in,
     * AV1 software fallback) vs the Media3 Transformer.
     */
    enum class Engine {
        /** `StreamCopyExportEngine` — `-c copy -ss -to`, ~50x faster than transcode. */
        STREAM_COPY,

        /** Media3 Transformer — the existing in-tree re-encode path. */
        TRANSFORMER,
    }

    /**
     * Outcome of the cost/benefit check. Drives whether the caller should
     * actually use [MixedRenderComposer] vs delegate the whole render to one
     * of the simpler paths.
     */
    enum class Benefit {
        /** Mixed copy/re-encode plan; concat step is required. */
        Mixed,

        /** Whole timeline is one run; no concat needed. */
        SingleRun,

        /** Every run is re-encode; no copy benefit. */
        NoBenefit,
    }

    enum class IssueSeverity { INFO, WARNING }

    data class ValidationIssue(
        val severity: IssueSeverity,
        val message: String,
        val runIndex: Int? = null,
    )

    /**
     * One run's export plan. The orchestrator runs each in order and
     * collects the resulting file under `outputFileName`.
     */
    data class RunExecution(
        val index: Int,
        val run: SmartRenderEngine.RenderRun,
        val engine: Engine,
        val outputFileName: String,
    )

    /**
     * Final concat step plan. `null` when the composer determined no concat
     * is needed (single run, or all-re-encode shortcut).
     */
    data class ConcatStep(
        /** Input file names in concat order (relative — caller resolves paths). */
        val inputs: List<String>,
        val outputFileName: String,
    )

    /**
     * The full composer plan. Consumers iterate [runs] in order, then run
     * [concat] if present.
     */
    data class CompositionPlan(
        val benefit: Benefit,
        val runs: List<RunExecution>,
        val concat: ConcatStep?,
        val issues: List<ValidationIssue>,
    ) {
        val needsConcat: Boolean get() = concat != null

        /** Number of distinct engines this plan involves. Useful for telemetry. */
        val distinctEnginesUsed: Set<Engine> get() = runs.map { it.engine }.toSet()
    }

    /**
     * Build a [CompositionPlan] from the run list.
     *
     * @param runs The output of [SmartRenderEngine.planRuns].
     * @param projectStem A filesystem-safe project stem used to name the
     *   per-run temp files (e.g. "vlog-march-2026"). Sanitised by the caller;
     *   this composer does not touch the filesystem.
     * @param finalOutputName The user's eventual output file name.
     * @param minKeyframeAlignMs Runs shorter than this are flagged with a
     *   keyframe-alignment warning — stream-copy may slip a frame or two
     *   on tiny ranges. Defaults to 250 ms (one I-frame at most plausible
     *   GOP intervals).
     * @param finalExtension Output container extension (`.mp4`, `.mkv`). Used
     *   to name the per-run + final files consistently so the concat demuxer
     *   sees compatible containers.
     */
    fun plan(
        runs: List<SmartRenderEngine.RenderRun>,
        projectStem: String,
        finalOutputName: String,
        minKeyframeAlignMs: Long = 250L,
        finalExtension: String = ".mp4",
    ): CompositionPlan {
        val issues = mutableListOf<ValidationIssue>()
        if (runs.isEmpty()) {
            issues += ValidationIssue(IssueSeverity.WARNING, "Timeline has no runs to render.")
            return CompositionPlan(Benefit.NoBenefit, emptyList(), null, issues)
        }

        val sanitisedStem = sanitiseStem(projectStem)
        val ext = if (finalExtension.startsWith('.')) finalExtension else ".$finalExtension"

        val executions = runs.mapIndexed { i, run ->
            val engine = if (run.needsReEncode) Engine.TRANSFORMER else Engine.STREAM_COPY
            val tag = if (run.needsReEncode) "re" else "cp"
            val name = "${sanitisedStem}-run${i.toString().padStart(2, '0')}-$tag$ext"
            // Flag short stream-copy runs — keyframe alignment is fuzzy below
            // the typical 250 ms GOP boundary.
            if (engine == Engine.STREAM_COPY && run.durationMs < minKeyframeAlignMs) {
                issues += ValidationIssue(
                    severity = IssueSeverity.WARNING,
                    message = "Stream-copy run is shorter than ${minKeyframeAlignMs}ms; " +
                        "keyframe alignment may slip a frame. Consider re-encoding this run.",
                    runIndex = i,
                )
            }
            RunExecution(index = i, run = run, engine = engine, outputFileName = name)
        }

        return when {
            executions.size == 1 -> {
                issues += ValidationIssue(
                    IssueSeverity.INFO,
                    "Single-run timeline — concat skipped; run output is the final output.",
                )
                CompositionPlan(
                    benefit = Benefit.SingleRun,
                    runs = executions.map { it.copy(outputFileName = finalOutputName) },
                    concat = null,
                    issues = issues,
                )
            }
            executions.all { it.engine == Engine.TRANSFORMER } -> {
                issues += ValidationIssue(
                    IssueSeverity.INFO,
                    "Every run needs re-encoding — no stream-copy benefit. " +
                        "Caller should use the whole-timeline Transformer path.",
                )
                CompositionPlan(
                    benefit = Benefit.NoBenefit,
                    runs = executions,
                    concat = null,
                    issues = issues,
                )
            }
            else -> {
                CompositionPlan(
                    benefit = Benefit.Mixed,
                    runs = executions,
                    concat = ConcatStep(
                        inputs = executions.map { it.outputFileName },
                        outputFileName = finalOutputName,
                    ),
                    issues = issues,
                )
            }
        }
    }

    /**
     * Trim and sanitise the project stem so the run output names land on
     * any filesystem the user might be exporting to (FAT32 SAF targets are
     * the strictest). Pure — caller can use this for any temp-file naming.
     */
    fun sanitiseStem(stem: String): String {
        val cleaned = stem.trim()
            .map { c -> if (c.isLetterOrDigit() || c in ALLOWED_STEM_CHARS) c else '_' }
            .joinToString("")
            .trim('_')
            .ifEmpty { "clearcut" }
        return cleaned.take(MAX_STEM_CHARS)
    }

    private const val MAX_STEM_CHARS = 48
    private val ALLOWED_STEM_CHARS = setOf('-', '_', '.', ' ').map { it }
}
