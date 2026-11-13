package com.lumen.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.lumen.tv.data.Http
import com.lumen.tv.data.Store
import com.lumen.tv.update.Updater

class LumenApp : Application(), ImageLoaderFactory {
    /** One store for the whole process, shared by the UI and the update installer. */
    val store: Store by lazy { Store(this) }
    val updater: Updater by lazy { Updater(this, store) }

    override fun onCreate() {
        super.onCreate()
        // Save the stack trace of any crash so it can be shown on the next launch.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val text = buildString {
                    append("Lumen TV ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL}\n")
                    append("Thread: ${thread.name}\n\n")
                    append(android.util.Log.getStackTraceString(error))
                }
                crashFile(this).writeText(text)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(Http.client)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(250L * 1024 * 1024).build() }
        .respectCacheHeaders(false) // provider logos rarely send sensible cache headers
        .crossfade(true)
        .build()
}

fun crashFile(context: android.content.Context) = java.io.File(context.filesDir, "last_crash.txt")
