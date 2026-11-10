package com.binancealert.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.binancealert.app.data.ConnState
import com.binancealert.app.data.PriceAlert
import com.binancealert.app.data.PriceTick
import com.binancealert.app.data.formatClock
import com.binancealert.app.data.formatDateTime
import com.binancealert.app.data.formatPrice
import com.binancealert.app.data.pairName
import com.binancealert.app.notify.Notifier

// ---------------------------------------------------------------- shared bits

@Composable
private fun AppCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Box(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Yellow)
            Text(label, fontSize = 12.sp, color = Muted)
        }
    }
}

private fun stateColor(s: ConnState): Color = when (s) {
    ConnState.CONNECTED -> Green
    ConnState.CONNECTING, ConnState.RECONNECTING -> Yellow
    ConnState.NO_INTERNET -> Red
    ConnState.STOPPED -> Muted
}

@Composable
private fun Title(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
}

// ---------------------------------------------------------------- dashboard

@Composable
fun DashboardScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsState()
    val watch by vm.watch.collectAsState()
    val alerts by vm.alerts.collectAsState()
    val state by vm.engine.state.collectAsState()
    val prices by vm.engine.prices.collectAsState()
    val last by vm.engine.lastUpdate.collectAsState()

    val activeSymbols = alerts.filter { it.status == "ACTIVE" }.map { it.symbol }
    val symbols = (watch.map { it.symbol } + activeSymbols).distinct()
    val hitCount = alerts.count { it.status == "HIT" }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(stateColor(state), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text("WebSocket: " + state.label, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        if (last > 0) "Last price update: " + formatClock(last) else "No price received yet",
                        color = Muted, fontSize = 13.sp
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Monitoring", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (settings.monitoring) "ON - runs in background" else "OFF - alerts will not fire",
                                color = Muted, fontSize = 13.sp
                            )
                        }
                        Switch(checked = settings.monitoring, onCheckedChange = { vm.setMonitoring(it) })
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Monitored coins", symbols.size.toString(), Modifier.weight(1f))
                Stat("Active alerts", activeSymbols.size.toString(), Modifier.weight(1f))
                Stat("Triggered", hitCount.toString(), Modifier.weight(1f))
            }
        }
        item { Title("Live prices") }
        if (symbols.isEmpty()) {
            item {
                Text(
                    "No coins yet. Open the Coins tab and add some coins, or create an alert.",
                    color = Muted
                )
            }
        }
        items(symbols, key = { it }) { sym ->
            PriceCard(
                symbol = sym,
                tick = prices[sym],
                live = state == ConnState.CONNECTED,
                alertCount = activeSymbols.count { it == sym },
                onRemove = { vm.toggleWatch(sym, false) }
            )
        }
    }
}

@Composable
private fun PriceCard(symbol: String, tick: PriceTick?, live: Boolean, alertCount: Int, onRemove: () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pairName(symbol), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    when {
                        tick == null -> "Waiting for first trade..."
                        live -> "Updated " + formatClock(tick.time)
                        else -> "Not live - last known " + formatClock(tick.time)
                    },
                    color = if (tick != null && !live) Yellow else Muted,
                    fontSize = 12.sp
                )
                if (alertCount > 0) {
                    Text("$alertCount active alert(s)", color = Yellow, fontSize = 12.sp)
                }
            }
            Text(
                if (tick != null) formatPrice(tick.price) else "--",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Muted)
            }
        }
    }
}

// ---------------------------------------------------------------- coin list

@Composable
fun CoinListScreen(vm: AppViewModel, onAlert: (String) -> Unit) {
    DisposableEffect(Unit) {
        vm.ticker.start()
        onDispose { vm.ticker.stop() }
    }
    val pairs by vm.pairs.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.pairsError.collectAsState()
    val live by vm.ticker.prices.collectAsState()
    val tickerOn by vm.ticker.connected.collectAsState()
    val engPrices by vm.engine.prices.collectAsState()
    val watch by vm.watch.collectAsState()
    val watched = watch.map { it.symbol }.toSet()

    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(pairs, query) {
        val t = query.trim().uppercase()
        if (t.isEmpty()) pairs
        else pairs.filter { it.base.contains(t) }.sortedBy { if (it.base.startsWith(t)) 0 else 1 }
    }

    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search coin (e.g. BTC, RLC)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            Text(
                "${filtered.size} of ${pairs.size} Spot USDT pairs  |  prices: " +
                    if (tickerOn) "live" else "connecting...",
                color = Muted, fontSize = 12.sp
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error != null) {
                Text(error ?: "", color = Red, fontSize = 13.sp)
                OutlinedButton(onClick = { vm.loadPairs() }) { Text("Retry") }
            }
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items(filtered, key = { it.symbol }) { p ->
                val price = live[p.symbol] ?: engPrices[p.symbol]?.price
                val isWatched = p.symbol in watched
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.base, fontWeight = FontWeight.Bold)
                        Text("/USDT", color = Muted, fontSize = 12.sp)
                    }
                    Text(
                        if (price != null) formatPrice(price) else "--",
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(onClick = { onAlert(p.symbol) }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Create alert", tint = Yellow)
                    }
                    if (isWatched) {
                        OutlinedButton(onClick = { vm.toggleWatch(p.symbol, false) }) { Text("Remove") }
                    } else {
                        Button(onClick = { vm.toggleWatch(p.symbol, true) }) { Text("Add") }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// ---------------------------------------------------------------- add alert

@Composable
fun AddAlertScreen(vm: AppViewModel, onSaved: () -> Unit) {
    val pairs by vm.pairs.collectAsState()
    val engPrices by vm.engine.prices.collectAsState()
    val initial = remember { vm.alertSymbol }
    LaunchedEffect(Unit) { vm.alertSymbol = "" }

    var query by rememberSaveable { mutableStateOf(initial.removeSuffix("USDT")) }
    var selected by rememberSaveable { mutableStateOf(initial) }
    var priceText by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(true) }
    var current by remember { mutableStateOf<Double?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selected) {
        current = null
        if (selected.isNotEmpty()) current = vm.fetchPrice(selected)
    }

    val suggestions = remember(pairs, query, selected) {
        if (selected.isNotEmpty() || query.isEmpty()) emptyList()
        else {
            val starts = pairs.filter { it.base.startsWith(query) }
            val contains = pairs.filter { !it.base.startsWith(query) && it.base.contains(query) }
            (starts + contains).take(6)
        }
    }
    val target = priceText.toDoubleOrNull()
    val now = (engPrices[selected]?.price) ?: current

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Title("Create price alert") }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { v ->
                    query = v.uppercase().filter { it.isLetterOrDigit() }
                    selected = ""
                    message = null
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Coin (e.g. RLC)") },
                supportingText = { Text("Spot USDT pairs only") }
            )
        }
        items(suggestions, key = { it.symbol }) { p ->
            Text(
                pairName(p.symbol),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selected = p.symbol
                        query = p.base
                    }
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                fontWeight = FontWeight.SemiBold
            )
        }
        if (selected.isNotEmpty()) {
            item {
                AppCard {
                    Column {
                        Text(pairName(selected), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "Current price: " + (now?.let { formatPrice(it) } ?: "loading..."),
                            color = Muted
                        )
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = priceText,
                onValueChange = { v ->
                    priceText = v.replace(',', '.').filter { it.isDigit() || it == '.' }
                    message = null
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Exact target price (USDT)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
        item {
            val hint = when {
                target == null || target <= 0.0 || now == null -> null
                target < now -> "Will fire when the price FALLS to " + formatPrice(target)
                target > now -> "Will fire when the price RISES to " + formatPrice(target)
                else -> "Price is exactly at the target - will fire on the next trade"
            }
            if (hint != null) Text(hint, color = Yellow, fontSize = 13.sp)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Alert active", Modifier.weight(1f))
                Switch(checked = active, onCheckedChange = { active = it })
            }
        }
        item {
            Button(
                onClick = {
                    val t = priceText.toDoubleOrNull()
                    when {
                        selected.isEmpty() -> message = "Please pick a coin from the suggestions."
                        t == null || t <= 0.0 -> message = "Enter a valid target price."
                        else -> {
                            vm.addAlert(selected, t, active)
                            priceText = ""
                            onSaved()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save alert") }
        }
        if (message != null) item { Text(message ?: "", color = Red) }
    }
}

// ---------------------------------------------------------------- active alerts

@Composable
fun ActiveAlertsScreen(vm: AppViewModel) {
    val alerts by vm.alerts.collectAsState()
    val prices by vm.engine.prices.collectAsState()
    val list = alerts.filter { it.status != "HIT" }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Title("Active alerts") }
        if (list.isEmpty()) item { Text("No alerts yet. Use the Add Alert tab.", color = Muted) }
        items(list, key = { it.id }) { a ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pairName(a.symbol), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "Target " + formatPrice(a.targetPrice),
                            fontFamily = FontFamily.Monospace, color = Yellow
                        )
                        Text(
                            "Now: " + (prices[a.symbol]?.let { formatPrice(it.price) } ?: "--") +
                                "  |  " + (if (a.status == "ACTIVE") "Watching" else "Paused"),
                            color = Muted, fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = a.status == "ACTIVE",
                        onCheckedChange = { vm.setAlertActive(a.id, it) }
                    )
                    IconButton(onClick = { vm.deleteAlert(a.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Red)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- history

@Composable
fun HistoryScreen(vm: AppViewModel) {
    val alerts by vm.alerts.collectAsState()
    val hits = alerts.filter { it.status == "HIT" }.sortedByDescending { it.hitAt ?: 0L }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Title("Alert history") }
                if (hits.isNotEmpty()) TextButton(onClick = { vm.clearHistory() }) { Text("Clear all") }
            }
        }
        if (hits.isEmpty()) item { Text("No alert has been triggered yet.", color = Muted) }
        items(hits, key = { it.id }) { a -> HistoryCard(a) }
    }
}

@Composable
private fun HistoryCard(a: PriceAlert) {
    val dir = a.direction ?: ""
    val color = when (dir) {
        "UPWARD" -> Green
        "DOWNWARD" -> Red
        else -> Yellow
    }
    val arrow = when (dir) {
        "UPWARD" -> "\u25B2 "
        "DOWNWARD" -> "\u25BC "
        else -> "\u25CF "
    }
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pairName(a.symbol), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(arrow + dir, color = color, fontWeight = FontWeight.Bold)
            }
            Text("Target price: " + formatPrice(a.targetPrice), fontFamily = FontFamily.Monospace)
            Text(
                "Hit price: " + (a.hitPrice?.let { formatPrice(it) } ?: "--"),
                fontFamily = FontFamily.Monospace
            )
            Text(
                "Triggered: " + (a.hitAt?.let { formatDateTime(it) } ?: "--"),
                color = Muted, fontSize = 12.sp
            )
        }
    }
}

// ---------------------------------------------------------------- settings

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val settings by vm.settings.collectAsState()

    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(ctx) {
        val owner = ctx as? LifecycleOwner
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) refresh++
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    val notifOk = remember(refresh) { Notifier.canNotify(ctx) }
    val batteryOk = remember(refresh) {
        val pm = ctx.getSystemService(PowerManager::class.java)
        pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun open(intent: Intent) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Title("Settings") }
        item {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SwitchRow("Notification sound", settings.sound) { v -> vm.updateSettings { it.copy(sound = v) } }
                    SwitchRow("Vibration", settings.vibration) { v -> vm.updateSettings { it.copy(vibration = v) } }
                    OutlinedButton(
                        onClick = { Notifier.test(ctx, settings.sound, settings.vibration) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Send test notification") }
                }
            }
        }
        item {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Notifications", fontWeight = FontWeight.Bold)
                    Text(
                        if (notifOk) "Permission granted." else "Notifications are BLOCKED - alerts cannot be shown.",
                        color = if (notifOk) Green else Red, fontSize = 13.sp
                    )
                    OutlinedButton(onClick = {
                        open(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        )
                    }) { Text("Open notification settings") }
                }
            }
        }
        item {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Background reliability", fontWeight = FontWeight.Bold)
                    SwitchRow("Keep CPU awake while monitoring", settings.keepAwake) { v ->
                        vm.updateSettings { it.copy(keepAwake = v) }
                    }
                    Text(
                        "ON is more reliable with the screen locked but uses more battery.",
                        color = Muted, fontSize = 12.sp
                    )
                    Text(
                        if (batteryOk) "Battery optimization: unrestricted (good)."
                        else "Battery optimization: ON - Android may pause the app in the background.",
                        color = if (batteryOk) Green else Yellow, fontSize = 13.sp
                    )
                    OutlinedButton(onClick = {
                        open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }) { Text("Open battery optimization list") }
                    Text(
                        "In that list choose \"All apps\", find BINANCE PRICE ALERT and select \"Don't optimize\". " +
                            "On Xiaomi, Oppo, Vivo, Realme, Huawei and Samsung phones also allow \"Autostart\" / " +
                            "\"Background activity\" for this app in the phone's own settings.",
                        color = Muted, fontSize = 12.sp
                    )
                }
            }
        }
        item {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Good to know", fontWeight = FontWeight.Bold)
                    Text(
                        "Monitoring cannot work if you force-stop the app, if the phone is switched off, or " +
                            "if the phone has no internet. After a restart the app tries to resume by itself, " +
                            "but some phones block this - open the app once if the notification is missing.",
                        color = Muted, fontSize = 12.sp
                    )
                    Text(
                        "Only public Binance price data is used. No login, no API key, no trading, no tracking. " +
                            "All alerts stay on this device.",
                        color = Muted, fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
