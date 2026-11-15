package com.neighborly.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.neighborly.app.R
import com.neighborly.app.data.model.Profile
import com.neighborly.app.feature.profile.EditProfileScreen
import com.neighborly.app.feature.profile.MenuScreen
import com.neighborly.app.feature.profile.ProfileScreen

@Composable
fun NeighborlyNavHost(nav: NavHostController, me: Profile, root: RootViewModel) {
    NavHost(nav, startDestination = HomeRoute) {
        composable<HomeRoute> { Placeholder(R.string.tab_home) }
        composable<MapRoute> { Placeholder(R.string.tab_map) }
        composable<MarketRoute> { Placeholder(R.string.tab_market) }
        composable<EventsRoute> { Placeholder(R.string.tab_events) }
        composable<AlertsRoute> { Placeholder(R.string.tab_alerts) }

        composable<MenuRoute> {
            MenuScreen(me, onBack = { nav.popBackStack() }, onNavigate = { nav.navigate(it) }, onSignOut = root::signOut)
        }
        composable<ProfileRoute> {
            ProfileScreen(
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(EditProfileRoute) },
                onOpenChat = { id, name -> nav.navigate(ChatRoute(id, name)) },
            )
        }
        composable<EditProfileRoute> { EditProfileScreen(onBack = { nav.popBackStack() }) }
    }
}

@Composable
private fun Placeholder(label: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(label)) }
}
