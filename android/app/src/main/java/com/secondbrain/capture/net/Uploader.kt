package com.secondbrain.capture.net

import android.content.Context
import android.util.Log
import com.secondbrain.capture.Prefs
import com.secondbrain.capture.data.EventStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Отправка буфера на ядро пакетами. Успех = сервер ответил 200; тогда события помечаются отправленными. */
class Uploader(private val context: Context) {
    private val prefs = Prefs(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    sealed class Result {
        data class Ok(val sent: Int) : Result()
        data class Fail(val reason: String) : Result()
    }

    fun uploadPending(): Result {
        if (!prefs.configured) return Result.Fail("сервер или токен не заданы")
        val store = EventStore(context)
        var total = 0
        try {
            while (true) {
                val batch = store.pending(200)
                if (batch.isEmpty()) break
                val body = JSONObject().put("events", JSONArray(batch)).toString()
                val req = Request.Builder()
                    .url(prefs.serverUrl + "/api/events")
                    .header("Authorization", "Bearer " + prefs.token)
                    .post(body.toRequestBody(JSON))
                    .build()
                val code = client.newCall(req).execute().use { it.code }
                if (code !in 200..299) {
                    val reason = "HTTP $code"
                    prefs.lastUploadError = reason
                    return Result.Fail(reason)
                }
                store.markSent(batch.map { it.getString("event_key") })
                total += batch.size
            }
            prefs.lastUploadAt = System.currentTimeMillis()
            prefs.lastUploadError = ""
            return Result.Ok(total)
        } catch (e: Exception) {
            Log.w(TAG, "upload failed", e)
            val reason = e.javaClass.simpleName + ": " + (e.message ?: "")
            prefs.lastUploadError = reason
            return Result.Fail(reason)
        } finally {
            store.close()
        }
    }

    /** Проверка связи: GET /api/health без токена, затем пустой пакет для проверки токена. */
    fun ping(): String {
        if (!prefs.configured) return "не задан адрес или токен"
        return try {
            val health = Request.Builder().url(prefs.serverUrl + "/api/health").get().build()
            val healthCode = client.newCall(health).execute().use { it.code }
            if (healthCode !in 200..299) return "сервер ответил HTTP $healthCode"
            // Пустой пакет: сервер вернёт 422 при верном токене и 401 при неверном.
            val probe = Request.Builder()
                .url(prefs.serverUrl + "/api/events")
                .header("Authorization", "Bearer " + prefs.token)
                .post("{}".toRequestBody(JSON))
                .build()
            when (val code = client.newCall(probe).execute().use { it.code }) {
                401 -> "сервер доступен, но токен неверный"
                422 -> "связь есть, токен принят"
                else -> "сервер доступен, ответ HTTP $code"
            }
        } catch (e: Exception) {
            "нет связи: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    companion object {
        private const val TAG = "brain.upload"
        private val JSON = "application/json".toMediaType()
    }
}
