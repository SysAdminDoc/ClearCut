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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
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
    @ApplicationContext private val context: Context
) {
    private val modelDir = File(context.filesDir, "whisper")
    private val encoderFile = File(modelDir, "encoder_model.onnx")
    private val decoderFile = File(modelDir, "decoder_model.onnx")
    private val vocabFile = File(modelDir, "vocab.json")

    private val _modelState = MutableStateFlow(
        if (encoderFile.exists() && decoderFile.exists() && vocabFile.exists())
            WhisperModelState.READY else WhisperModelState.NOT_DOWNLOADED
    )
    val modelState: StateFlow<WhisperModelState> = _modelState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    @Volatile private var vocab: Map<Int, String>? = null
    @Volatile private var ortEnv: OrtEnvironment? = null

    companion object {
        private const val BASE_URL = "https://huggingface.co/onnx-community/whisper-tiny.en/resolve/main/onnx"
        private const val ENCODER_URL = "$BASE_URL/encoder_model.onnx"
        private const val DECODER_URL = "$BASE_URL/decoder_model.onnx"
        private const val VOCAB_URL = "https://huggingface.co/onnx-community/whisper-tiny.en/resolve/main/vocab.json"

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

        fun estimateModelSizeMB(): Int = 75 // ~40MB encoder + ~35MB decoder
    }

    fun isReady(): Boolean = _modelState.value == WhisperModelState.READY

    /**
     * Download Whisper tiny.en ONNX model files from HuggingFace.
     */
    suspend fun downloadModel(
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            _modelState.value = WhisperModelState.DOWNLOADING
            _downloadProgress.value = 0f
            modelDir.mkdirs()

            val files = listOf(
                ENCODER_URL to encoderFile,
                DECODER_URL to decoderFile,
                VOCAB_URL to vocabFile
            )

            var completedBytes = 0L
            val totalEstimate = estimateModelSizeMB() * 1024L * 1024L

            for ((url, file) in files) {
                if (file.exists() && file.length() > 1000) continue
                val tempFile = File(file.parentFile, "${file.name}.tmp")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 30000
                conn.readTimeout = 60000
                conn.setRequestProperty("User-Agent", "NovaCut/${com.novacut.editor.NovaCutApp.VERSION.removePrefix("v")}")
                try {
                    conn.connect()

                    if (conn.responseCode != 200) {
                        throw Exception("HTTP ${conn.responseCode} for $url")
                    }

                    val fileSize = conn.contentLengthLong
                    conn.inputStream.buffered().use { input ->
                        tempFile.outputStream().buffered().use { output ->
                            val buf = ByteArray(8192)
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                output.write(buf, 0, read)
                                completedBytes += read
                                val progress = if (fileSize > 0) {
                                    completedBytes.toFloat() / maxOf(totalEstimate, completedBytes + 1)
                                } else {
                                    completedBytes.toFloat() / totalEstimate
                                }
                                _downloadProgress.value = progress.coerceIn(0f, 0.99f)
                                onProgress(_downloadProgress.value)
                            }
                        }
                    }
                    if (!tempFile.renameTo(file)) {
                        tempFile.copyTo(file, overwrite = true)
                        tempFile.delete()
                    }
                } catch (e: Exception) {
                    tempFile.delete()
                    throw e
                } finally {
                    conn.disconnect()
                }
            }

            _downloadProgress.value = 1f
            onProgress(1f)
            _modelState.value = WhisperModelState.READY
            true
        } catch (e: Exception) {
            _modelState.value = WhisperModelState.ERROR
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

                // Run decoder (greedy with timestamps)
                val segments = runDecoder(env, decoderSession, encoderOutput, v, chunkOffsetMs)
                encoderOutput.close()
                allSegments.addAll(segments)
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

        return try {
            val results = session.run(mapOf("input_features" to inputTensor))
            val output = results.firstOrNull()?.value
            if (output is OnnxTensor) {
                // Clone the tensor data before closing results
                val data = output.floatBuffer
                val cloned = FloatArray(data.remaining())
                data.get(cloned)
                val shape = output.info.shape
                results.close()
                inputTensor.close()
                OnnxTensor.createTensor(env, FloatBuffer.wrap(cloned), shape)
            } else {
                results.close()
                inputTensor.close()
                null
            }
        } catch (e: Exception) {
            inputTensor.close()
            null
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
            val idTensor = OnnxTensor.createTensor(env, inputIds, inputShape)

            try {
                val results = session.run(mapOf(
                    "input_ids" to idTensor,
                    "encoder_hidden_states" to encoderOutput
                ))

                val logits = results.firstOrNull()?.value as? OnnxTensor
                if (logits == null) {
                    results.close()
                    idTensor.close()
                    break
                }
                val logitsData = logits.floatBuffer
                val vocabSize = logits.info.shape[2].toInt()
                val seqLen = logits.info.shape[1].toInt()

                // Get logits for last token position
                val lastOffset = (seqLen - 1) * vocabSize
                var bestToken = 0
                var bestLogit = -Float.MAX_VALUE
                for (t in 0 until vocabSize) {
                    val l = logitsData.get(lastOffset + t)
                    if (l > bestLogit) {
                        bestLogit = l
                        bestToken = t
                    }
                }

                results.close()
                idTensor.close()

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
            } catch (e: Exception) {
                idTensor.close()
                break
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
            val json = JSONObject(vocabFile.readText())
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
                        if (outBuf != null && bufferInfo.size > 0) {
                            val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
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
        return if (modelDir.exists()) {
            modelDir.listFiles()?.sumOf { it.length() }?.div(1024 * 1024) ?: 0
        } else 0
    }
}
