package com.binance.pricealert.data.websocket

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class TradePriceUpdate(
    val symbol: String,
    val price: Double,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class WsConnectionStatus {
    object Disconnected : WsConnectionStatus()
    object Connecting : WsConnectionStatus()
    data class Connected(val activeStreamsCount: Int) : WsConnectionStatus()
    data class Error(val message: String) : WsConnectionStatus()
}

class BinanceWebSocketManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private var currentSymbols: Set<String> = emptySet()
    private val isManuallyStopped = AtomicBoolean(false)
    private var reconnectAttempts = 0

    private val _connectionStatus = MutableStateFlow<WsConnectionStatus>(WsConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<WsConnectionStatus> = _connectionStatus.asStateFlow()

    private val _priceUpdates = MutableSharedFlow<TradePriceUpdate>(extraBufferCapacity = 64)
    val priceUpdates: SharedFlow<TradePriceUpdate> = _priceUpdates.asSharedFlow()

    fun updateMonitoredSymbols(symbols: Set<String>) {
        currentSymbols = symbols.map { it.uppercase() }.toSet()
        if (currentSymbols.isEmpty()) disconnect() else connect()
    }

    @Synchronized
    fun connect() {
        if (currentSymbols.isEmpty()) return
        isManuallyStopped.set(false)
        try { webSocket?.close(1000, "Reconnecting") } catch (_: Exception) {}

        _connectionStatus.value = WsConnectionStatus.Connecting
        val streamParam = currentSymbols.joinToString("/") { "${it.lowercase()}@trade" }
        val url = "wss://stream.binance.com:9443/stream?streams=$streamParam"

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                reconnectAttempts = 0
                _connectionStatus.value = WsConnectionStatus.Connected(currentSymbols.size)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val json = JsonParser.parseString(text).asJsonObject
                    val data = if (json.has("data")) json.getAsJsonObject("data") else json
                    val symbol = data.get("s")?.asString?.uppercase()
                    val price = data.get("p")?.asString?.toDoubleOrNull()
                    if (symbol != null && price != null && price > 0.0) {
                        _priceUpdates.tryEmit(TradePriceUpdate(symbol, price))
                    }
                } catch (_: Exception) {}
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _connectionStatus.value = WsConnectionStatus.Error(t.message ?: "Connection failure")
                if (!isManuallyStopped.get()) scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        CoroutineScope(Dispatchers.IO).launch {
            reconnectAttempts++
            delay((2000L * reconnectAttempts).coerceAtMost(30000L))
            connect()
        }
    }

    fun disconnect() {
        isManuallyStopped.set(true)
        try { webSocket?.close(1000, "User disconnect") } catch (_: Exception) {}
        _connectionStatus.value = WsConnectionStatus.Disconnected
    }
}