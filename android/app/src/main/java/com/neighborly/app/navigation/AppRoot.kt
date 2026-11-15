package com.neighborly.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.neighborly.app.R
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable data object HomeRoute
@Serializable data object MapRoute
@Serializable data object MarketRoute
@Serializable data object EventsRoute
@Serializable data object AlertsRoute

private data class Tab(val route: Any, val cls: KClass<*>, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(HomeRoute, HomeRoute::class, R.string.tab_home, Icons.Filled.Home),
    Tab(MapRoute, MapRoute::class, R.string.tab_map, Icons.Filled.Map),
    Tab(MarketRoute, MarketRoute::class, R.string.tab_market, Icons.Filled.Storefront),
    Tab(EventsRoute, EventsRoute::class, R.string.tab_events, Icons.Filled.Event),
    Tab(AlertsRoute, AlertsRoute::class, R.string.tab_alerts, Icons.Filled.Campaign),
)

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val entry = nav.currentBackStackEntryAsState().value
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = entry?.destination?.hasRoute(tab.cls) == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = HomeRoute, modifier = Modifier.padding(padding)) {
            composable<HomeRoute> { Placeholder(R.string.tab_home) }
            composable<MapRoute> { Placeholder(R.string.tab_map) }
            composable<MarketRoute> { Placeholder(R.string.tab_market) }
            composable<EventsRoute> { Placeholder(R.string.tab_events) }
            composable<AlertsRoute> { Placeholder(R.string.tab_alerts) }
        }
    }
}

@Composable
private fun Placeholder(label: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(label)) }
}
