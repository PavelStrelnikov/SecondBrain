package com.secondbrain.capture.capture

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.CallLog
import android.util.Log
import androidx.core.app.NotificationCompat
import com.secondbrain.capture.App
import com.secondbrain.capture.MainActivity
import com.secondbrain.capture.Prefs
import com.secondbrain.capture.R
import com.secondbrain.capture.data.EventStore
import com.secondbrain.capture.data.Events
import com.secondbrain.capture.work.UploadWorker
import org.json.JSONObject

/**
 * Foreground-служба: держит процесс живым, наблюдает за журналом звонков,
 * раз в 30 минут шлёт пульс, чтобы страница «здоровье» видела, что телефон жив.
 */
class CaptureService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var observer: ContentObserver

    private val heartbeat = object : Runnable {
        override fun run() {
            sendHeartbeat("alive")
            handler.post(scanRecordings)
            handler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                // Журнал обновляется с задержкой после окончания звонка; ждём секунду и читаем.
                handler.removeCallbacks(scanCalls)
                handler.postDelayed(scanCalls, 1500)
            }
        }
        try {
            contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
        } catch (e: SecurityException) {
            Log.w(TAG, "call log observer: no permission", e)
        }
        UploadWorker.schedulePeriodic(this)
        handler.post(scanCalls)
        handler.post(heartbeat)
        sendHeartbeat("service_started")
    }

    private val scanCalls = Runnable {
        val added = CallLogWatcher(this).scan()
        if (added > 0) UploadWorker.uploadNow(this)
        // Файл записи появляется через несколько секунд после конца звонка; сканируем с задержкой.
        handler.postDelayed(scanRecordings, 8000)
        handler.postDelayed(scanRecordings, 30000)
    }

    private val scanRecordings = Runnable {
        Thread { runCatching { RecordingWatcher(this).scan() } }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { contentResolver.unregisterContentObserver(observer) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun sendHeartbeat(state: String) {
        val prefs = Prefs(this)
        val store = EventStore(this)
        try {
            val now = System.currentTimeMillis()
            val bm = getSystemService(BatteryManager::class.java)
            val battery = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val payload = JSONObject().apply {
                put("state", state)
                put("battery", battery)
                put("pending", store.countPending())
            }
            store.put(Events.build("state:$state:" + (now / 60_000L), "app_state", "android", null, now, prefs.deviceId, payload))
        } finally {
            store.close()
        }
        UploadWorker.uploadNow(this)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_brain)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_running))
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val TAG = "brain.service"
        private const val NOTIF_ID = 1
        private const val HEARTBEAT_MS = 30L * 60_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, CaptureService::class.java))
        }
    }
}
