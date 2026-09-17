package com.secondbrain.capture

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/** Настройки и курсоры. Курсор журнала звонков хранится здесь, чтобы не слать старое после перезапуска. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("brain", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString("server_url", "") ?: ""
        set(v) = sp.edit().putString("server_url", v.trim().trimEnd('/')).apply()

    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) = sp.edit().putString("token", v.trim()).apply()

    val deviceId: String
        get() {
            val existing = sp.getString("device_id", null)
            if (existing != null) return existing
            val id = "galaxy-" + UUID.randomUUID().toString().take(8)
            sp.edit().putString("device_id", id).apply()
            return id
        }

    /** Последний обработанный _ID в CallLog. -1 = ещё ни разу не читали. */
    var lastCallId: Long
        get() = sp.getLong("last_call_id", -1L)
        set(v) = sp.edit().putLong("last_call_id", v).apply()

    var lastUploadAt: Long
        get() = sp.getLong("last_upload_at", 0L)
        set(v) = sp.edit().putLong("last_upload_at", v).apply()

    var lastUploadError: String
        get() = sp.getString("last_upload_error", "") ?: ""
        set(v) = sp.edit().putString("last_upload_error", v).apply()

    val configured: Boolean get() = serverUrl.isNotBlank() && token.isNotBlank()
}
