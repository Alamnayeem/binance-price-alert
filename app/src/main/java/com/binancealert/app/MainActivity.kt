package com.binancealert.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.binancealert.app.ui.ActiveAlertsScreen
import com.binancealert.app.ui.AddAlertScreen
import com.binancealert.app.ui.AppTheme
import com.binancealert.app.ui.AppViewModel
import com.binancealert.app.ui.ChartScreen
import com.binancealert.app.ui.CoinListScreen
import com.binancealert.app.ui.DashboardScreen
import com.binancealert.app.ui.EnhancedActiveAlertsScreen
import com.binancealert.app.ui.EnhancedAddAlertScreen
import com.binancealert.app.ui.EnhancedDashboardScreen
import com.binancealert.app.ui.EnhancedHistoryScreen
import com.binancealert.app.ui.HistoryScreen
import com.binancealert.app.ui.SettingsScreen
import com.binancealert.app.ui.Surface0
import com.binancealert.app.ui.Yellow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        setContent { AppTheme { Root() } }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)
private val tabs = listOf(
    Tab("dashboard", "Home", Icons.Default.Home),
    Tab("coins", "Coins", Icons.AutoMirrored.Filled.List),
    Tab("chart", "Chart", Icons.Default.ShowChart),
    Tab("add", "Add", Icons.Default.Add),
    Tab("active", "Active", Icons.Default.Notifications),
    Tab("history", "History", Icons.Default.DateRange)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Root(vm: AppViewModel = viewModel()) {
    val ctx = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "dashboard"
    fun go(target: String, restore: Boolean = true) { nav.navigate(target) {
        popUpTo("dashboard") { saveState = true }; launchSingleTop = true; restoreState = restore
    } }
    Scaffold(
        topBar = { TopAppBar(title = { Text("BINANCE PRICE ALERT", color = Yellow, fontWeight = FontWeight.Bold) },
            actions = { IconButton(onClick = { go("settings") }) { Icon(Icons.Default.Settings, contentDescription = "Settings") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface0, actionIconContentColor = MaterialTheme.colorScheme.onSurface)) },
        bottomBar = { NavigationBar(containerColor = Surface0) { tabs.forEach { t ->
            NavigationBarItem(selected = route == t.route, onClick = { go(t.route) }, icon = { Icon(t.icon, contentDescription = t.label) },
                label = { Text(t.label, maxLines = 1) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Surface0, selectedTextColor = Yellow, indicatorColor = Yellow))
        } } }
    ) { pad ->
        NavHost(nav, startDestination = "dashboard", modifier = Modifier.padding(pad)) {
            composable("dashboard") { EnhancedDashboardScreen(vm) }
            composable("coins") { CoinListScreen(vm) { symbol -> vm.alertSymbol = symbol; go("add", restore = false) } }
            composable("chart") { ChartScreen(vm) }
            composable("add") { EnhancedAddAlertScreen(vm) { go("active") } }
            composable("active") { EnhancedActiveAlertsScreen(vm) }
            composable("history") { EnhancedHistoryScreen(vm) }
            composable("settings") { SettingsScreen(vm) }
        }
    }
}
