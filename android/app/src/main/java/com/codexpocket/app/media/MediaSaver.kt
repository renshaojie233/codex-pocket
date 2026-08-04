package com.codexpocket.app.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.codexpocket.app.model.MediaAttachment
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object MediaSaver {
    private val client = OkHttpClient()

    suspend fun save(context: Context, attachment: MediaAttachment, source: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(source).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("下载失败（${response.code}）")
                val body = response.body ?: error("媒体文件为空")
                val responseMime = response.header("Content-Type")?.substringBefore(';')
                val mimeType = attachment.mimeType.ifBlank {
                    responseMime ?: mimeFromName(attachment.name) ?: defaultMime(attachment.kind)
                }
                val displayName = safeFileName(attachment.name, mimeType, attachment.kind)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveWithMediaStore(context, attachment.kind, displayName, mimeType) { output ->
                        body.byteStream().use { input -> input.copyTo(output) }
                    }
                } else {
                    saveLegacy(context, attachment.kind, displayName, mimeType) { file ->
                        file.outputStream().use { output ->
                            body.byteStream().use { input -> input.copyTo(output) }
                        }
                    }
                }
                displayName
            }
        }

    private fun saveWithMediaStore(
        context: Context,
        kind: String,
        displayName: String,
        mimeType: String,
        write: (java.io.OutputStream) -> Unit,
    ) {
        val (collection, relativePath) = when (kind) {
            "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to
                "${Environment.DIRECTORY_MOVIES}/Codex Pocket"
            "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to
                "${Environment.DIRECTORY_MUSIC}/Codex Pocket"
            else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to
                "${Environment.DIRECTORY_PICTURES}/Codex Pocket"
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("无法创建相册文件")
        try {
            resolver.openOutputStream(uri)?.use(write) ?: error("无法写入相册文件")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(
        context: Context,
        kind: String,
        displayName: String,
        mimeType: String,
        write: (File) -> Unit,
    ) {
        val directoryName = when (kind) {
            "video" -> Environment.DIRECTORY_MOVIES
            "audio" -> Environment.DIRECTORY_MUSIC
            else -> Environment.DIRECTORY_PICTURES
        }
        val directory = File(
            Environment.getExternalStoragePublicDirectory(directoryName),
            "Codex Pocket",
        )
        if (!directory.exists() && !directory.mkdirs()) error("无法创建媒体文件夹")
        val file = uniqueFile(directory, displayName)
        write(file)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(mimeType),
            null,
        )
    }

    private fun uniqueFile(directory: File, displayName: String): File {
        var candidate = File(directory, displayName)
        if (!candidate.exists()) return candidate
        val dot = displayName.lastIndexOf('.')
        val base = if (dot > 0) displayName.substring(0, dot) else displayName
        val extension = if (dot > 0) displayName.substring(dot) else ""
        var index = 2
        while (candidate.exists()) {
            candidate = File(directory, "$base ($index)$extension")
            index += 1
        }
        return candidate
    }

    private fun safeFileName(name: String, mimeType: String, kind: String): String {
        val cleaned = name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^\\p{L}\\p{N}._() -]"), "_")
            .trim().take(120)
            .ifBlank { "codex-${System.currentTimeMillis()}" }
        if (cleaned.substringAfterLast('.', "").isNotBlank()) return cleaned
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?: when (kind) {
                "video" -> "mp4"
                "audio" -> "m4a"
                else -> "jpg"
            }
        return "$cleaned.${extension.lowercase(Locale.ROOT)}"
    }

    private fun mimeFromName(name: String): String? {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension.takeIf(String::isNotBlank)?.let {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(it)
        }
    }

    private fun defaultMime(kind: String): String = when (kind) {
        "video" -> "video/mp4"
        "audio" -> "audio/mp4"
        else -> "image/jpeg"
    }
}
