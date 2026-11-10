package com.binancealert.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.binancealert.app.App
import com.binancealert.app.data.AppSettings
import com.binancealert.app.data.CoinPair
import com.binancealert.app.data.PriceAlert
import com.binancealert.app.data.WatchCoin
import com.binancealert.app.engine.MonitorController
import com.binancealert.app.net.BinanceRest
import com.binancealert.app.net.TickerFeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val a = application as App
    private val dao = a.db.dao()
    private val rest = BinanceRest(a.client, File(application.cacheDir, "pairs.txt"))

    val engine = a.engine
    val ticker = TickerFeed(a.client)

    val settings: StateFlow<AppSettings> = dao.settings()
        .map { it ?: AppSettings() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val watch: StateFlow<List<WatchCoin>> = dao.watchCoins()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val alerts: StateFlow<List<PriceAlert>> = dao.alerts()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pairs = MutableStateFlow<List<CoinPair>>(emptyList())
    val loading = MutableStateFlow(false)
    val pairsError = MutableStateFlow<String?>(null)

    /** Coin chosen on the Coin List screen, used to pre-fill the Add Alert screen. */
    var alertSymbol: String = ""

    init {
        loadPairs()
    }

    fun loadPairs() {
        if (loading.value) return
        viewModelScope.launch {
            loading.value = true
            pairsError.value = null
            try {
                pairs.value = rest.loadPairs()
            } catch (e: Exception) {
                pairsError.value = "Could not load coin list (" + (e.message ?: "unknown error") + ")"
            }
            loading.value = false
        }
    }

    suspend fun fetchPrice(symbol: String): Double? = rest.fetchPrice(symbol)

    fun toggleWatch(symbol: String, on: Boolean) {
        viewModelScope.launch {
            if (on) dao.addCoin(WatchCoin(symbol, System.currentTimeMillis())) else dao.removeCoin(symbol)
        }
    }

    fun addAlert(symbol: String, target: Double, active: Boolean) {
        viewModelScope.launch {
            dao.addCoin(WatchCoin(symbol, System.currentTimeMillis()))
            dao.addAlert(
                PriceAlert(
                    symbol = symbol,
                    targetPrice = target,
                    status = if (active) "ACTIVE" else "INACTIVE",
                    createdAt = System.currentTimeMillis()
                )
            )
            if (active && !settings.value.monitoring) setMonitoring(true)
        }
    }

    fun setAlertActive(id: Long, active: Boolean) {
        viewModelScope.launch { dao.setStatus(id, if (active) "ACTIVE" else "INACTIVE") }
    }

    fun deleteAlert(id: Long) {
        viewModelScope.launch { dao.deleteAlert(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { dao.clearHistory() }
    }

    fun setMonitoring(on: Boolean) {
        viewModelScope.launch {
            dao.saveSettings(settings.value.copy(monitoring = on))
            val ctx = getApplication<Application>()
            if (on) runCatching { MonitorController.start(ctx) } else MonitorController.stop(ctx)
        }
    }

    fun updateSettings(change: (AppSettings) -> AppSettings) {
        viewModelScope.launch { dao.saveSettings(change(settings.value)) }
    }

    override fun onCleared() {
        ticker.stop()
        super.onCleared()
    }
}
