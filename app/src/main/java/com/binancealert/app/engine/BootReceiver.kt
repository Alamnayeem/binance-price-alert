package com.binancealert.app.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.binancealert.app.data.AppDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Restores monitoring after a device restart if it was ON before (subject to Android restrictions). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val appCtx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = AppDb.get(appCtx).dao().settingsOnce()
                if (s?.monitoring == true) {
                    runCatching { MonitorController.start(appCtx) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
