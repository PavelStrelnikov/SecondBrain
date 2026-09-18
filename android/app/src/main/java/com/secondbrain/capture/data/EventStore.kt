package com.secondbrain.capture.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/**
 * Буфер событий на телефоне. Событие лежит здесь, пока сервер не подтвердил приём.
 * Ключ события уникален: повторная запись того же ключа игнорируется (идемпотентность на обеих сторонах).
 */
class EventStore(context: Context) : SQLiteOpenHelper(context, "events.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE events (event_key TEXT PRIMARY KEY, json TEXT NOT NULL, created_at INTEGER NOT NULL, sent INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX idx_events_sent ON events(sent, created_at)")
        db.execSQL("CREATE TABLE uploaded_recordings (file_key TEXT PRIMARY KEY, created_at INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** @return true, если событие новое. */
    fun put(event: JSONObject): Boolean {
        val cv = ContentValues().apply {
            put("event_key", event.getString("event_key"))
            put("json", event.toString())
            put("created_at", System.currentTimeMillis())
            put("sent", 0)
        }
        return writableDatabase.insertWithOnConflict("events", null, cv, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun pending(limit: Int = 200): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        readableDatabase.query("events", arrayOf("json"), "sent = 0", null, null, null, "created_at ASC", limit.toString())
            .use { c -> while (c.moveToNext()) out.add(JSONObject(c.getString(0))) }
        return out
    }

    fun markSent(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (k in keys) db.execSQL("UPDATE events SET sent = 1 WHERE event_key = ?", arrayOf(k))
            // Отправленное старше 7 дней не нужно: сервер уже хранит правду.
            val cutoff = System.currentTimeMillis() - 7L * 86_400_000L
            db.execSQL("DELETE FROM events WHERE sent = 1 AND created_at < ?", arrayOf<Any>(cutoff))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** @return true, если этой записи ещё не было (её надо загрузить). */
    fun markRecordingSeen(fileKey: String): Boolean {
        val cv = ContentValues().apply {
            put("file_key", fileKey)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insertWithOnConflict("uploaded_recordings", null, cv, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun forgetRecording(fileKey: String) {
        writableDatabase.delete("uploaded_recordings", "file_key = ?", arrayOf(fileKey))
    }

    fun clearRecordingsSeen() {
        writableDatabase.delete("uploaded_recordings", null, null)
    }

    fun countPending(): Long = readableDatabase.compileStatement("SELECT COUNT(*) FROM events WHERE sent = 0").simpleQueryForLong()
    fun countSent(): Long = readableDatabase.compileStatement("SELECT COUNT(*) FROM events WHERE sent = 1").simpleQueryForLong()
}
