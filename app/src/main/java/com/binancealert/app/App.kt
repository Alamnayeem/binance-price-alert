package com.binancealert.app

import android.app.Application
import com.binancealert.app.data.AppDb
import com.binancealert.app.engine.MonitorEngine
import com.binancealert.app.notify.Notifier
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class App : Application() {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS) // detects dead connections
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
    val db: AppDb by lazy { AppDb.get(this) }
    val engine: MonitorEngine by lazy { MonitorEngine(this, client) }

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
    }
}
