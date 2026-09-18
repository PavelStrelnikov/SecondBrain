package com.secondbrain.capture.capture

import android.content.Context
import android.os.Environment
import android.util.Log
import com.secondbrain.capture.Prefs
import com.secondbrain.capture.data.EventStore
import com.secondbrain.capture.data.Events
import com.secondbrain.capture.net.RecordingUploader
import org.json.JSONObject
import java.io.File

/**
 * Читает записи звонков напрямую из папок звонилки Samsung (нужен доступ ко всем файлам).
 * Пишет диагностическое событие app_state на сервер, чтобы на странице «здоровье» было видно,
 * что именно увидел сканер: какие папки есть, сколько файлов, сколько отправлено, первая ошибка.
 */
class RecordingWatcher(private val context: Context) {

    fun scan(force: Boolean = false): Int {
        val prefs = Prefs(context)
        if (!prefs.configured) return 0
        val store = EventStore(context)
        val uploader = RecordingUploader(context)
        val manager = Environment.isExternalStorageManager()
        val base = Environment.getExternalStorageDirectory()
        val dirs = listOf(
            File(base, "Recordings/Call"),
            File(base, "Call"),
            File(base, "Recordings/Voice Recorder"),
            File(base, "Sounds"),
        )
        val cutoff = System.currentTimeMillis() - 3L * 86_400_000L
        var found = 0
        var uploaded = 0
        var seen = 0
        val dirsPresent = ArrayList<String>()
        var firstError = ""
        try {
            if (force) store.clearRecordingsSeen()
            if (manager) {
                for (dir in dirs) {
                    val files = dir.listFiles() ?: continue
                    dirsPresent.add(dir.name + "=" + files.size)
                    for (f in files.sortedBy { it.lastModified() }) {
                        try {
                            if (!f.isFile) continue
                            val n = f.name.lowercase()
                            if (!n.endsWith(".m4a") && !n.endsWith(".mp3") && !n.endsWith(".amr")) continue
                            if (f.lastModified() < cutoff) continue
                            found++
                            if (!store.markRecordingSeen(f.name)) { seen++; continue }
                            val ok = uploader.uploadFile(f, f.name, Events.isoTime(f.lastModified()), 0)
                            if (ok) uploaded++ else { store.forgetRecording(f.name); if (firstError.isEmpty()) firstError = "upload:" + f.name }
                        } catch (e: Exception) {
                            if (firstError.isEmpty()) firstError = (e.javaClass.simpleName) + ":" + f.name
                            Log.w(TAG, "file failed ${f.name}", e)
                        }
                    }
                }
            } else {
                firstError = "no all-files access"
            }
            // Диагностика на сервер, чтобы было видно на странице здоровья.
            val payload = JSONObject().apply {
                put("state", "rec_scan")
                put("manager", manager)
                put("dirs", dirsPresent.joinToString(","))
                put("found", found)
                put("uploaded", uploaded)
                put("already_seen", seen)
                if (firstError.isNotEmpty()) put("error", firstError)
            }
            val now = System.currentTimeMillis()
            store.put(Events.build("recscan:" + (now / 60_000L), "app_state", "android", null, now, prefs.deviceId, payload))
        } catch (e: Exception) {
            Log.w(TAG, "recording scan failed", e)
        } finally {
            store.close()
        }
        com.secondbrain.capture.work.UploadWorker.uploadNow(context)
        return uploaded
    }

    companion object {
        private const val TAG = "brain.rec"
    }
}
