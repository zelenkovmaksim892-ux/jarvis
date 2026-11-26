package com.jarvis.assistant.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Offline Russian Piper voice (Ruslan, medium) powered by sherpa-onnx.
 * The model is downloaded separately from the APK and verified before extraction.
 */
object RussianVoice {
    private const val TAG = "RussianVoice"
    const val MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-ruslan-medium.tar.bz2"
    const val ARCHIVE_SIZE = 67_210_684L
    const val ARCHIVE_SHA256 = "0690b1cad01f86e8db9ba988af24898bdc1af774e23cb2e46b9c730269b6fd83"
    private const val IDLE_UNLOAD_MS = 600_000L
    private const val MODEL_FILE = "ru_RU-ruslan-medium.onnx"

    private val main = Handler(Looper.getMainLooper())
    private val worker: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "jarvis-russian-tts").apply { priority = Thread.MAX_PRIORITY } }

    @Volatile private var tts: OfflineTts? = null
    @Volatile var lastError: String? = null
        private set
    @Volatile var failed: Boolean = false
        private set
    private var unloadTask: ScheduledFuture<*>? = null

    fun modelDir(context: Context) = File(context.applicationContext.filesDir, "russian-voice")

    fun isInstalled(context: Context): Boolean {
        val dir = modelDir(context)
        return File(dir, MODEL_FILE).isFile &&
            File(dir, "tokens.txt").isFile &&
            File(dir, "espeak-ng-data").isDirectory
    }

    fun warmUp(context: Context) {
        val app = context.applicationContext
        if (!isInstalled(app) || failed) return
        worker.execute { runCatching { engine(app) }; scheduleUnload() }
    }

    fun speak(
        context: Context,
        text: String,
        speed: Float,
        onDone: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val app = context.applicationContext
        val ticket = AudioOut.interrupt()
        worker.execute {
            try {
                check(isInstalled(app)) { "Русская голосовая модель ещё не скачана" }
                val engine = engine(app) ?: error(lastError ?: "Не удалось загрузить русскую голосовую модель")
                val playback = AudioOut.Playback(ticket)
                val sampleRate = engine.sampleRate()
                val pieces = SpeechChunks.split(text, first = 90, max = 180)
                for (piece in pieces) {
                    if (!AudioOut.isCurrent(ticket)) break
                    engine.generateWithCallback(piece, 0, speed.coerceIn(0.75f, 1.25f)) { samples ->
                        if (!AudioOut.isCurrent(ticket)) 0
                        else if (playback.write(resample(samples, sampleRate))) 1
                        else 0
                    }
                }
                playback.finish()
                scheduleUnload()
                if (AudioOut.isCurrent(ticket)) main.post { onDone() }
            } catch (e: Throwable) {
                lastError = e.message ?: e.javaClass.simpleName
                Log.e(TAG, "Russian speech failed", e)
                if (AudioOut.isCurrent(ticket)) main.post { onError(e) }
            }
        }
    }

    fun stop() {
        AudioOut.stop()
    }

    fun unload() {
        worker.execute {
            unloadTask?.cancel(false)
            tts?.release()
            tts = null
        }
    }

    private fun engine(context: Context): OfflineTts? {
        tts?.let { return it }
        if (failed) return null
        return try {
            val dir = modelDir(context)
            OfflineTts(
                config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model = File(dir, MODEL_FILE).absolutePath,
                            tokens = File(dir, "tokens.txt").absolutePath,
                            dataDir = File(dir, "espeak-ng-data").absolutePath,
                        ),
                        numThreads = 2,
                        debug = false,
                        provider = "cpu",
                    ),
                    maxNumSentences = 1,
                ),
            ).also {
                tts = it
                failed = false
                lastError = null
                Log.i(TAG, "Russian Piper voice loaded; sampleRate=${it.sampleRate()}")
            }
        } catch (e: Throwable) {
            failed = true
            lastError = e.message ?: e.javaClass.simpleName
            Log.e(TAG, "Could not load Russian Piper voice", e)
            null
        }
    }

    private fun scheduleUnload() {
        unloadTask?.cancel(false)
        unloadTask = worker.schedule({
            tts?.release()
            tts = null
            Log.i(TAG, "unloaded after idle")
        }, IDLE_UNLOAD_MS, TimeUnit.MILLISECONDS)
    }

    /** Piper Ruslan produces 22.05 kHz audio; the shared player runs at 24 kHz. */
    private fun resample(input: FloatArray, sourceRate: Int): FloatArray {
        if (sourceRate == AudioOut.SAMPLE_RATE || input.isEmpty()) return input
        val ratio = sourceRate.toDouble() / AudioOut.SAMPLE_RATE
        val count = (input.size / ratio).toInt().coerceAtLeast(1)
        return FloatArray(count) { i ->
            val position = i * ratio
            val left = position.toInt().coerceIn(0, input.lastIndex)
            val right = (left + 1).coerceAtMost(input.lastIndex)
            val fraction = (position - left).toFloat()
            input[left] + (input[right] - input[left]) * fraction
        }
    }
}
