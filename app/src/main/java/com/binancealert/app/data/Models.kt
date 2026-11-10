package com.binancealert.app.data

import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ConnState(val label: String) {
    STOPPED("Monitoring OFF"),
    CONNECTING("Connecting..."),
    CONNECTED("Connected"),
    RECONNECTING("Reconnecting..."),
    NO_INTERNET("No internet")
}

data class PriceTick(val price: Double, val time: Long)

data class CoinPair(val symbol: String, val base: String)

fun pairName(symbol: String): String =
    if (symbol.endsWith("USDT")) symbol.removeSuffix("USDT") + "/USDT" else symbol

fun formatPrice(p: Double): String {
    return try {
        var bd = BigDecimal(p.toString()).stripTrailingZeros()
        if (bd.scale() < 2) bd = bd.setScale(2)
        bd.toPlainString()
    } catch (e: Exception) {
        p.toString()
    }
}

fun formatClock(ms: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))

fun formatDateTime(ms: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(ms))
