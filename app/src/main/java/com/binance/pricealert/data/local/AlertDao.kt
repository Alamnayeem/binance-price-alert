package com.binance.pricealert.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM monitored_coins ORDER BY symbol")
    fun getAllMonitoredCoins(): Flow<List<MonitoredCoin>>

    @Query("SELECT * FROM price_alerts ORDER BY id DESC")
    fun getAllAlerts(): Flow<List<PriceAlert>>

    @Query("SELECT * FROM alert_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<AlertHistory>>

    @Query("SELECT * FROM price_alerts WHERE symbol = :symbol AND isActive = 1 AND isTriggered = 0")
    suspend fun getActiveAlertsForSymbol(symbol: String): List<PriceAlert>

    @Query("UPDATE monitored_coins SET currentPrice = :price, previousPrice = :previousPrice, lastUpdated = :timestamp WHERE symbol = :symbol")
    suspend fun updateCoinPrice(symbol: String, price: Double, previousPrice: Double?, timestamp: Long)

    @Query("UPDATE price_alerts SET isTriggered = 1, isActive = 0 WHERE id = :alertId")
    suspend fun markAlertTriggered(alertId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMonitoredCoin(coin: MonitoredCoin)

    @Insert
    suspend fun insertAlert(alert: PriceAlert): Long

    @Insert
    suspend fun insertHistory(history: AlertHistory)
}
