package com.secondbrain.capture.capture

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import com.secondbrain.capture.Prefs

/** Поднимает службу после перезагрузки и после обновления приложения, и просит систему заново привязать слушатель уведомлений. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (Prefs(context).configured) CaptureService.start(context)
        rebindListener(context)
    }

    companion object {
        /** После обновления пакета Android часто оставляет слушатель отвязанным до ручного переключения. */
        fun rebindListener(context: Context) {
            runCatching {
                NotificationListenerService.requestRebind(
                    ComponentName(context, NotificationCaptureService::class.java),
                )
            }
        }
    }
}
