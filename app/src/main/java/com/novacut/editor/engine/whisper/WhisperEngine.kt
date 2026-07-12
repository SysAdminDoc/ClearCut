package com.novacut.editor.engine.whisper

import android.content.Context
import android.media.MediaCodec
import android.util.Log
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.novacut.editor.engine.ModelDownloadManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

data class WhisperSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

enum class WhisperModelState {
    NOT_DOWNLOADED, DOWNLOADING, READY, ERROR
}

@Singleton
class WhisperEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDownloadManager: ModelDownloadManager
) {
    private val modelDir = File(context.filesDir, "whisper")
    private val encoderFile = File(modelDir, "encoder_model.onnx")
    private val decoderFile = File(modelDir, "decoder_model.onnx")
    private val vocabFile = File(modelDir, "vocab.json")

    private val _modelState = MutableStateFlow(
        if (hasMinimumModelFiles())
            WhisperModelState.READY else WhisperModelState.NOT_DOWNLOADED
    )
    val modelState: StateFlow<WhisperModelState> = _modelState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    @Volatile private var vocab: Map<Int, String>? = null
    @Volatile private var ortEnv: OrtEnvironment? = null

    companion object {
        private const val MODEL_REVISION = "2575352d61be1bf7225cf8f8b268a4678025fc58"
        private const val BASE_URL =
            "https://huggingface.co/onnx-community/whisper-tiny.en/resolve/$MODEL_REVISION"
        private const val ONNX_BASE_URL = "$BASE_URL/onnx"
        private const val ENCODER_URL = "$ONNX_BASE_URL/encoder_model.onnx"
        private const val DECODER_URL = "$ONNX_BASE_URL/decoder_model.onnx"
        private const val VOCAB_URL = "$BASE_URL/vocab.json"
        private const val ENCODER_SHA256 =
            "8c361b9430a5ef6619ee64b7fe06c725df19f36d508cc8b847064b34a888a3fe"
        private const val DECODER_SHA256 =
            "14f1d425a4821feeba77cf93eeeaf812ca816f2e3fec382b4f0fa93d29de710e"
        private const val VOCAB_SHA256 =
            "f6bd25a65e4e63ca31360e9fb11c7e4f9a391a78385d640acd814092dd6eee4f"
        private const val MIN_ENCODER_BYTES = 5L * 1024L * 1024L
        private const val MIN_DECODER_BYTES = 5L * 1024L * 1024L
        private const val MIN_VOCAB_BYTES = 4L * 1024L

        // Special tokens
        const val SOT = 50257          // <|startoftranscript|>
        const val EOT = 50256          // <|endoftext|>
        const val EN = 50258           // <|en|>
        const val TRANSCRIBE = 50359   // <|transcribe|>
        const val NO_TIMESTAMPS = 50363
        const val TIMESTAMP_BEGIN = 50364 // each increment = 0.02s
        const val NO_SPEECH = 50362

        private const val MAX_DECODE_TOKENS = 224
        private const val ENCODER_HIDDEN = 384 // whisper-tiny hidden size
        private const val ENCODER_SEQ = 1500   // encoder output sequence length

        private const val ENCODER_ESTIMATED_BYTES = 32_904_992L
        private const val DECODER_ESTIMATED_BYTES = 118_395_947L
        private const val VOCAB_ESTIMATED_BYTES = 999_186L
        private const val TOTAL_ESTIMATED_BYTES =
            ENCODER_ESTIMATED_BYTES + DECODER_ESTIMATED_BYTES + VOCAB_ESTIMATED_BYTES

        fun estimateModelSizeMB(): Int = 146
    }

    private fun hasMinimumModelFiles(): Boolean {
        return encoderFile.exists() && encoderFile.length() >= MIN_ENCODER_BYTES &&
            decoderFile.exists() && decoderFile.length() >= MIN_DECODER_BYTES &&
            vocabFile.exists() && vocabFile.length() >= MIN_VOCAB_BYTES
    }

    private fun hasVerifiedModelFiles(): Boolean {
        return ModelDownloadManager.verifyChecksumOrDelete(
            file = encoderFile,
            minimumBytes = MIN_ENCODER_BYTES,
            expectedSha256 = ENCODER_SHA256,
        ) &&
            ModelDownloadManager.verifyChecksumOrDelete(
                file = decoderFile,
                minimumBytes = MIN_DECODER_BYTES,
                expectedSha256 = DECODER_SHA256,
            ) &&
            ModelDownloadManager.verifyChecksumOrDelete(
                file = vocabFile,
                minimumBytes = MIN_VOCAB_BYTES,
                expectedSha256 = VOCAB_SHA256,
            )
    }

    fun refreshModelState(): WhisperModelState {
        val state = when {
            !hasMinimumModelFiles() -> WhisperModelState.NOT_DOWNLOADED
            hasVerifiedModelFiles() -> WhisperModelState.READY
            else -> WhisperModelState.ERROR
        }
        _modelState.value = state
        return state
    }

    fun isReady(): Boolean = _modelState.value == WhisperModelState.READY && hasMinimumModelFiles()

    /**
     * Download Whisper tiny.en ONNX model files from HuggingFace.
     */
    suspend fun downloadModel(
        wifiOnly: Boolean = false,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            _modelState.value = WhisperModelState.DOWNLOADING
            _downloadProgress.value = 0f
            modelDir.mkdirs()

            val files = listOf(
                ModelDownloadManager.ModelFile(
                    url = ENCODER_URL,
                    targetFile = encoderFile,
                    minimumBytes = MIN_ENCODER_BYTES,
                    estimatedBytes = ENCODER_ESTIMATED_BYTES,
                    maxBytes = ENCODER_ESTIMATED_BYTES,
                    displayName = "Whisper encoder",
                    sha256 = ENCODER_SHA256,
                    checksumRequired = true
                ),
                ModelDownloadManager.ModelFile(
                    url = DECODER_URL,
                    targetFile = decoderFile,
                    minimumBytes = MIN_DECODER_BYTES,
                    estimatedBytes = DECODER_ESTIMATED_BYTES,
                    maxBytes = DECODER_ESTIMATED_BYTES,
                    displayName = "Whisper decoder",
                    sha256 = DECODER_SHA256,
                    checksumRequired = true
                ),
                ModelDownloadManager.ModelFile(
                    url = VOCAB_URL,
                    targetFile = vocabFile,
                    minimumBytes = MIN_VOCAB_BYTES,
                    estimatedBytes = VOCAB_ESTIMATED_BYTES,
                    maxBytes = VOCAB_ESTIMATED_BYTES,
                    displayName = "Whisper vocabulary",
                    sha256 = VOCAB_SHA256,
                    checksumRequired = true
                )
            )

            modelDownloadManager.downloadFiles(
                files = files,
                totalEstimateBytes = TOTAL_ESTIMATED_BYTES,
                connectTimeoutMs = 30_000,
                readTimeoutMs = 60_000,
                wifiOnly = wifiOnly
            ) { progress ->
                _downloadProgress.value = progress.coerceIn(0f, 0.99f)
                onProgress(_downloadProgress.value)
            }

            _downloadProgress.value = 1f
            onProgress(1f)
            if (hasVerifiedModelFiles()) {
                _modelState.value = WhisperModelState.READY
                true
            } else {
                _modelState.value = WhisperModelState.ERROR
                false
            }
        } catch (e: ModelDownloadManager.MeteredNetworkException) {
            _modelState.value = if (hasVerifiedModelFiles()) {
                WhisperModelState.READY
            } else {
                WhisperModelState.NOT_DOWNLOADED
            }
            _downloadProgress.value = 0f
            throw e
        } catch (e: Exception) {
            _modelState.value = if (hasVerifiedModelFiles()) {
                WhisperModelState.READY
            } else {
                WhisperModelState.ERROR
            }
            _downloadProgress.value = 0f
            false
        }
    }

    /**
     * Transcribe audio from a video/audio URI.
     * Returns timed segments with transcribed text.
     */
    suspend fun transcribe(
        uri: Uri,
        onProgress: (Float) -> Unit = {}
    ): List<WhisperSegment> = withContext(Dispatchers.IO) {
        if (!isReady()) return@withContext emptyList()
        if (!hasVerifiedModelFiles()) {
            _modelState.value = WhisperModelState.ERROR
            return@withContext emptyList()
        }

        onProgress(0.05f)

        // Step 1: Decode audio to PCM
        val (pcm, sampleRate) = decodeAudioToPcm(uri) ?: return@withContext emptyList()
        onProgress(0.15f)

        // Step 2: Resample to 16kHz if needed
        val audio16k = if (sampleRate != WhisperMel.SAMPLE_RATE) {
            resample(pcm, sampleRate, WhisperMel.SAMPLE_RATE)
        } else pcm
        onProgress(0.20f)

        // Step 3: Process in 30-second chunks
        val chunkSamples = WhisperMel.N_SAMPLES
        val numChunks = ((audio16k.size + chunkSamples - 1) / chunkSamples).coerceAtLeast(1)
        val allSegments = mutableListOf<WhisperSegment>()

        // Load vocab if not loaded
        if (vocab == null) loadVocab()
        val v = vocab ?: return@withContext emptyList()

        // Create ONNX sessions
        val env = ortEnv ?: OrtEnvironment.getEnvironment().also { ortEnv = it }
        val sessionOpts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
        }

        var encoderSession: OrtSession? = null
        var decoderSession: OrtSession? = null

        try {
            try {
                encoderSession = env.createSession(encoderFile.absolutePath, sessionOpts)
                decoderSession = env.createSession(decoderFile.absolutePath, sessionOpts)
            } catch (e: Exception) {
                Log.e("WhisperEngine", "Failed to create ONNX sessions (model may be corrupt)", e)
                sessionOpts.close()
                return@withContext emptyList()
            }
            sessionOpts.close()

            for (chunk in 0 until numChunks) {
                val chunkStart = chunk * chunkSamples
                val chunkAudio = FloatArray(chunkSamples)
                val copyLen = minOf(chunkSamples, audio16k.size - chunkStart)
                if (copyLen > 0) System.arraycopy(audio16k, chunkStart, chunkAudio, 0, copyLen)

                val chunkOffsetMs = (chunkStart.toLong() * 1000L) / WhisperMel.SAMPLE_RATE

                // Compute mel spectrogram
                val mel = WhisperMel.compute(chunkAudio)
                onProgress(0.20f + 0.3f * (chunk + 0.3f) / numChunks)

                // Run encoder
                val encoderOutput = runEncoder(env, encoderSession, mel)
                    ?: continue
                onProgress(0.20f + 0.3f * (chunk + 0.6f) / numChunks)

                // Run decoder (greedy with timestamps). Always close encoderOutput, even on exception.
                try {
                    val segments = runDecoder(env, decoderSession, encoderOutput, v, chunkOffsetMs)
                    allSegments.addAll(segments)
                } finally {
                    try { encoderOutput.close() } catch (e: Exception) { Log.w("WhisperEngine", "encoderOutput close failed", e) }
                }
                onProgress(0.20f + 0.3f * (chunk + 1f) / numChunks)
            }
        } finally {
            encoderSession?.close()
            decoderSession?.close()
        }

        onProgress(1f)

        // Merge adjacent segments with same text or very close timing
        mergeSegments(allSegments)
    }

    private fun runEncoder(
        env: OrtEnvironment,
        session: OrtSession,
        mel: FloatArray
    ): OnnxTensor? {
        val inputShape = longArrayOf(1, WhisperMel.N_MELS.toLong(), WhisperMel.N_FRAMES.toLong())
        val inputBuffer = FloatBuffer.wrap(mel)
        val inputTensor = OnnxTensor.createTensor(env, inputBuffer, inputShape)

        var results: OrtSession.Result? = null
        return try {
            results = session.run(mapOf("input_features" to inputTensor))
            val output = results.firstOrNull()?.value as? OnnxTensor ?: return null
            // Clone the tensor data before closing results so caller owns a fresh OnnxTensor.
            val data = output.floatBuffer
            val cloned = FloatArray(data.remaining())
            data.get(cloned)
            val shape = output.info.shape
            OnnxTensor.createTensor(env, FloatBuffer.wrap(cloned), shape)
        } catch (e: Exception) {
            Log.w("WhisperEngine", "Encoder run failed", e)
            null
        } finally {
            try { results?.close() } catch (e: Exception) { Log.w("WhisperEngine", "results close failed", e) }
            try { inputTensor.close() } catch (e: Exception) { Log.w("WhisperEngine", "inputTensor close failed", e) }
        }
    }

    private fun runDecoder(
        env: OrtEnvironment,
        session: OrtSession,
        encoderOutput: OnnxTensor,
        vocab: Map<Int, String>,
        chunkOffsetMs: Long
    ): List<WhisperSegment> {
        // Initial tokens: <|startoftranscript|>, <|en|>, <|transcribe|>
        val tokens = mutableListOf(SOT.toLong(), EN.toLong(), TRANSCRIBE.toLong())
        val segments = mutableListOf<WhisperSegment>()
        var lastTimestampMs = 0L

        for (step in 0 until MAX_DECODE_TOKENS) {
            val inputIds = LongBuffer.wrap(tokens.toLongArray())
            val inputShape = longArrayOf(1, tokens.size.toLong())
            var idTensor: OnnxTensor? = OnnxTensor.createTensor(env, inputIds, inputShape)
            var results: OrtSession.Result? = null

            val bestToken: Int
            try {
                results = session.run(mapOf(
                    "input_ids" to idTensor,
                    "encoder_hidden_states" to encoderOutput
                ))

                val logits = results.firstOrNull()?.value as? OnnxTensor
                if (logits == null) break

                // Validate shape before indexing — a malformed model output
                // with fewer than 3 dims (rank < 3) would throw
                // IndexOutOfBoundsException reading shape[2], leaking every
                // tensor accumulated in this decode loop and aborting
                // transcription silently. Bail cleanly instead.
                val shape = logits.info.shape
                if (shape.size < 3) {
                    android.util.Log.e("WhisperEngine", "Decoder logits have unexpected rank ${shape.size}; aborting")
                    break
                }
                val logitsData = logits.floatBuffer
                val vocabSize = shape[2].toInt()
                val seqLen = shape[1].toInt()
                if (vocabSize <= 0 || seqLen <= 0) {
                    android.util.Log.e("WhisperEngine", "Decoder logits have non-positive dims: shape=${shape.toList()}")
                    break
                }

                // Get logits for last token position
                val lastOffset = (seqLen - 1) * vocabSize
                var best = 0
                var bestLogit = -Float.MAX_VALUE
                for (t in 0 until vocabSize) {
                    val l = logitsData.get(lastOffset + t)
                    if (l > bestLogit) {
                        bestLogit = l
                        best = t
                    }
                }
                bestToken = best
            } catch (e: Exception) {
                break
            } finally {
                results?.close()
                idTensor?.close()
                idTensor = null
            }

            // End of text
            if (bestToken == EOT) break

            // No speech detected
            if (bestToken == NO_SPEECH && tokens.size <= 4) break

            tokens.add(bestToken.toLong())

            // Process timestamp tokens
            if (bestToken >= TIMESTAMP_BEGIN) {
                val timestampMs = ((bestToken - TIMESTAMP_BEGIN) * 20).toLong()

                // Collect text between last two timestamps
                val textTokens = mutableListOf<Int>()
                var startTs = lastTimestampMs
                for (i in tokens.indices.reversed()) {
                    val t = tokens[i].toInt()
                    if (t >= TIMESTAMP_BEGIN && tokens[i] != bestToken.toLong()) {
                        startTs = ((t - TIMESTAMP_BEGIN) * 20).toLong()
                        break
                    }
                    if (t < TIMESTAMP_BEGIN && t != SOT && t != EN &&
                        t != TRANSCRIBE && t != NO_TIMESTAMPS && t != NO_SPEECH) {
                        textTokens.add(0, t)
                    }
                }

                if (textTokens.isNotEmpty()) {
                    val text = decodeTokens(textTokens, vocab).trim()
                    if (text.isNotBlank()) {
                        segments.add(WhisperSegment(
                            startMs = chunkOffsetMs + startTs,
                            endMs = chunkOffsetMs + timestampMs,
                            text = text
                        ))
                    }
                }
                lastTimestampMs = timestampMs
            }
        }

        return segments
    }

    private fun decodeTokens(tokenIds: List<Int>, vocab: Map<Int, String>): String {
        val sb = StringBuilder()
        for (id in tokenIds) {
            val text = vocab[id] ?: continue
            // Whisper GPT-2 vocab uses Unicode byte encoding (e.g. "Ġ" = space prefix)
            sb.append(text)
        }
        return decodeGpt2Bytes(sb.toString())
    }

    /**
     * Decode GPT-2 byte-level BPE text back to UTF-8.
     * GPT-2 maps bytes 0-255 to Unicode chars to avoid whitespace issues.
     * "Ġ" (U+0120) represents a space prefix.
     */
    private fun decodeGpt2Bytes(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            val code = ch.code
            when {
                code == 0x0120 -> sb.append(' ') // Ġ = space
                code in 0x0100..0x0200 -> sb.append((code - 0x0100).toChar()) // Mapped bytes
                code == 0x010A -> sb.append('\n') // Ċ = newline
                code == 0x0109 -> sb.append('\t') // ĉ = tab
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun loadVocab() {
        if (!vocabFile.exists()) return
        try {
            val json = JSONObject(vocabFile.readText(Charsets.UTF_8))
            val map = mutableMapOf<Int, String>()
            json.keys().forEach { key ->
                val tokenId = json.getInt(key)
                map[tokenId] = key
            }
            vocab = map
        } catch (e: Exception) {
            Log.w("WhisperEngine", "Failed to load vocab from $vocabFile", e)
        }
    }

    /**
     * Decode audio from URI to mono float32 PCM.
     * Returns (samples, sampleRate) or null on failure.
     */
    private fun decodeAudioToPcm(uri: Uri): Pair<FloatArray, Int>? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)

            var audioIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val tf = extractor.getTrackFormat(i)
                if (tf.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioIndex = i
                    format = tf
                    break
                }
            }
            if (audioIndex < 0 || format == null) return null

            extractor.selectTrack(audioIndex)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            if (sampleRate <= 0 || channels <= 0) return null
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            val decoder = MediaCodec.createDecoderByType(mime)
            val chunks = mutableListOf<FloatArray>()
            var totalSamples = 0

            try {
                decoder.configure(format, null, null, 0)
                decoder.start()
                val bufferInfo = MediaCodec.BufferInfo()
                var eos = false

                while (!eos) {
                    val inIdx = decoder.dequeueInputBuffer(10000)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx) ?: continue
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eos = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                    var outIdx = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                    while (outIdx >= 0) {
                        val outBuf = decoder.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 1) {
                            val buf = outBuf.duplicate()
                            val start = bufferInfo.offset.coerceIn(0, buf.capacity())
                            val rawEnd = (bufferInfo.offset + bufferInfo.size).coerceIn(start, buf.capacity())
                            val end = rawEnd - ((rawEnd - start) % 2)
                            buf.position(start)
                            buf.limit(end)
                            val shortBuf = buf.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val samples = ShortArray(shortBuf.remaining())
                            shortBuf.get(samples)
                            // Mix to mono
                            val mono = FloatArray(samples.size / channels)
                            for (i in mono.indices) {
                                var sum = 0f
                                for (ch in 0 until channels) {
                                    val idx = i * channels + ch
                                    if (idx < samples.size) sum += samples[idx].toFloat()
                                }
                                mono[i] = sum / channels / 32768f
                            }
                            chunks.add(mono)
                            totalSamples += mono.size
                        }
                        decoder.releaseOutputBuffer(outIdx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            eos = true; break
                        }
                        outIdx = decoder.dequeueOutputBuffer(bufferInfo, 0)
                    }
                }
            } finally {
                try { decoder.stop() } catch (e: Exception) { Log.w("WhisperEngine", "Failed to stop decoder", e) }
                decoder.release()
            }

            if (totalSamples == 0) return null

            val allSamples = FloatArray(totalSamples)
            var offset = 0
            for (chunk in chunks) {
                System.arraycopy(chunk, 0, allSamples, offset, chunk.size)
                offset += chunk.size
            }
            return allSamples to sampleRate
        } catch (e: Exception) {
            Log.w("WhisperEngine", "PCM decode failed for $uri", e)
            return null
        } finally {
            extractor.release()
        }
    }

    /**
     * Linear interpolation resampling.
     */
    private fun resample(input: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (srcRate == dstRate) return input
        // A malformed container can report a 0/negative sample rate; without this guard
        // ratio becomes Infinity → outLen overflows → OOM/crash. Skip resampling instead.
        if (srcRate <= 0 || dstRate <= 0) return input
        val ratio = dstRate.toDouble() / srcRate.toDouble()
        val outLen = (input.size * ratio).roundToInt()
        val output = FloatArray(outLen)
        for (i in 0 until outLen) {
            val srcPos = i / ratio
            val srcIdx = srcPos.toInt()
            val frac = (srcPos - srcIdx).toFloat()
            val s0 = input.getOrElse(srcIdx) { 0f }
            val s1 = input.getOrElse(srcIdx + 1) { s0 }
            output[i] = s0 + frac * (s1 - s0)
        }
        return output
    }

    /**
     * Merge segments: combine adjacent segments, remove duplicates.
     */
    private fun mergeSegments(segments: List<WhisperSegment>): List<WhisperSegment> {
        if (segments.size < 2) return segments
        val merged = mutableListOf(segments[0])
        for (i in 1 until segments.size) {
            val prev = merged.last()
            val curr = segments[i]
            // Merge if gap < 200ms and same/similar text continuation
            if (curr.startMs - prev.endMs < 200 && curr.text == prev.text) {
                merged[merged.lastIndex] = prev.copy(endMs = curr.endMs)
            } else {
                merged.add(curr)
            }
        }
        return merged
    }

    fun deleteModel() {
        modelDir.deleteRecursively()
        vocab = null
        _modelState.value = WhisperModelState.NOT_DOWNLOADED
        _downloadProgress.value = 0f
    }

    fun getModelSizeMB(): Long {
        return getModelSizeBytes().div(1024 * 1024)
    }

    fun getModelSizeBytes(): Long {
        return if (modelDir.exists()) {
            modelDir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
        } else 0L
    }
}
