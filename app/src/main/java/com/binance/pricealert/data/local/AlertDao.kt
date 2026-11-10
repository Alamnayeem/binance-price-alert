package com.binance.pricealert.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM monitored_coins ORDER BY symbol")
    fun getAllMonitoredCoins(): Flow<List<MonitoredCoin>>

    @Query("SELECT * FROM price_alerts ORDER BY id DESC")
    fun getAllAlerts(): Flow<List<PriceAlert>>

    @Query("SELECT * FROM alert_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<AlertHistory>>

    @Query("SELECT * FROM price_alerts WHERE symbol = :symbol AND isActive = 1")
    suspend fun getActiveAlertsForSymbol(symbol: String): List<PriceAlert>

    @Query("SELECT * FROM monitored_coins WHERE symbol = :symbol LIMIT 1")
    suspend fun getMonitoredCoin(symbol: String): MonitoredCoin?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMonitoredCoin(coin: MonitoredCoin)

    @Transaction
    suspend fun updateCoinPrice(symbol: String, currentPrice: Double, previousPrice: Double?, timestamp: Long) {
        upsertMonitoredCoin(MonitoredCoin(symbol, currentPrice, previousPrice, timestamp))
    }

    @Query("UPDATE price_alerts SET isActive = 0, hitPrice = :hitPrice, direction = :direction, triggeredAt = :timestamp WHERE id = :alertId")
    suspend fun markAlertTriggered(alertId: Long, hitPrice: Double, direction: String, timestamp: Long)

    @Insert
    suspend fun insertHistory(history: AlertHistory)
}
