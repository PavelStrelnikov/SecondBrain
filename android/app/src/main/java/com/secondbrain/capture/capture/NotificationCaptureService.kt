package com.secondbrain.capture.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.secondbrain.capture.Prefs
import com.secondbrain.capture.data.EventStore
import com.secondbrain.capture.data.Events
import com.secondbrain.capture.work.UploadWorker
import org.json.JSONObject

/**
 * Слушает уведомления WhatsApp и WhatsApp Business.
 * WhatsApp публикует уведомление в стиле переписки и переиздаёт его с накопленным списком сообщений,
 * поэтому каждое сообщение получает ключ по содержимому и времени: повтор не создаёт дубля.
 * Сводные уведомления («5 новых сообщений из 3 чатов») пропускаются.
 */
class NotificationCaptureService : NotificationListenerService() {

    override fun onListenerConnected() {
        Log.i(TAG, "listener connected")
        heartbeat("listener_connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val source = when (sbn.packageName) {
            "com.whatsapp" -> "whatsapp"
            "com.whatsapp.w4b" -> "whatsapp_business"
            else -> return
        }
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val prefs = Prefs(this)
        val store = EventStore(this)
        var added = 0
        try {
            val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

            // Звонки WhatsApp: входящий вызов идёт как CATEGORY_CALL, пропущенный как CATEGORY_MISSED_CALL.
            // Содержания у них нет, только факт и кто звонил. Ключ по минуте, потому что уведомление обновляется.
            if (n.category == Notification.CATEGORY_CALL || n.category == Notification.CATEGORY_MISSED_CALL) {
                val direction = if (n.category == Notification.CATEGORY_MISSED_CALL) "missed" else "incoming"
                val key = "wacall:" + Events.sha1(sbn.packageName, direction, title, sbn.postTime / 60_000L)
                val payload = JSONObject().apply {
                    put("caller", title)
                    put("text", text)
                    put("via", "whatsapp")
                }
                if (store.put(Events.build(key, "call", source, direction, sbn.postTime, prefs.deviceId, payload))) added++
                return
            }

            if (isServiceNotification(n, title)) return

            val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
            if (style != null) {
                val conversation = style.conversationTitle?.toString() ?: title
                val isGroup = style.isGroupConversation
                for (m in style.messages) {
                    val body = m.text?.toString() ?: continue
                    val sender = m.person?.name?.toString() ?: conversation
                    // Сообщения от «You» появляются, когда ты ответил прямо из уведомления.
                    val direction = if (m.person == null || m.person?.name == style.user.name) "outgoing" else "incoming"
                    val key = "wa:" + Events.sha1(sbn.packageName, conversation, sender, body, m.timestamp)
                    val payload = JSONObject().apply {
                        put("chat", conversation)
                        put("sender", sender)
                        put("text", body)
                        put("is_group", isGroup)
                        put("has_media", m.dataMimeType != null)
                    }
                    if (store.put(Events.build(key, "message", source, direction, m.timestamp, prefs.deviceId, payload))) added++
                }
            } else {
                // Запасной путь: старый формат без стиля переписки.
                if (text.isBlank()) return
                val key = "wa:" + Events.sha1(sbn.packageName, title, text, sbn.postTime / 1000)
                val payload = JSONObject().apply {
                    put("chat", title)
                    put("sender", title)
                    put("text", text)
                    put("fallback", true)
                }
                if (store.put(Events.build(key, "message", source, "incoming", sbn.postTime, prefs.deviceId, payload))) added++
            }
        } catch (e: Exception) {
            Log.w(TAG, "failed to parse notification", e)
        } finally {
            store.close()
        }
        if (added > 0) UploadWorker.uploadNow(this)
    }

    /**
     * Служебные уведомления WhatsApp: резервная копия, «проверка новых сообщений», идущий звонок,
     * загрузка медиа. У них заголовок = имя приложения, есть прогресс или флаг foreground-службы.
     */
    private fun isServiceNotification(n: Notification, title: String): Boolean {
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return true
        if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return true
        if (n.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0) return true
        if (n.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)) return true
        if (n.category == Notification.CATEGORY_PROGRESS || n.category == Notification.CATEGORY_SERVICE ||
            n.category == Notification.CATEGORY_STATUS || n.category == Notification.CATEGORY_SYSTEM
        ) return true
        val t = title.trim().lowercase()
        return t == "whatsapp" || t == "whatsapp business"
    }

    private fun heartbeat(state: String) {
        val prefs = Prefs(this)
        val store = EventStore(this)
        try {
            val now = System.currentTimeMillis()
            val key = "state:$state:" + (now / 60_000L)
            store.put(Events.build(key, "app_state", "android", null, now, prefs.deviceId, JSONObject().put("state", state)))
        } finally {
            store.close()
        }
        UploadWorker.uploadNow(this)
    }

    companion object {
        private const val TAG = "brain.notif"
    }
}
