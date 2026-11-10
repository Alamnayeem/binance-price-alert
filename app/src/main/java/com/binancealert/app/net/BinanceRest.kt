package com.binancealert.app.net

import com.binancealert.app.data.CoinPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Public, key-less Binance REST calls (used only for the coin list and one-off price look-ups). */
class BinanceRest(baseClient: OkHttpClient, private val cacheFile: File) {

    private val client = baseClient.newBuilder()
        .readTimeout(40, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun get(url: String): String {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP " + r.code)
            return r.body?.string() ?: throw IOException("Empty response")
        }
    }

    suspend fun loadPairs(): List<CoinPair> = withContext(Dispatchers.IO) {
        try {
            val body = get("https://api.binance.com/api/v3/exchangeInfo?permissions=SPOT")
            val arr = JSONObject(body).getJSONArray("symbols")
            val out = ArrayList<CoinPair>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("quoteAsset") == "USDT" &&
                    o.optString("status") == "TRADING" &&
                    o.optBoolean("isSpotTradingAllowed", true)
                ) {
                    out.add(CoinPair(o.getString("symbol"), o.getString("baseAsset")))
                }
            }
            out.sortBy { it.base }
            runCatching {
                cacheFile.writeText(out.joinToString("\n") { it.symbol + "," + it.base })
            }
            out as List<CoinPair>
        } catch (e: Exception) {
            readCache() ?: throw e
        }
    }

    private fun readCache(): List<CoinPair>? {
        return try {
            if (!cacheFile.exists()) return null
            val list = cacheFile.readLines().mapNotNull { line ->
                val p = line.split(",")
                if (p.size == 2) CoinPair(p[0], p[1]) else null
            }
            if (list.isEmpty()) null else list
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchPrice(symbol: String): Double? = withContext(Dispatchers.IO) {
        try {
            val body = get("https://api.binance.com/api/v3/ticker/price?symbol=$symbol")
            JSONObject(body).getString("price").toDouble()
        } catch (e: Exception) {
            null
        }
    }
}
