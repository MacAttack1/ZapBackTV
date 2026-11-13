package com.lumen.tv.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    // Deliberately unchanged across the rename: providers can filter by user agent, and this one is known to work.
    const val DEFAULT_UA = "Lumen/1.0 (Linux; Android) ExoPlayerLib"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    fun get(url: String, userAgent: String? = null): Response {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent?.takeIf { it.isNotBlank() } ?: DEFAULT_UA)
            .header("Accept", "*/*")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw IOException(
                when (code) {
                    401, 403 -> "Access denied by the server (HTTP $code). Check your credentials."
                    404 -> "Not found (HTTP 404). Check the address."
                    in 500..599 -> "The provider's server had an error (HTTP $code)."
                    else -> "Request failed (HTTP $code)."
                }
            )
        }
        return response
    }

    fun getString(url: String, userAgent: String? = null): String =
        get(url, userAgent).use { it.body?.string() ?: "" }

    /**
     * Downloads to a temp file first so a failed refresh never destroys the cache.
     * [onProgress] receives (bytesSoFar, totalBytes or -1), throttled to a few updates a second.
     */
    fun download(url: String, userAgent: String?, target: File, onProgress: ((Long, Long) -> Unit)? = null) {
        val tmp = File(target.parentFile, target.name + ".part")
        get(url, userAgent).use { r ->
            val body = r.body ?: throw IOException("Empty response")
            val total = body.contentLength()
            var read = 0L
            var last = 0L
            tmp.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        val now = System.currentTimeMillis()
                        if (onProgress != null && now - last > 150) { last = now; onProgress(read, total) }
                    }
                }
            }
            onProgress?.invoke(read, total)
        }
        if (tmp.length() == 0L) { tmp.delete(); throw IOException("The server returned an empty playlist") }
        if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
    }
}

/** Reports how far through a file a parser has read, as a 0..1 fraction. */
class ProgressInputStream(
    inner: java.io.InputStream,
    private val total: Long,
    private val onFraction: (Float) -> Unit,
) : java.io.FilterInputStream(inner) {
    private var read = 0L
    private var last = 0L

    private fun tick(n: Int) {
        if (n <= 0 || total <= 0) return
        read += n
        val now = System.currentTimeMillis()
        if (now - last > 150) { last = now; onFraction((read.toFloat() / total).coerceIn(0f, 1f)) }
    }

    override fun read(): Int = super.read().also { if (it >= 0) tick(1) }
    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { tick(it) }
}

fun formatBytes(b: Long): String = when {
    b >= 1_000_000_000 -> String.format(java.util.Locale.US, "%.1f GB", b / 1e9)
    b >= 1_000_000 -> String.format(java.util.Locale.US, "%.1f MB", b / 1e6)
    b >= 1_000 -> String.format(java.util.Locale.US, "%.0f KB", b / 1e3)
    else -> "$b B"
}
