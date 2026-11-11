package com.binance.pricealert.data.repository

import com.binance.pricealert.data.local.*
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap

data class TriggeredAlertEvent(
    val alert: PriceAlert,
    val hitPrice: Double,
    val previousPrice: Double?,
    val direction: String,
    val timestamp: Long = System.currentTimeMillis()
)

class AlertRepository(private val alertDao: AlertDao) {
    private val latestPrices = ConcurrentHashMap<String, Double>()

    fun getAllMonitoredCoins(): Flow<List<MonitoredCoin>> = alertDao.getAllMonitoredCoins()
    fun getAllAlerts(): Flow<List<PriceAlert>> = alertDao.getAllAlerts()
    fun getAllHistory(): Flow<List<AlertHistory>> = alertDao.getAllHistory()

    suspend fun evaluatePriceUpdate(symbol: String, currentPrice: Double): List<TriggeredAlertEvent> {
        val uppercase = symbol.uppercase()
        val prev = latestPrices[uppercase]
        latestPrices[uppercase] = currentPrice

        alertDao.updateCoinPrice(uppercase, currentPrice, prev, System.currentTimeMillis())

        val activeAlerts = alertDao.getActiveAlertsForSymbol(uppercase)
        if (activeAlerts.isEmpty()) return emptyList()

        val triggeredEvents = mutableListOf<TriggeredAlertEvent>()
        for (alert in activeAlerts) {
            val target = alert.targetPrice
            var dir: String? = null

            if (prev != null) {
                if (prev > target && currentPrice <= target) dir = "DOWNWARD"
                else if (prev < target && currentPrice >= target) dir = "UPWARD"
                else if (prev == target || currentPrice == target) dir = if (currentPrice >= prev) "UPWARD" else "DOWNWARD"
            } else if (currentPrice == target) {
                dir = "EXACT"
            }

            if (dir != null) {
                val now = System.currentTimeMillis()
                alertDao.markAlertTriggered(alert.id)
                alertDao.insertHistory(AlertHistory(alertId = alert.id, symbol = alert.symbol, targetPrice = target, hitPrice = currentPrice, direction = dir, timestamp = now))
                triggeredEvents.add(TriggeredAlertEvent(alert, currentPrice, prev, dir, now))
            }
        }
        return triggeredEvents
    }
}
