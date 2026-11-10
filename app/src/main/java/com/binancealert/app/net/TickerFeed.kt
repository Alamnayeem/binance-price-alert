package com.binancealert.app.net

import com.binancealert.app.data.ConnState
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import org.json.JSONArray

/**
 * One public stream (!miniTicker@arr) that delivers prices of all pairs once per second.
 * Only run while the Coin List / Add Alert screen is visible, so it costs nothing in the background.
 */
class TickerFeed(client: OkHttpClient) {
    val prices = MutableStateFlow<Map<String, Double>>(emptyMap())
    val connected = MutableStateFlow(false)

    private val all = HashMap<String, Double>()

    private val socket = ReconnectingSocket(
        client,
        "wss://stream.binance.com:443/ws/!miniTicker@arr",
        { st -> connected.value = (st == ConnState.CONNECTED) },
        { },
        { text -> parse(text) }
    )

    fun start() = socket.start()
    fun stop() = socket.stop()

    private fun parse(text: String) {
        try {
            val arr = JSONArray(text)
            synchronized(all) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val s = o.getString("s")
                    if (s.endsWith("USDT")) all[s] = o.getString("c").toDouble()
                }
                prices.value = HashMap(all)
            }
        } catch (e: Exception) {
            // ignore non-array messages
        }
    }
}
