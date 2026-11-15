package com.neighborly.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.Avatar
import com.neighborly.app.core.designsystem.MetaText
import com.neighborly.app.core.designsystem.NbCard
import com.neighborly.app.data.model.Profile
import com.neighborly.app.navigation.ChatsRoute
import com.neighborly.app.navigation.LostFoundRoute
import com.neighborly.app.navigation.ModerationRoute
import com.neighborly.app.navigation.NotificationsRoute
import com.neighborly.app.navigation.ProfileRoute
import com.neighborly.app.navigation.SearchRoute
import com.neighborly.app.navigation.ServicesRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(me: Profile, onBack: () -> Unit, onNavigate: (Any) -> Unit, onSignOut: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.menu_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NbCard(Modifier.clickable { onNavigate(ProfileRoute(userId = me.id)) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(me.avatarUrl, me.fullName, size = 48.dp)
                    Column {
                        Text(me.fullName, style = MaterialTheme.typography.titleSmall)
                        MetaText(stringResource(R.string.menu_see_profile))
                    }
                }
            }
            NbCard {
                MenuItem(Icons.Outlined.Search, stringResource(R.string.menu_feed_filters)) { onNavigate(SearchRoute) }
                MenuItem(Icons.Outlined.Handyman, stringResource(R.string.menu_services)) { onNavigate(ServicesRoute()) }
                MenuItem(Icons.Outlined.VolunteerActivism, stringResource(R.string.menu_help)) { onNavigate(ServicesRoute(helpTab = true)) }
                MenuItem(Icons.Outlined.TravelExplore, stringResource(R.string.menu_lost_found)) { onNavigate(LostFoundRoute()) }
                MenuItem(Icons.AutoMirrored.Outlined.Chat, stringResource(R.string.menu_messages)) { onNavigate(ChatsRoute) }
                MenuItem(Icons.Outlined.Notifications, stringResource(R.string.menu_notifications)) { onNavigate(NotificationsRoute) }
                if (me.isModerator) {
                    MenuItem(Icons.Outlined.Gavel, stringResource(R.string.menu_moderation)) { onNavigate(ModerationRoute) }
                }
                HorizontalDivider()
                MenuItem(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.action_log_out), onClick = onSignOut)
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
