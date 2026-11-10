package com.binancealert.app.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.binancealert.app.data.AppDb
import com.binancealert.app.data.AppSettings
import com.binancealert.app.data.ConnState
import com.binancealert.app.data.PriceAlert
import com.binancealert.app.data.PriceTick
import com.binancealert.app.net.ReconnectingSocket
import com.binancealert.app.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps ONE public Binance WebSocket open (wss://stream.binance.com:443/ws) and subscribes to
 * "<symbol>@trade" streams for every monitored coin. Every trade is checked against the active alerts.
 */
class MonitorEngine(private val app: Context, private val client: OkHttpClient) {

    private val dao = AppDb.get(app).dao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    val prices = MutableStateFlow<Map<String, PriceTick>>(emptyMap())
    val state = MutableStateFlow(ConnState.STOPPED)
    val lastUpdate = MutableStateFlow(0L)

    private val lastPrice = ConcurrentHashMap<String, Double>()

    @Volatile
    private var alerts: List<PriceAlert> = emptyList()

    @Volatile
    private var settings = AppSettings()

    @Volatile
    private var connected = false
    private var desired: Set<String> = emptySet()
    private var subscribed: Set<String> = emptySet()
    private var reqId = 1

    private var socket: ReconnectingSocket? = null
    private var jobs: List<Job> = emptyList()
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    /** Call on the main thread. Safe to call repeatedly. */
    fun start() {
        if (socket != null) return
        val s = ReconnectingSocket(
            client,
            "wss://stream.binance.com:443/ws",
            { st ->
                if (st != ConnState.CONNECTED) connected = false
                state.value = st
            },
            { scope.launch { onOpened() } },
            { text -> onText(text) }
        )
        socket = s
        s.start()
        registerNetwork(s)

        val symbolsJob = scope.launch {
            combine(dao.watchCoins(), dao.activeAlerts()) { coins, active ->
                alerts = active
                (coins.map { it.symbol } + active.map { it.symbol }).toSet()
            }.distinctUntilChanged().collect {
                desired = it
                sync()
            }
        }
        val settingsJob = scope.launch {
            dao.settings().collect { settings = it ?: AppSettings() }
        }
        jobs = listOf(symbolsJob, settingsJob)
    }

    /** Call on the main thread. */
    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        val cm = app.getSystemService(ConnectivityManager::class.java)
        netCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        netCallback = null
        socket?.stop()
        socket = null
        connected = false
        subscribed = emptySet()
        lastPrice.clear()
    }

    private fun registerNetwork(s: ReconnectingSocket) {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (state.value != ConnState.CONNECTED) s.resume()
            }

            override fun onLost(network: Network) {
                s.pause()
            }
        }
        netCallback = cb
        try {
            cm.registerDefaultNetworkCallback(cb)
        } catch (e: Exception) {
            netCallback = null
        }
    }

    private suspend fun onOpened() {
        mutex.withLock {
            connected = true
            subscribed = emptySet()
        }
        sync()
    }

    /** Subscribes / unsubscribes so the socket matches the wanted coin list. */
    private suspend fun sync() {
        mutex.withLock {
            val s = socket ?: return
            if (!connected) return
            val add = desired - subscribed
            val remove = subscribed - desired
            if (add.isNotEmpty()) {
                for (chunk in add.chunked(100)) {
                    if (!send(s, "SUBSCRIBE", chunk)) return
                    delay(250) // Binance allows max 5 control messages per second
                }
            }
            if (remove.isNotEmpty()) {
                for (chunk in remove.chunked(100)) {
                    if (!send(s, "UNSUBSCRIBE", chunk)) return
                    delay(250)
                }
            }
            subscribed = desired
        }
    }

    private fun send(s: ReconnectingSocket, method: String, symbols: List<String>): Boolean {
        val params = JSONArray()
        symbols.forEach { params.put(it.lowercase() + "@trade") }
        val msg = JSONObject()
        msg.put("method", method)
        msg.put("params", params)
        msg.put("id", reqId++)
        return s.send(msg.toString())
    }

    private fun onText(text: String) {
        try {
            var o = JSONObject(text)
            if (o.has("data")) o = o.getJSONObject("data")
            if (o.optString("e") != "trade") return
            onTick(o.getString("s"), o.getString("p").toDouble())
        } catch (e: Exception) {
            // ignore messages we do not understand (e.g. subscription replies)
        }
    }

    private fun onTick(symbol: String, price: Double) {
        val now = System.currentTimeMillis()
        val prev = lastPrice.put(symbol, price)
        prices.value = prices.value + (symbol to PriceTick(price, now))
        lastUpdate.value = now

        for (a in alerts) {
            if (a.symbol != symbol) continue
            val dir = direction(prev, price, a.targetPrice) ?: continue
            scope.launch {
                // markHit only succeeds once per alert -> no duplicate notifications
                if (dao.markHit(a.id, price, now, dir) == 1) {
                    val st = settings
                    Notifier.alertHit(app, a, price, dir, st.sound, st.vibration)
                }
            }
        }
    }

    /**
     * Crossing detection from the previous trade price to the current one.
     * Returns the direction, or null when the target was not reached/crossed.
     */
    private fun direction(prev: Double?, price: Double, target: Double): String? {
        if (price == target) {
            if (prev == null || prev == target) return "AT TARGET"
            return if (prev > target) "DOWNWARD" else "UPWARD"
        }
        if (prev == null) return null
        if (prev > target && price < target) return "DOWNWARD"
        if (prev < target && price > target) return "UPWARD"
        return null
    }
}
