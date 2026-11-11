package com.binancealert.app.net

import com.binancealert.app.data.CoinPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Candle(val openTime: Long, val open: Double, val high: Double, val low: Double, val close: Double)

/** Public, key-less Binance REST calls (used only for the coin list and one-off price look-ups). */
class BinanceRest(baseClient: OkHttpClient, private val cacheFile: File) {

    private val client = baseClient.newBuilder()
        .readTimeout(40, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun get(url: String): String {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP " + response.code)
            return response.body?.string() ?: throw IOException("Empty response")
        }
    }

    suspend fun loadPairs(): List<CoinPair> = withContext(Dispatchers.IO) {
        try {
            val body = get("https://api.binance.com/api/v3/exchangeInfo?permissions=SPOT")
            val arr = JSONObject(body).getJSONArray("symbols")
            val out = ArrayList<CoinPair>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                if (item.optString("quoteAsset") == "USDT" &&
                    item.optString("status") == "TRADING" &&
                    item.optBoolean("isSpotTradingAllowed", true)
                ) {
                    out.add(CoinPair(item.getString("symbol"), item.getString("baseAsset")))
                }
            }
            out.sortBy { it.base }
            runCatching { cacheFile.writeText(out.joinToString("\n") { it.symbol + "," + it.base }) }
            out
        } catch (e: Exception) {
            readCache() ?: throw e
        }
    }

    private fun readCache(): List<CoinPair>? {
        return try {
            if (!cacheFile.exists()) {
                null
            } else {
                cacheFile.readLines().mapNotNull { line ->
                    val parts = line.split(",")
                    if (parts.size == 2) CoinPair(parts[0], parts[1]) else null
                }.takeIf { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchPrice(symbol: String): Double? = withContext(Dispatchers.IO) {
        try {
            JSONObject(get("https://api.binance.com/api/v3/ticker/price?symbol=$symbol"))
                .getString("price").toDouble()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchCandles(symbol: String, interval: String): List<Candle> = withContext(Dispatchers.IO) {
        require(interval in setOf("1m", "5m", "15m", "1h", "4h", "1d")) { "Unsupported interval" }
        val rows = JSONArray(
            get("https://api.binance.com/api/v3/klines?symbol=$symbol&interval=$interval&limit=100")
        )
        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONArray(i)
                add(
                    Candle(
                        openTime = row.getLong(0),
                        open = row.getString(1).toDouble(),
                        high = row.getString(2).toDouble(),
                        low = row.getString(3).toDouble(),
                        close = row.getString(4).toDouble()
                    )
                )
            }
        }
    }
}
