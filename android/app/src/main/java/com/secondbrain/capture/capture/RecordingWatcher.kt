package com.secondbrain.capture.capture

import android.content.Context
import android.os.Environment
import android.util.Log
import com.secondbrain.capture.Prefs
import com.secondbrain.capture.data.EventStore
import com.secondbrain.capture.data.Events
import com.secondbrain.capture.net.RecordingUploader
import java.io.File

/**
 * Ищет новые записи звонков напрямую в папках, куда их кладёт звонилка Samsung.
 * Samsung не индексирует записи звонков в общем списке аудио, поэтому читаем файлы напрямую;
 * для этого нужно разрешение «доступ ко всем файлам» (MANAGE_EXTERNAL_STORAGE).
 */
class RecordingWatcher(private val context: Context) {

    fun scan(): Int {
        val prefs = Prefs(context)
        if (!prefs.configured) return 0
        if (!Environment.isExternalStorageManager()) {
            Log.w(TAG, "no all-files access; cannot read call recordings")
            return 0
        }
        val base = Environment.getExternalStorageDirectory()
        val dirs = listOf(
            File(base, "Recordings/Call"),
            File(base, "Call"),
            File(base, "Recordings/Voice Recorder"),
            File(base, "Sounds"),
        )
        val store = EventStore(context)
        val uploader = RecordingUploader(context)
        val cutoff = System.currentTimeMillis() - 3L * 86_400_000L
        var uploaded = 0
        try {
            for (dir in dirs) {
                val files = dir.listFiles() ?: continue
                for (f in files.sortedBy { it.lastModified() }) {
                    if (!f.isFile) continue
                    val name = f.name
                    if (!name.endsWith(".m4a") && !name.endsWith(".mp3") && !name.endsWith(".amr")) continue
                    if (f.lastModified() < cutoff) continue
                    if (!store.markRecordingSeen(name)) continue
                    val durationS = 0  // длительность возьмём из привязанного звонка на сервере
                    val ok = uploader.uploadFile(f, name, Events.isoTime(f.lastModified()), durationS)
                    if (ok) uploaded++ else store.forgetRecording(name)
                }
            }
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
