package com.binancealert.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.binancealert.app.data.PriceAlert
import com.binancealert.app.data.formatDateTime
import com.binancealert.app.data.formatPrice
import com.binancealert.app.data.pairName
import com.binancealert.app.net.Candle

@Composable
fun EnhancedDashboardScreen(vm: AppViewModel) {
    val watch by vm.watch.collectAsState()
    val alerts by vm.alerts.collectAsState()
    val prices by vm.engine.prices.collectAsState()
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf("Name") }
    val activeSymbols = alerts.filter { it.status == "ACTIVE" }.map { it.symbol }.toSet()
    val favorites = watch.filter { it.favorite }.map { it.symbol }.toSet()
    val symbols = (watch.map { it.symbol } + activeSymbols).distinct()
        .filter { !favoritesOnly || it in favorites }
        .sortedWith(when (sort) {
            "Price ↑" -> compareBy { prices[it]?.price ?: Double.MAX_VALUE }
            "Price ↓" -> compareByDescending { prices[it]?.price ?: Double.MIN_VALUE }
            "Alert status" -> compareByDescending<String> { it in activeSymbols }.thenBy { it }
            else -> compareBy { it }
        }.let { comparator -> compareByDescending<String> { it in favorites }.then(comparator) })

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dashboard coins", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { favoritesOnly = !favoritesOnly }) { Text(if (favoritesOnly) "All" else "★ Favorites") }
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Name", "Price ↑", "Price ↓", "Alert status").forEach { option ->
                TextButton(onClick = { sort = option }) { Text(if (sort == option) "✓ $option" else option, fontSize = 11.sp) }
            }
        } }
        if (symbols.isEmpty()) item { Text("কোনো coin নেই। Coins ট্যাব থেকে Add করুন।") }
        items(symbols, key = { it }) { symbol ->
            val tick = prices[symbol]
            val isFavorite = symbol in favorites
            Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pairName(symbol), fontWeight = FontWeight.Bold)
                        Text("${tick?.let { formatPrice(it.price) } ?: "--"} USDT", color = Yellow)
                        Text(if (symbol in activeSymbols) "Alert active" else "No active alert", color = Muted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { vm.toggleFavorite(symbol, !isFavorite) }) {
                        Text(if (isFavorite) "★" else "☆", color = Yellow, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun EnhancedAddAlertScreen(vm: AppViewModel, onSaved: () -> Unit) {
    val pairs by vm.pairs.collectAsState()
    val initial = rememberSaveable { vm.alertSymbol }
    var query by rememberSaveable { mutableStateOf(initial.removeSuffix("USDT")) }
    var selected by rememberSaveable { mutableStateOf(initial) }
    var target by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(true) }
    var error by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.alertSymbol = "" }
    val suggestions = if (selected.isNotEmpty() || query.isBlank()) emptyList() else pairs
        .filter { it.base.contains(query.trim(), ignoreCase = true) }.take(8)
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Create price alert", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(query, { query = it.uppercase(); selected = "" }, Modifier.fillMaxWidth(), label = { Text("Coin (e.g. BTC)") }, singleLine = true) }
        items(suggestions, key = { it.symbol }) { pair -> Text(pairName(pair.symbol), Modifier.fillMaxWidth().clickable { selected = pair.symbol; query = pair.base }.padding(10.dp)) }
        if (selected.isNotEmpty()) item { Text("Selected: ${pairName(selected)}", color = Yellow) }
        item { OutlinedTextField(target, { target = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.fillMaxWidth(), label = { Text("Target price (USDT)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) }
        item { OutlinedTextField(note, { note = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Label / note (optional)") }, supportingText = { Text("${note.length}/80") }, singleLine = true) }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Alert active", Modifier.weight(1f)); Switch(active, { active = it }) } }
        item { Button(onClick = {
            val parsed = target.toDoubleOrNull()
            if (selected.isBlank() || parsed == null || parsed <= 0) error = "Coin বাছুন এবং সঠিক target price দিন।"
            else { vm.addAlert(selected, parsed, active, note); onSaved() }
        }, Modifier.fillMaxWidth()) { Text("Save alert") } }
        if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun EnhancedActiveAlertsScreen(vm: AppViewModel) {
    val alerts by vm.alerts.collectAsState()
    val prices by vm.engine.prices.collectAsState()
    val list = alerts.filter { it.status != "HIT" }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Active alerts", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        items(list, key = { it.id }) { a ->
            Card(colors = CardDefaults.cardColors(containerColor = CardBg)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("${pairName(a.symbol)} • ${formatPrice(a.targetPrice)} USDT", fontWeight = FontWeight.Bold)
                    if (a.note.isNotBlank()) Text(a.note, color = Yellow)
                    Text("Now: ${prices[a.symbol]?.let { formatPrice(it.price) } ?: "--"} • ${if (a.status == "ACTIVE") "Watching" else "Paused"}", color = Muted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Enabled", Modifier.weight(1f)); Switch(a.status == "ACTIVE", { vm.setAlertActive(a.id, it) })
                        TextButton(onClick = { vm.deleteAlert(a.id) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

@Composable
fun EnhancedHistoryScreen(vm: AppViewModel) {
    val alerts by vm.alerts.collectAsState()
    val hits = alerts.filter { it.status == "HIT" }.sortedByDescending { it.hitAt ?: 0L }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Alert history", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { vm.clearHistory() }) { Text("Clear all") }
        } }
        items(hits, key = { it.id }) { a ->
            Card(colors = CardDefaults.cardColors(containerColor = CardBg)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("${pairName(a.symbol)} • ${a.direction ?: "HIT"}", fontWeight = FontWeight.Bold)
                    if (a.note.isNotBlank()) Text(a.note, color = Yellow)
                    Text("Target ${formatPrice(a.targetPrice)} • Hit ${a.hitPrice?.let { formatPrice(it) } ?: "--"} USDT")
                    Text(a.hitAt?.let { formatDateTime(it) } ?: "", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun ChartScreen(vm: AppViewModel) {
    val pairs by vm.pairs.collectAsState()
    val candles by vm.chartCandles.collectAsState()
    val loading by vm.chartLoading.collectAsState()
    val error by vm.chartError.collectAsState()
    var query by rememberSaveable { mutableStateOf("BTC") }
    var symbol by rememberSaveable { mutableStateOf("BTCUSDT") }
    var interval by rememberSaveable { mutableStateOf("1h") }
    LaunchedEffect(symbol, interval) { vm.loadCandles(symbol, interval) }
    val suggestions = if (query.equals(symbol.removeSuffix("USDT"), true)) emptyList() else pairs.filter { it.base.contains(query, true) }.take(6)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Price chart", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(query, { query = it.uppercase() }, Modifier.fillMaxWidth(), label = { Text("Search coin") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("1m", "5m", "15m", "1h", "4h", "1d").forEach { tf ->
                TextButton(onClick = { interval = tf }) { Text(if (interval == tf) "[$tf]" else tf) }
            }
        }
        suggestions.take(4).forEach { p -> Text(pairName(p.symbol), Modifier.fillMaxWidth().clickable { symbol = p.symbol; query = p.base }.padding(4.dp)) }
        Text("${pairName(symbol)} • $interval", color = Yellow, fontWeight = FontWeight.Bold)
        if (loading) Text("Chart loading…", color = Muted)
        if (error != null) Text(error ?: "", color = MaterialTheme.colorScheme.error)
        CandleChart(candles, Modifier.fillMaxWidth().weight(1f))
        candles.lastOrNull()?.let { Text("Latest close: ${formatPrice(it.close)} USDT", color = Muted) }
    }
}

@Composable
private fun CandleChart(candles: List<Candle>, modifier: Modifier = Modifier) {
    Canvas(modifier.height(260.dp).background(Color(0xFF1E2329), RoundedCornerShape(12.dp)).padding(8.dp)) {
        if (candles.isEmpty()) return@Canvas
        val min = candles.minOf { it.low }
        val max = candles.maxOf { it.high }
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val slot = size.width / candles.size
        candles.forEachIndexed { index, c ->
            val x = slot * (index + 0.5f)
            fun y(v: Double) = size.height - (((v - min) / range) * size.height).toFloat()
            val color = if (c.close >= c.open) Color(0xFF0ECB81) else Color(0xFFF6465D)
            drawLine(color, Offset(x, y(c.high)), Offset(x, y(c.low)), strokeWidth = 1.5f)
            val bodyTop = minOf(y(c.open), y(c.close))
            val bodyBottom = maxOf(y(c.open), y(c.close))
            drawLine(color, Offset(x, bodyTop), Offset(x, maxOf(bodyTop + 1f, bodyBottom)), strokeWidth = (slot * 0.55f).coerceAtLeast(1f))
        }
    }
}
