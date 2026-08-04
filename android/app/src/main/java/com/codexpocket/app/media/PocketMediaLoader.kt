package com.codexpocket.app.media

import android.content.Context
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import java.io.File

object PocketMediaLoader {
    const val MAX_MEDIA_CACHE_BYTES = 896L * 1024L * 1024L
    private var loader: ImageLoader? = null

    @Synchronized
    fun get(context: Context): ImageLoader = loader ?: ImageLoader.Builder(context.applicationContext)
        .components { add(VideoFrameDecoder.Factory()) }
        .diskCache {
            DiskCache.Builder()
                .directory(directory(context))
                .maxSizeBytes(MAX_MEDIA_CACHE_BYTES)
                .build()
        }
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()
        .also { loader = it }

    @OptIn(ExperimentalCoilApi::class)
    @Synchronized
    fun clear(context: Context) {
        loader?.diskCache?.clear() ?: directory(context).deleteRecursively()
    }

    fun sizeBytes(context: Context): Long = directory(context)
        .walkTopDown()
        .filter(File::isFile)
        .sumOf(File::length)

    private fun directory(context: Context): File = File(context.cacheDir, "codex-pocket-media")
}
