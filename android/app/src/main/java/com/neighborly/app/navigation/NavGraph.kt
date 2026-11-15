package com.neighborly.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.CollectMessages
import com.neighborly.app.core.designsystem.SectionTitle
import com.neighborly.app.data.model.Profile
import com.neighborly.app.feature.feed.HomeScreen
import com.neighborly.app.feature.feed.SearchScreen
import com.neighborly.app.feature.feed.feedItems
import com.neighborly.app.feature.post.ComposerScreen
import com.neighborly.app.feature.post.PostDetailScreen
import com.neighborly.app.feature.post.PostNav
import com.neighborly.app.feature.post.rememberPostOverlays
import com.neighborly.app.feature.profile.EditProfileScreen
import com.neighborly.app.feature.profile.MenuScreen
import com.neighborly.app.feature.profile.ProfilePostsViewModel
import com.neighborly.app.feature.profile.ProfileScreen
import com.neighborly.app.feature.events.EventDetailScreen
import com.neighborly.app.feature.events.EventEditScreen
import com.neighborly.app.feature.events.EventsScreen
import com.neighborly.app.feature.lostfound.LostFoundDetailScreen
import com.neighborly.app.feature.lostfound.LostFoundEditScreen
import com.neighborly.app.feature.lostfound.LostFoundListScreen
import com.neighborly.app.feature.marketplace.ListingDetailScreen
import com.neighborly.app.feature.marketplace.ListingEditScreen
import com.neighborly.app.feature.marketplace.MarketScreen
import com.neighborly.app.feature.services.HelpDetailScreen
import com.neighborly.app.feature.services.HelpEditScreen
import com.neighborly.app.feature.services.ServiceDetailScreen
import com.neighborly.app.feature.services.ServiceEditScreen
import com.neighborly.app.feature.services.ServicesScreen
import androidx.navigation.toRoute
import com.neighborly.app.feature.alerts.AlertCreateScreen
import com.neighborly.app.feature.alerts.AlertsScreen
import com.neighborly.app.feature.alerts.NotificationsScreen
import com.neighborly.app.feature.messages.ChatScreen
import com.neighborly.app.feature.messages.ChatsScreen

@Composable
fun NeighborlyNavHost(nav: NavHostController, me: Profile, root: RootViewModel) {
    val postNav = remember(nav) {
        PostNav(
            openPost = { nav.navigate(PostRoute(it)) },
            openProfile = { nav.navigate(ProfileRoute(userId = it)) },
            editPost = { nav.navigate(ComposerRoute(editPostId = it)) },
        )
    }
    val openChat: (Long, String) -> Unit = remember(nav) { { id, name -> nav.navigate(ChatRoute(id, name)) } }
    NavHost(nav, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                nav = postNav,
                onCompose = { photo, loc, ask -> nav.navigate(ComposerRoute(withPhoto = photo, withLocation = loc, ask = ask)) },
                onOpen = { nav.navigate(it) },
            )
        }
        composable<MapRoute> { Placeholder(R.string.tab_map) }
        composable<MarketRoute> {
            MarketScreen(onOpen = { nav.navigate(ListingRoute(it)) }, onCreate = { nav.navigate(ListingEditRoute()) })
        }
        composable<ListingRoute> {
            ListingDetailScreen(
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(ListingEditRoute(it)) },
                onOpenProfile = postNav.openProfile,
                onOpenChat = openChat,
            )
        }
        composable<ListingEditRoute> {
            ListingEditScreen(onBack = { nav.popBackStack() }, onSaved = { id, created -> nav.afterSave(ListingRoute(id), created) })
        }
        composable<EventsRoute> {
            EventsScreen(onOpen = { nav.navigate(EventRoute(it)) }, onCreate = { nav.navigate(EventEditRoute()) })
        }
        composable<EventRoute> {
            EventDetailScreen(onBack = { nav.popBackStack() }, onEdit = { nav.navigate(EventEditRoute(it)) }, onOpenProfile = postNav.openProfile)
        }
        composable<EventEditRoute> {
            EventEditScreen(onBack = { nav.popBackStack() }, onSaved = { id, created -> nav.afterSave(EventRoute(id), created) })
        }
        composable<LostFoundRoute> { entry ->
            if (entry.toRoute<LostFoundRoute>().itemId == null) {
                LostFoundListScreen(
                    onBack = { nav.popBackStack() },
                    onOpen = { nav.navigate(LostFoundRoute(it)) },
                    onCreate = { nav.navigate(LostFoundEditRoute()) },
                )
            } else {
                LostFoundDetailScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(LostFoundEditRoute(it)) },
                    onOpenProfile = postNav.openProfile,
                    onOpenChat = openChat,
                )
            }
        }
        composable<LostFoundEditRoute> {
            LostFoundEditScreen(onBack = { nav.popBackStack() }, onSaved = { id, created -> nav.afterSave(LostFoundRoute(id), created) })
        }
        composable<ServicesRoute> {
            ServicesScreen(
                onBack = { nav.popBackStack() },
                onOpenService = { nav.navigate(ServiceRoute(it)) },
                onOpenHelp = { nav.navigate(HelpRoute(it)) },
                onCreateService = { nav.navigate(ServiceEditRoute()) },
                onAskHelp = { nav.navigate(HelpEditRoute) },
            )
        }
        composable<ServiceRoute> {
            ServiceDetailScreen(
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(ServiceEditRoute(it)) },
                onOpenProfile = postNav.openProfile,
                onOpenChat = openChat,
            )
        }
        composable<ServiceEditRoute> {
            ServiceEditScreen(onBack = { nav.popBackStack() }, onSaved = { id, created -> nav.afterSave(ServiceRoute(id), created) })
        }
        composable<HelpRoute> {
            HelpDetailScreen(onBack = { nav.popBackStack() }, onOpenProfile = postNav.openProfile, onOpenChat = openChat)
        }
        composable<HelpEditRoute> {
            HelpEditScreen(onBack = { nav.popBackStack() }, onSaved = { id -> nav.afterSave(HelpRoute(id), created = true) })
        }
        composable<AlertsRoute> {
            AlertsScreen(onCreate = { nav.navigate(AlertCreateRoute) }, onOpenProfile = postNav.openProfile, onChanged = root::refreshBadges)
        }
        composable<AlertCreateRoute> {
            AlertCreateScreen(onBack = { nav.popBackStack() }, onDone = {
                nav.popBackStack()
                nav.currentBackStackEntry?.savedStateHandle?.set("refresh", true)
            })
        }
        composable<ChatsRoute> {
            ChatsScreen(onBack = { nav.popBackStack() }, onOpen = openChat)
        }
        composable<ChatRoute> {
            ChatScreen(
                onBack = { root.refreshBadges(); nav.popBackStack() },
                onOpenProfile = postNav.openProfile,
                onOpenPost = postNav.openPost,
            )
        }
        composable<NotificationsRoute> {
            NotificationsScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(it) }, onChanged = root::refreshBadges)
        }

        composable<SearchRoute> { SearchScreen(postNav, onBack = { nav.popBackStack() }) }
        composable<PostRoute> { PostDetailScreen(postNav, onBack = { nav.popBackStack() }) }
        composable<ComposerRoute> {
            ComposerScreen(onBack = { nav.popBackStack() }, onDone = {
                nav.popBackStack()
                // Refresh whatever list we came back to.
                nav.currentBackStackEntry?.savedStateHandle?.set("refresh", true)
            })
        }

        composable<MenuRoute> {
            MenuScreen(me, onBack = { nav.popBackStack() }, onNavigate = { nav.navigate(it) }, onSignOut = root::signOut)
        }
        composable<ProfileRoute> {
            val postsVm: ProfilePostsViewModel = hiltViewModel()
            val posts by postsVm.posts.state.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            val overlays = rememberPostOverlays(postsVm, postNav, scope)
            CollectMessages(postsVm.messages)
            ProfileScreen(
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(EditProfileRoute) },
                onOpenChat = { id, name -> nav.navigate(ChatRoute(id, name)) },
                onUserLoaded = postsVm::bind,
                overlay = overlays.render,
                extraContent = {
                    item(key = "posts-title") { SectionTitle(stringResource(R.string.profile_posts)) }
                    feedItems(posts, postsVm.myId, postsVm.canModerate, overlays.callbacks, onRetry = { postsVm.posts.reload() },
                        emptyTitle = R.string.profile_no_posts)
                },
            )
        }
        composable<EditProfileRoute> { EditProfileScreen(onBack = { nav.popBackStack() }) }
    }
}

/** After a create/edit form: close it, ask the previous list to refresh, and open the new item. */
private fun NavHostController.afterSave(detail: Any, created: Boolean) {
    popBackStack()
    currentBackStackEntry?.savedStateHandle?.set("refresh", true)
    if (created) navigate(detail)
}

@Composable
private fun Placeholder(label: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(label)) }
}
