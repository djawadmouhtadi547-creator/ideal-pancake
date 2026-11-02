package com.daftar.kredi

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.daftar.kredi.ui.AppTheme
import com.daftar.kredi.ui.AppViewModel
import com.daftar.kredi.ui.CalculatorScreen
import com.daftar.kredi.ui.CustomerDetailScreen
import com.daftar.kredi.ui.CustomersScreen
import com.daftar.kredi.ui.LockScreen
import com.daftar.kredi.ui.CashierScreen
import com.daftar.kredi.ui.CatalogScreen
import com.daftar.kredi.ui.SettingsScreen
import com.daftar.kredi.ui.StatsScreen

class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by viewModels()
    private val locked = mutableStateOf(false)
    private var leftAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        locked.value = vm.settings.pinEnabled.value
        setContent {
            AppTheme {
                // الواجهة عربية: من اليمين لليسار دائماً
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val isLocked by locked
                    if (isLocked) {
                        LockScreen(vm) { locked.value = false }
                    } else {
                        AppRoot(vm)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        leftAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (leftAt != 0L && vm.settings.pinEnabled.value &&
            SystemClock.elapsedRealtime() - leftAt > 30_000
        ) {
            locked.value = true
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun AppRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    val tabs = listOf(
        Tab("customers", "الزبائن", Icons.Default.Person),
        Tab("cashier", "الكاشير", Icons.Default.ShoppingCart),
        Tab("stats", "الإحصائيات", Icons.Default.DateRange),
        Tab("calc", "الحاسبة", Icons.Default.Build),
        Tab("settings", "الإعدادات", Icons.Default.Settings)
    )
    val showBar = tabs.any { it.route == route }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo("customers") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }
    ) { pad: PaddingValues ->
        NavHost(
            navController = nav,
            startDestination = "customers",
            modifier = Modifier.padding(pad).consumeWindowInsets(pad).imePadding()
        ) {
            composable("customers") {
                CustomersScreen(vm) { id -> nav.navigate("customer/$id") }
            }
            composable(
                "customer/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                CustomerDetailScreen(vm, entry.arguments?.getLong("id") ?: 0L) { nav.popBackStack() }
            }
            composable("stats") { StatsScreen(vm) }
            composable("cashier") { CashierScreen(vm) { nav.navigate("catalog") } }
            composable("catalog") { CatalogScreen(vm) { nav.popBackStack() } }
            composable("calc") { CalculatorScreen() }
            composable("settings") { SettingsScreen(vm) }
        }
    }
}
