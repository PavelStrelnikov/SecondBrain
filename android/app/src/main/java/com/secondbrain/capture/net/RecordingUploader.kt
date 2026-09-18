package com.secondbrain.capture.net

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import com.secondbrain.capture.Prefs
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Грузит один аудиофайл на /api/recordings как multipart. Успех = сервер ответил 2xx. */
class RecordingUploader(private val context: Context) {
    private val prefs = Prefs(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun uploadFile(file: File, filename: String, recordedAtIso: String, durationS: Int): Boolean =
        try {
            upload(file.readBytes(), filename, recordedAtIso, durationS)
        } catch (e: Exception) {
            Log.w("brain.recup", "read failed for ${file.absolutePath}", e)
            false
        }

    fun upload(uri: Uri, filename: String, recordedAtIso: String, durationS: Int): Boolean {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
        return upload(bytes, filename, recordedAtIso, durationS)
    }

    private fun upload(bytes: ByteArray, filename: String, recordedAtIso: String, durationS: Int): Boolean {
        return try {
            val media = "audio/*".toMediaTypeOrNull()
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", filename, bytes.toRequestBody(media))
                .addFormDataPart("file_key", filename)
                .addFormDataPart("recorded_at", recordedAtIso)
                .addFormDataPart("duration_s", durationS.toString())
                .build()
            val req = Request.Builder()
                .url(prefs.serverUrl + "/api/recordings")
                .header("Authorization", "Bearer " + prefs.token)
                .post(body)
                .build()
            val code = client.newCall(req).execute().use { it.code }
            code in 200..299
        } catch (e: Exception) {
            Log.w("brain.recup", "upload failed for $filename", e)
            false
        }
    }
}
