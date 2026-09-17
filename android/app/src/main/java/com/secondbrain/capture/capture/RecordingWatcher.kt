package com.secondbrain.capture.capture

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.secondbrain.capture.Prefs
import com.secondbrain.capture.data.EventStore
import com.secondbrain.capture.data.Events
import com.secondbrain.capture.net.RecordingUploader

/**
 * Ищет новые аудиозаписи звонков в общем хранилище и грузит их на сервер.
 * Samsung кладёт записи в Recordings/Call или в Call; фильтруем по пути.
 * Каждый файл грузим один раз: ключ = display_name, отмечается в базе после успеха.
 */
class RecordingWatcher(private val context: Context) {

    fun scan(): Int {
        val prefs = Prefs(context)
        if (!prefs.configured) return 0
        val store = EventStore(context)
        val uploader = RecordingUploader(context)
        var uploaded = 0
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
        )
        // Записи звонков: путь содержит Call, либо имя начинается с "Call recording".
        val selection =
            "(${MediaStore.Audio.Media.RELATIVE_PATH} LIKE '%Call%' OR " +
                "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE 'Call%') AND " +
                "${MediaStore.Audio.Media.DATE_MODIFIED} > ?"
        // Только за последние 3 дня, чтобы не заливать старый архив.
        val cutoff = (System.currentTimeMillis() / 1000L) - 3L * 86_400L
        try {
            context.contentResolver.query(
                collection, projection, selection, arrayOf(cutoff.toString()),
                "${MediaStore.Audio.Media.DATE_MODIFIED} ASC",
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val iDate = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (c.moveToNext()) {
                    val name = c.getString(iName) ?: continue
                    if (!store.markRecordingSeen(name)) continue  // уже видели
                    val uri: Uri = android.content.ContentUris.withAppendedId(collection, c.getLong(iId))
                    val recordedMs = c.getLong(iDate) * 1000L
                    val durationS = (c.getLong(iDur) / 1000L).toInt()
                    val ok = uploader.upload(uri, name, Events.isoTime(recordedMs), durationS)
                    if (ok) {
                        uploaded++
                    } else {
                        store.forgetRecording(name)  // повторим в следующий раз
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "no audio permission", e)
        } catch (e: Exception) {
            Log.w(TAG, "recording scan failed", e)
        } finally {
            store.close()
        }
        return uploaded
    }

    companion object {
        private const val TAG = "brain.rec"
    }
}
