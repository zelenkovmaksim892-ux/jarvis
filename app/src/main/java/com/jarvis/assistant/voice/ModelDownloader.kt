package com.jarvis.assistant.voice

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI

/** Downloads and unpacks the on-device voice models with bounded, traversal-safe extraction. */
object ModelDownloader {

    private const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024
    private const val MAX_EXPANDED_BYTES = 2L * 1024 * 1024 * 1024
    private const val MAX_ARCHIVE_ENTRIES = 10_000
    private const val MAX_REDIRECTS = 6
    private val ALLOWED_DOWNLOAD_HOSTS = setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com")

    private val downloadClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)
            // Never allow an HTTPS model download to be downgraded to plain HTTP.
            .followSslRedirects(false)
            .build()
    }

    /** The "Hey Jarvis" keyword model: a 17 MB download, of which only ~5 MB of int8 files are kept. */
    fun downloadWakeWord(context: Context, progress: (Int) -> Unit) {
        download(
            context, WakeModel.URL, WakeModel.dir(context), progress, ::openTarBz2,
            expectedSize = WakeModel.ARCHIVE_SIZE,
            expectedSha256 = null, // Upstream release does not publish a machine-readable asset digest.
        )
        prune(WakeModel.dir(context), WakeModel.KEEP_FILES)
    }

    /** Offline speech recognition for commands (57 MB download). */
    fun downloadSpeech(context: Context, progress: (Int) -> Unit) {
        download(
            context, SpeechModel.URL, SpeechModel.dir(context), progress, ::openTarBz2,
            expectedSize = SpeechModel.ARCHIVE_SIZE,
            expectedSha256 = SpeechModel.ARCHIVE_SHA256,
        )
        prune(SpeechModel.dir(context), SpeechModel.KEEP_FILES)
    }

    /** Deletes everything in [dir] except [keep] (sample audio, unused model variants). */
    internal fun prune(dir: File, keep: Set<String>) =
        dir.walkBottomUp().filter { it != dir && it.name !in keep }.forEach { it.delete() }

    /** The ~100 MB Kokoro voice. */
    fun downloadKokoro(context: Context, progress: (Int) -> Unit) =
        download(
            context, Kokoro.MODEL_URL, Kokoro.modelDir(context), progress, ::openTarBz2,
            expectedSize = Kokoro.ARCHIVE_SIZE,
            expectedSha256 = Kokoro.ARCHIVE_SHA256,
        )

    private fun download(
        context: Context,
        url: String,
        target: File,
        progress: (Int) -> Unit,
        open: (InputStream) -> ArchiveInputStream<*>,
        expectedSize: Long,
        expectedSha256: String?,
    ) {
        val parsed = runCatching { URI(url) }.getOrNull()
            ?: throw IOException("Invalid model download URL")
        if (!parsed.scheme.equals("https", ignoreCase = true) ||
            parsed.host.isNullOrBlank() || parsed.host.lowercase() !in ALLOWED_DOWNLOAD_HOSTS ||
            parsed.userInfo != null || parsed.fragment != null
        ) throw IOException("Model downloads must use an allowlisted HTTPS host without embedded credentials")

        val tmp = File(context.cacheDir, target.name + ".download")
        try {
            executeAllowlistedDownload(url).use { resp ->
                if (!resp.isSuccessful) throw IOException("Download failed: HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("Empty download")
                val total = body.contentLength()
                if (total > MAX_ARCHIVE_BYTES || (total >= 0 && total != expectedSize)) {
                    throw IOException("Model archive has an unexpected size")
                }
                body.byteStream().use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var last = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            done += n
                            if (done > MAX_ARCHIVE_BYTES || done > expectedSize) {
                                throw IOException("Model archive exceeds its expected size")
                            }
                            out.write(buf, 0, n)
                            val pct = if (total > 0) (done * 90 / total).toInt().coerceIn(0, 90) else 0
                            if (pct != last) { last = pct; progress(pct) }
                        }
                    }
                }
            }
            verifyArchive(tmp, expectedSize, expectedSha256)
            unpack(tmp, target, open)
        } finally {
            tmp.delete()
        }
        progress(100)
    }


    /**
     * Follow redirects manually. Validate each destination before sending a request to it.
     * Checking resp.request.url after automatic redirects is too late.
     */
    internal fun executeAllowlistedDownload(url: String): okhttp3.Response {
        var current = validateDownloadUrl(url)
        repeat(MAX_REDIRECTS + 1) { hop ->
            val response = downloadClient.newCall(Request.Builder().url(current).build()).execute()
            if (response.code !in setOf(301, 302, 303, 307, 308)) {
                if (!response.request.url.isHttps || response.request.url.host !in ALLOWED_DOWNLOAD_HOSTS) {
                    response.close()
                    throw IOException("Refusing an untrusted model download destination")
                }
                return response
            }
            val location = response.header("Location")
            response.close()
            if (hop == MAX_REDIRECTS || location.isNullOrBlank()) {
                throw IOException("Too many redirects or redirect without a location")
            }
            val next = current.resolve(location) ?: throw IOException("Invalid model download redirect")
            current = validateDownloadUrl(next.toString())
        }
        throw IOException("Too many redirects")
    }

    internal fun validateDownloadUrl(value: String): okhttp3.HttpUrl {
        val parsed = runCatching { URI(value) }.getOrNull() ?: throw IOException("Invalid model download URL")
        if (!parsed.scheme.equals("https", ignoreCase = true) || parsed.host.isNullOrBlank() ||
            parsed.host.lowercase() !in ALLOWED_DOWNLOAD_HOSTS || parsed.userInfo != null || parsed.fragment != null
        ) throw IOException("Model downloads must use an allowlisted HTTPS host without embedded credentials")
        return runCatching { okhttp3.HttpUrl.get(value) }.getOrElse { throw IOException("Invalid model download URL") }
    }

    /** Checks a downloaded model archive against a pinned size and, when available, a trusted SHA-256. */
    internal fun verifyArchive(file: File, expectedSize: Long, expectedSha256: String?) {
        if (!file.isFile || file.length() != expectedSize) {
            throw IOException("Model archive size does not match the pinned release metadata")
        }
        if (expectedSha256 != null) {
            if (!Regex("^[a-fA-F0-9]{64}$").matches(expectedSha256)) {
                throw IOException("Invalid pinned model SHA-256")
            }
            val actual = sha256Of(file)
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                throw IOException("Model archive SHA-256 mismatch")
            }
        }
    }

    private fun sha256Of(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Unpacks [file] into [target], dropping the archive's top-level folder. */
    internal fun unpack(file: File, target: File, open: (InputStream) -> ArchiveInputStream<*>) {
        val parent = target.absoluteFile.parentFile ?: throw IOException("Invalid model target directory")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create model directory")
        val staging = File(parent, target.name + ".new").apply {
            if (exists() && !deleteRecursively()) throw IOException("Could not clear model staging directory")
            if (!mkdirs()) throw IOException("Could not create model staging directory")
        }
        val backup = File(parent, target.name + ".old")
        val root = staging.canonicalPath + File.separator
        var entryCount = 0
        var expandedBytes = 0L

        try {
            open(file.inputStream()).use { archive ->
                while (true) {
                    val entry = archive.nextEntry ?: break
                    entryCount++
                    if (entryCount > MAX_ARCHIVE_ENTRIES) throw IOException("Model archive contains too many entries")

                    val name = entry.name
                    // Treat both slash styles as separators while validating every component.
                    // Do not strip the archive root until traversal checks have passed.
                    val normalizedName = name.replace('\\', '/')
                    if (name.isBlank() || name.contains('\u0000') || name.contains('\\') ||
                        normalizedName.startsWith("/") || Regex("^[A-Za-z]:").containsMatchIn(normalizedName)
                    ) throw IOException("Unsafe model archive path")
                    val segments = normalizedName.trimEnd('/').split('/')
                    if (segments.isEmpty() || segments.any { it == ".." || it == "." || it.isEmpty() }) {
                        throw IOException("Unsafe model archive path")
                    }
                    val relative = segments.drop(1).joinToString("/")
                    if (relative.isEmpty()) continue

                    val output = File(staging, relative)
                    val canonical = output.canonicalPath
                    if (!canonical.startsWith(root)) throw IOException("Model archive entry escapes staging directory")
                    if (entry.isDirectory) {
                        if (!output.exists() && !output.mkdirs()) throw IOException("Could not create model directory")
                        continue
                    }

                    output.parentFile?.let { if (!it.exists() && !it.mkdirs()) throw IOException("Could not create model directory") }
                    output.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = archive.read(buffer)
                            if (count < 0) break
                            expandedBytes += count
                            if (expandedBytes > MAX_EXPANDED_BYTES) {
                                throw IOException("Expanded model archive exceeds the safety limit")
                            }
                            out.write(buffer, 0, count)
                        }
                    }
                }
            }

            // Keep the previous working model until the new one is completely extracted.
            if (backup.exists() && !backup.deleteRecursively()) throw IOException("Could not clear previous model backup")
            if (target.exists() && !target.renameTo(backup)) throw IOException("Could not preserve the previous model")
            if (!staging.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                throw IOException("Could not install model")
            }
            backup.deleteRecursively()
        } catch (e: Exception) {
            if (!target.exists() && backup.exists()) backup.renameTo(target)
            throw e
        } finally {
            staging.deleteRecursively()
        }
    }

    internal fun openTarBz2(input: InputStream): ArchiveInputStream<*> =
        TarArchiveInputStream(BZip2CompressorInputStream(input.buffered()))
}
