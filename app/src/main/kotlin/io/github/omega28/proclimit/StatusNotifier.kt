package io.github.omega28.proclimit

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Posts the boot result: a silent notification on success, an alerting one on failure. */
object StatusNotifier {
    private const val CHANNEL_APPLIED = "applied"
    private const val CHANNEL_PROBLEMS = "problems"
    private const val NOTIFICATION_ID = 1

    fun canNotify(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun postBootResult(context: Context, result: ProcLimit.Result, time: Long) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_APPLIED, "Applied at boot", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Silent confirmation that the limit was set after boot"
                },
                NotificationChannel(CHANNEL_PROBLEMS, "Problems", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "The limit couldn't be set after boot"
                },
            )
        )

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val at = ProcLimit.formatTime(context, time)
        val builder = if (result.ok) {
            Notification.Builder(context, CHANNEL_APPLIED)
                .setContentTitle("At most ${ProcLimit.TARGET_LIMIT} background processes")
                .setContentText("Limit applied at boot · $at")
        } else {
            Notification.Builder(context, CHANNEL_PROBLEMS)
                .setContentTitle("Couldn't set the background process limit")
                .setContentText(result.message)
                .setStyle(Notification.BigTextStyle().bigText("${result.message}\n$at"))
        }
        manager.notify(
            NOTIFICATION_ID,
            builder.setSmallIcon(R.drawable.ic_stat_proclimit)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}
