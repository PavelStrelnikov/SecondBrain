package com.secondbrain.capture

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = "Постоянное уведомление службы перехвата"
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val CHANNEL_ID = "capture"
    }
}
