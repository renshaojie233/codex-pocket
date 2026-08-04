package com.codexpocket.app.network

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import org.json.JSONObject

data class UploadedImage(
    val path: String,
    val name: String,
    val mimeType: String,
)

class ImageUploader(private val contentResolver: ContentResolver) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun upload(
        endpoint: String,
        token: String,
        uri: Uri,
        displayName: String,
        mimeType: String,
        sizeBytes: Long?,
    ): UploadedImage = suspendCancellableCoroutine { continuation ->
        val requestBody = object : RequestBody() {
            override fun contentType() = mimeType.toMediaType()

            override fun contentLength(): Long = sizeBytes ?: -1L

            override fun writeTo(sink: BufferedSink) {
                val input = contentResolver.openInputStream(uri)
                    ?: throw FileNotFoundException("无法读取所选图片")
                input.source().use(sink::writeAll)
            }
        }
        val request = Request.Builder()
            .url(uploadUrl(endpoint))
            .header("Authorization", "Bearer $token")
            .header(
                "X-File-Name-Base64",
                Base64.encodeToString(displayName.toByteArray(Charsets.UTF_8), Base64.NO_WRAP),
            )
            .post(requestBody)
            .build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, error: java.io.IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val body = it.body?.string().orEmpty()
                        if (!it.isSuccessful) {
                            val message = runCatching { JSONObject(body).optString("error") }.getOrNull()
                                .orEmpty()
                                .ifBlank { "图片上传失败（HTTP ${it.code}）" }
                            if (continuation.isActive) {
                                continuation.resumeWithException(IllegalStateException(message))
                            }
                            return
                        }
                        val payload = runCatching { JSONObject(body) }.getOrElse { error ->
                            if (continuation.isActive) continuation.resumeWithException(error)
                            return
                        }
                        val path = payload.optString("path")
                        if (path.isBlank()) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(IllegalStateException("Bridge 没有返回图片路径"))
                            }
                            return
                        }
                        if (continuation.isActive) {
                            continuation.resume(
                                UploadedImage(
                                    path = path,
                                    name = payload.optString("name", displayName),
                                    mimeType = payload.optString("mimeType", mimeType),
                                ),
                            )
                        }
                    }
                }
            },
        )
    }

    private fun uploadUrl(endpoint: String): String {
        val bridge = Uri.parse(endpoint)
        val scheme = if (bridge.scheme == "wss") "https" else "http"
        return Uri.Builder()
            .scheme(scheme)
            .encodedAuthority(bridge.encodedAuthority)
            .path("/upload/image")
            .build()
            .toString()
    }
}
