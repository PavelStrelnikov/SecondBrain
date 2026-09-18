package com.secondbrain.capture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.secondbrain.capture.capture.BootReceiver
import com.secondbrain.capture.capture.CaptureService
import com.secondbrain.capture.capture.RecordingWatcher
import com.secondbrain.capture.data.EventStore
import com.secondbrain.capture.net.Uploader
import com.secondbrain.capture.work.UploadWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private val io = Executors.newSingleThreadExecutor()

    private val runtimePerms = arrayOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.POST_NOTIFICATIONS,
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        val serverUrl = findViewById<EditText>(R.id.serverUrl)
        val token = findViewById<EditText>(R.id.token)
        val connStatus = findViewById<TextView>(R.id.connStatus)
        serverUrl.setText(prefs.serverUrl)
        token.setText(prefs.token)

        findViewById<Button>(R.id.save).setOnClickListener {
            prefs.serverUrl = serverUrl.text.toString()
            prefs.token = token.text.toString()
            connStatus.text = "проверяю…"
            io.execute {
                val result = Uploader(this).ping()
                runOnUiThread {
                    connStatus.text = result
                    if (prefs.configured) startServiceIfAllowed()
                    refresh()
                }
            }
        }

        findViewById<Button>(R.id.permRuntime).setOnClickListener {
            ActivityCompat.requestPermissions(this, runtimePerms, 1)
        }
        findViewById<Button>(R.id.permNotifListener).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.permAudio).setOnClickListener {
            // Записи звонков Samsung читаются только через доступ ко всем файлам.
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName"),
                    ),
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
        findViewById<Button>(R.id.permBattery).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
            )
        }
        findViewById<Button>(R.id.sendNow).setOnClickListener {
            UploadWorker.uploadNow(this)
            io.execute {
                Uploader(this).uploadPending()
                val recs = runCatching { RecordingWatcher(this).scan() }.getOrDefault(0)
                runOnUiThread {
                    if (recs > 0) android.widget.Toast.makeText(this, "Записей отправлено: $recs", android.widget.Toast.LENGTH_SHORT).show()
                    refresh()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        if (prefs.configured) startServiceIfAllowed()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
        if (prefs.configured) startServiceIfAllowed()
    }

    private fun hasRuntimePerms(): Boolean =
        runtimePerms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    private fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun hasAudioPerm(): Boolean = Environment.isExternalStorageManager()

    private fun batteryUnrestricted(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun startServiceIfAllowed() {
        if (hasRuntimePerms()) CaptureService.start(this)
        if (hasNotificationAccess()) BootReceiver.rebindListener(this)
    }

    private fun refresh() {
        val ok = "✓"
        val no = "✗"
        findViewById<TextView>(R.id.permStatus).text = listOf(
            "${if (hasRuntimePerms()) ok else no} звонки и уведомления приложения",
            "${if (hasNotificationAccess()) ok else no} доступ к уведомлениям WhatsApp",
            "${if (batteryUnrestricted()) ok else no} без ограничений батареи",
            "${if (hasAudioPerm()) ok else no} доступ к записям звонков",
        ).joinToString("\n")

        io.execute {
            val store = EventStore(this)
            val pending = store.countPending()
            val sent = store.countSent()
            store.close()
            val fmt = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
            val lastUp = if (prefs.lastUploadAt > 0) fmt.format(Date(prefs.lastUploadAt)) else "никогда"
            val text = buildString {
                append("устройство:   ").append(prefs.deviceId).append('\n')
                append("в буфере:     ").append(pending).append('\n')
                append("отправлено:   ").append(sent).append(" (за 7 дней)\n")
                append("последняя отправка: ").append(lastUp).append('\n')
                if (prefs.lastUploadError.isNotBlank()) append("ошибка: ").append(prefs.lastUploadError).append('\n')
            }
            runOnUiThread { findViewById<TextView>(R.id.stats).text = text }
        }
    }
}
