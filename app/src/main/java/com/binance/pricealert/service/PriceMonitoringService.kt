package com.binance.pricealert.service

import android.app.*
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import com.binancealert.app.R
import com.binance.pricealert.*
import com.binance.pricealert.data.repository.*
import com.binance.pricealert.data.websocket.*
import com.binance.pricealert.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class PriceMonitoringService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var repository: AlertRepository
    private val wsManager = BinanceWebSocketManager()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as BinanceAlertApp
        repository = AlertRepository(app.database.alertDao())
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BinancePriceAlert:MonitoringWakeLock").apply {
            setReferenceCounted(false)
            acquire(24 * 60 * 60 * 1000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_MONITORING) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(1001, buildNotification("Monitoring Binance Spot USDT pairs..."))
        serviceScope.launch {
            repository.getAllMonitoredCoins().collectLatest { coins ->
                wsManager.updateMonitoredSymbols(coins.map { it.symbol }.toSet())
            }
        }
        serviceScope.launch {
            wsManager.priceUpdates.collect { update ->
                val triggered = repository.evaluatePriceUpdate(update.symbol, update.price)
                triggered.forEach { sendHitNotification(it) }
            }
        }
        return START_STICKY
    }

    private fun sendHitNotification(event: TriggeredAlertEvent) {
        val title = "TARGET HIT: ${event.alert.symbol} (${event.direction})"
        val msg = "Target: ${event.alert.targetPrice} | Hit: ${event.hitPrice} USDT"
        val notif = NotificationCompat.Builder(this, BinanceAlertApp.CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(msg)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify((System.currentTimeMillis() % 100000).toInt(), notif)
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, BinanceAlertApp.CHANNEL_MONITORING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Binance Price Alert")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    override fun onDestroy() {
        wsManager.disconnect()
        serviceScope.cancel()
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
    companion object {
        const val ACTION_STOP_MONITORING = "com.binance.pricealert.action.STOP"
    }
}
