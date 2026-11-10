package com.binance.pricealert.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitored_coins")
data class MonitoredCoin(
    @PrimaryKey val symbol: String,
    val currentPrice: Double = 0.0,
    val previousPrice: Double? = null,
    val lastUpdated: Long = 0L
)

@Entity(tableName = "price_alerts")
data class PriceAlert(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val symbol: String,
    val targetPrice: Double,
    val isActive: Boolean = true,
    val triggeredAt: Long? = null,
    val hitPrice: Double? = null,
    val direction: String? = null
)

@Entity(tableName = "alert_history")
data class AlertHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val alertId: Long,
    val symbol: String,
    val targetPrice: Double,
    val hitPrice: Double,
    val direction: String,
    val timestamp: Long = System.currentTimeMillis()
)
