package com.binancealert.app.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.binancealert.app.MainActivity
import com.binancealert.app.R
import com.binancealert.app.data.PriceAlert
import com.binancealert.app.data.formatPrice
import com.binancealert.app.data.pairName

object Notifier {
    const val CH_MONITOR = "monitor"
    const val ID_MONITOR = 1
    private const val ID_ALERT_BASE = 10_000
    private const val ID_TEST = 9_999

    /**
     * Android does not let an app change sound/vibration of an existing channel,
     * so there is one channel per sound/vibration combination and we pick the right one.
     */
    private fun alertChannelId(sound: Boolean, vibration: Boolean): String =
        "alert_" + (if (sound) "s" else "x") + (if (vibration) "v" else "x")

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)

        val monitor = NotificationChannel(
            CH_MONITOR, "Monitoring status", NotificationManager.IMPORTANCE_LOW
        )
        monitor.description = "Persistent notification shown while prices are being monitored"
        monitor.setShowBadge(false)
        nm.createNotificationChannel(monitor)

        for (sound in listOf(true, false)) {
            for (vib in listOf(true, false)) {
                val label = when {
                    sound && vib -> "sound + vibration"
                    sound -> "sound only"
                    vib -> "vibration only"
                    else -> "silent"
                }
                val ch = NotificationChannel(
                    alertChannelId(sound, vib),
                    "Price alerts ($label)",
                    NotificationManager.IMPORTANCE_HIGH
                )
                ch.description = "Notifications when a target price is hit"
                if (sound) {
                    ch.setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                } else {
                    ch.setSound(null, null)
                }
                ch.enableVibration(vib)
                if (vib) ch.vibrationPattern = longArrayOf(0, 400, 200, 400)
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun openAppIntent(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun monitorNotification(ctx: Context, text: String): Notification =
        NotificationCompat.Builder(ctx, CH_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle("BINANCE PRICE ALERT")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent(ctx))
            .build()

    fun updateMonitor(ctx: Context, text: String) {
        if (!canNotify(ctx)) return
        try {
            NotificationManagerCompat.from(ctx).notify(ID_MONITOR, monitorNotification(ctx, text))
        } catch (e: SecurityException) {
            // permission revoked
        }
    }

    fun alertHit(ctx: Context, alert: PriceAlert, hitPrice: Double, direction: String, sound: Boolean, vibration: Boolean) {
        ensureChannels(ctx)
        if (!canNotify(ctx)) return
        val pair = pairName(alert.symbol)
        val arrow = when (direction) {
            "UPWARD" -> "\u25B2"
            "DOWNWARD" -> "\u25BC"
            else -> "\u25CF"
        }
        val target = formatPrice(alert.targetPrice)
        val hit = formatPrice(hitPrice)
        val n = NotificationCompat.Builder(ctx, alertChannelId(sound, vibration))
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle("$pair $arrow $direction")
            .setContentText("Target $target  |  Hit price $hit")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Pair: $pair\nTarget price: $target USDT\nHit price: $hit USDT\nDirection: $direction"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(ctx))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ID_ALERT_BASE + alert.id.toInt(), n)
        } catch (e: SecurityException) {
            // permission revoked
        }
    }

    fun test(ctx: Context, sound: Boolean, vibration: Boolean) {
        ensureChannels(ctx)
        if (!canNotify(ctx)) return
        val n = NotificationCompat.Builder(ctx, alertChannelId(sound, vibration))
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle("Test notification")
            .setContentText("If you can see this, price alerts will be shown like this.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(ctx))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ID_TEST, n)
        } catch (e: SecurityException) {
            // permission revoked
        }
    }
}
