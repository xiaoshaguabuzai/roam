/**
 * 通知监听服务与授权检测。
 */
package roam.tools

import android.app.Notification
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * 通知读取服务。只保留最近 50 条在内存里，不落盘；
 * 是否可用由用户在系统设置里授予「通知使用权」决定。
 */
class NotificationListener : NotificationListenerService() {

    data class Item(val app: String, val title: String, val text: String, val at: Long)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return
        val app = runCatching {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)

        synchronized(recent) {
            recent.add(0, Item(app, title.take(80), text.take(160), sbn.postTime))
            while (recent.size > 50) recent.removeAt(recent.lastIndex)
        }
    }

    companion object {
        val recent = mutableListOf<Item>()

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ).orEmpty()
            return enabled.contains(context.packageName)
        }
    }
}
