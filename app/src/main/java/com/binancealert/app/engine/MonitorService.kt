package com.binancealert.app.engine

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.binancealert.app.App
import com.binancealert.app.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as App
        val first = Notifier.monitorNotification(this, "Starting...")
        ServiceCompat.startForeground(
            this,
            Notifier.ID_MONITOR,
            first,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        app.engine.start()

        if (job == null) {
            val dao = app.db.dao()
            job = scope.launch {
                combine(
                    app.engine.state,
                    dao.watchCoins(),
                    dao.activeAlerts(),
                    dao.settings()
                ) { st, coins, active, settings ->
                    val count = (coins.map { it.symbol } + active.map { it.symbol }).toSet().size
                    setWakeLock(settings?.keepAwake ?: true)
                    "${st.label}  |  $count coins  |  ${active.size} active alerts"
                }.distinctUntilChanged().collect { text ->
                    Notifier.updateMonitor(this@MonitorService, text)
                }
            }
        }
        return START_STICKY
    }

    private fun setWakeLock(on: Boolean) {
        if (on) {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "binancealert:monitor").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } else {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        }
    }

    override fun onDestroy() {
        setWakeLock(false)
        scope.cancel()
        job = null
        (application as App).engine.stop()
        super.onDestroy()
    }
}

object MonitorController {
    fun start(ctx: Context) {
        ContextCompat.startForegroundService(ctx, Intent(ctx, MonitorService::class.java))
    }

    fun stop(ctx: Context) {
        ctx.stopService(Intent(ctx, MonitorService::class.java))
    }
}
