package com.secondbrain.capture.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.secondbrain.capture.Prefs

/** Поднимает службу после перезагрузки и после обновления приложения. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (Prefs(context).configured) CaptureService.start(context)
    }
}
