package com.secondbrain.capture.capture

import android.content.Context
import android.provider.CallLog
import android.util.Log
import com.secondbrain.capture.Prefs
import com.secondbrain.capture.data.EventStore
import com.secondbrain.capture.data.Events
import org.json.JSONObject

/**
 * Читает новые строки журнала звонков после последнего обработанного _ID.
 * Первый запуск берёт только звонки за последние 24 часа, чтобы не заливать историю.
 */
class CallLogWatcher(private val context: Context) {
    private val prefs = Prefs(context)

    /** @return сколько новых событий записано в буфер. */
    fun scan(): Int {
        val projection = arrayOf(
            CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE,
            CallLog.Calls.DURATION, CallLog.Calls.CACHED_NAME, CallLog.Calls.PHONE_ACCOUNT_ID,
        )
        val lastId = prefs.lastCallId
        val selection: String
        val args: Array<String>
        if (lastId < 0) {
            selection = "${CallLog.Calls.DATE} > ?"
            args = arrayOf((System.currentTimeMillis() - 24L * 3_600_000L).toString())
        } else {
            selection = "${CallLog.Calls._ID} > ?"
            args = arrayOf(lastId.toString())
        }

        val store = EventStore(context)
        var added = 0
        var maxId = lastId
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI, projection, selection, args, "${CallLog.Calls._ID} ASC",
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(CallLog.Calls._ID)
                val iNum = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val iType = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val iDate = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val iDur = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val iName = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val iAcc = c.getColumnIndexOrThrow(CallLog.Calls.PHONE_ACCOUNT_ID)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val type = c.getInt(iType)
                    val direction = when (type) {
                        CallLog.Calls.INCOMING_TYPE, CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE, CallLog.Calls.VOICEMAIL_TYPE -> "missed"
                        CallLog.Calls.REJECTED_TYPE -> "rejected"
                        CallLog.Calls.BLOCKED_TYPE -> "blocked"
                        else -> null
                    }
                    val date = c.getLong(iDate)
                    val payload = JSONObject().apply {
                        put("number", c.getString(iNum) ?: "")
                        put("duration_s", c.getLong(iDur))
                        put("cached_name", c.getString(iName) ?: JSONObject.NULL)
                        put("sim", c.getString(iAcc) ?: JSONObject.NULL)
                        put("call_log_id", id)
                        put("raw_type", type)
                    }
                    val ev = Events.build("call:$id", "call", "android", direction, date, prefs.deviceId, payload)
                    if (store.put(ev)) added++
                    if (id > maxId) maxId = id
                }
            }
            if (maxId != lastId) prefs.lastCallId = maxId
        } catch (e: SecurityException) {
            Log.w(TAG, "no READ_CALL_LOG permission", e)
        } finally {
            store.close()
        }
        return added
    }

    companion object {
        private const val TAG = "brain.calls"
    }
}
