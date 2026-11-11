package com.binancealert.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.binancealert.app.App
import com.binancealert.app.MainActivity
import com.binancealert.app.R
import com.binancealert.app.data.WatchCoin
import com.binancealert.app.net.BinanceRest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class PriceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    companion object {
        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_price)
            views.setTextViewText(R.id.widget_symbol, "BINANCE PRICE")
            views.setTextViewText(R.id.widget_price, "Loading…")
            val open = Intent(context, MainActivity::class.java)
            val pending = android.app.PendingIntent.getActivity(context, id, open,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            manager.updateAppWidget(id, views)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val app = context.applicationContext as App
                    val favorite = app.db.dao().watchCoins().first().firstOrNull { it.favorite }
                    val symbol = favorite?.symbol ?: "BTCUSDT"
                    val price = BinanceRest(app.client, File(context.cacheDir, "widget-pairs.txt")).fetchPrice(symbol)
                    views.setTextViewText(R.id.widget_symbol, favorite?.symbol?.replace("USDT", "/USDT") ?: "BTC/USDT")
                    views.setTextViewText(R.id.widget_price, price?.let { "${"%.4f".format(it)} USDT" } ?: "No connection")
                    manager.updateAppWidget(id, views)
                } catch (_: Exception) {
                    views.setTextViewText(R.id.widget_price, "Open app to refresh")
                    manager.updateAppWidget(id, views)
                }
            }
        }
    }
}
