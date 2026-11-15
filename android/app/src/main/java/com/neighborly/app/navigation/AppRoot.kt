package com.neighborly.app.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.Avatar
import com.neighborly.app.core.designsystem.ErrorState
import com.neighborly.app.core.supabase.AuthState
import com.neighborly.app.data.model.Profile
import com.neighborly.app.feature.auth.BlockedScreen
import com.neighborly.app.feature.auth.ForgotPasswordScreen
import com.neighborly.app.feature.auth.LoginScreen
import com.neighborly.app.feature.auth.NewPasswordScreen
import com.neighborly.app.feature.auth.SignupScreen
import kotlin.reflect.KClass

private data class Tab(val route: Any, val cls: KClass<*>, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(HomeRoute, HomeRoute::class, R.string.tab_home, Icons.Filled.Home),
    Tab(MapRoute, MapRoute::class, R.string.tab_map, Icons.Filled.Map),
    Tab(MarketRoute, MarketRoute::class, R.string.tab_market, Icons.Filled.Storefront),
    Tab(EventsRoute, EventsRoute::class, R.string.tab_events, Icons.Filled.Event),
    Tab(AlertsRoute, AlertsRoute::class, R.string.tab_alerts, Icons.Filled.Campaign),
)

@Composable
fun AppRoot(vm: RootViewModel = hiltViewModel()) {
    val auth by vm.authState.collectAsStateWithLifecycle()
    when (val s = auth) {
        AuthState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        AuthState.SignedOut -> SignedOutGraph()
        AuthState.PasswordRecovery -> NewPasswordScreen(onSignOut = vm::signOut)
        is AuthState.Blocked -> BlockedScreen(s.profile.status, onSignOut = vm::signOut)
        is AuthState.ProfileError -> ErrorState(s.message, onRetry = vm::signOut)
        is AuthState.SignedIn -> MainShell(s.profile, vm)
    }
}

@Composable
private fun SignedOutGraph() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = LoginRoute) {
        composable<LoginRoute> {
            LoginScreen(onSignup = { nav.navigate(SignupRoute) }, onForgot = { nav.navigate(ForgotPasswordRoute) })
        }
        composable<SignupRoute> { SignupScreen(onLogin = { nav.popBackStack(LoginRoute, inclusive = false) }) }
        composable<ForgotPasswordRoute> { ForgotPasswordScreen(onBack = { nav.popBackStack() }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(me: Profile, vm: RootViewModel) {
    val nav = rememberNavController()
    val badges by vm.badges.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryAsState()
    val onTab = tabs.any { entry?.destination?.hasRoute(it.cls) == true }
    val snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }

    androidx.compose.runtime.CompositionLocalProvider(com.neighborly.app.core.designsystem.LocalSnackbar provides snackbar) {
    Scaffold(
        // Inner screens own their insets (their own Scaffolds); tab chrome handles its own.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        topBar = {
            if (onTab) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(34.dp))
                            Text(stringResource(R.string.app_name), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        IconButton(onClick = { nav.navigate(SearchRoute) }) {
                            Icon(Icons.Outlined.Search, stringResource(R.string.cd_search))
                        }
                        BadgeIcon(badges.chats, Icons.AutoMirrored.Outlined.Chat, stringResource(R.string.cd_chats)) { nav.navigate(ChatsRoute) }
                        BadgeIcon(badges.notifications, Icons.Outlined.Notifications, stringResource(R.string.cd_notifications)) {
                            nav.navigate(NotificationsRoute)
                        }
                        IconButton(onClick = { nav.navigate(MenuRoute) }) {
                            Avatar(me.avatarUrl, me.fullName, size = 32.dp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        },
        bottomBar = {
            if (onTab) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = entry?.destination?.hasRoute(tab.cls) == true,
                            onClick = { nav.navigateTab(tab.route) },
                            icon = {
                                if (tab.route == AlertsRoute && badges.alerts > 0) {
                                    BadgedBox(badge = { Badge { Text(badges.alerts.toString()) } }) { Icon(tab.icon, null) }
                                } else Icon(tab.icon, null)
                            },
                            label = { Text(stringResource(tab.label), maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            NeighborlyNavHost(nav, me, vm)
        }
    }
    }
}

@Composable
private fun BadgeIcon(count: Int, icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        if (count > 0) {
            BadgedBox(badge = { Badge { Text(if (count > 99) "99+" else count.toString()) } }) { Icon(icon, description) }
        } else Icon(icon, description)
    }
}

fun NavHostController.navigateTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
