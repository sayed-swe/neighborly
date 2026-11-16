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
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavBackStackEntry
import com.neighborly.app.feature.alerts.AlertsViewModel
import com.neighborly.app.feature.events.EventDetailViewModel
import com.neighborly.app.feature.events.EventsViewModel
import com.neighborly.app.feature.feed.HomeViewModel
import com.neighborly.app.feature.lostfound.LostFoundDetailViewModel
import com.neighborly.app.feature.lostfound.LostFoundListViewModel
import com.neighborly.app.feature.marketplace.ListingDetailViewModel
import com.neighborly.app.feature.marketplace.MarketViewModel
import com.neighborly.app.feature.moderation.ModerationViewModel
import com.neighborly.app.feature.post.PostDetailViewModel
import com.neighborly.app.feature.services.ServiceDetailViewModel
import com.neighborly.app.feature.services.ServicesViewModel
import com.neighborly.app.feature.map.MapScreen
import com.neighborly.app.feature.moderation.ModerationScreen
import com.neighborly.app.feature.moderation.ReportReviewScreen
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
        composable<HomeRoute> { entry ->
            val vm: HomeViewModel = hiltViewModel()
            entry.OnRefreshRequest(vm::refresh)
            HomeScreen(
                vm = vm,
                nav = postNav,
                onCompose = { photo, loc, ask -> nav.navigate(ComposerRoute(withPhoto = photo, withLocation = loc, ask = ask)) },
                onOpen = { nav.navigate(it) },
            )
        }
        composable<MapRoute> { MapScreen(onOpen = { nav.navigate(it) }) }
        composable<MarketRoute> { entry ->
            val vm: MarketViewModel = hiltViewModel()
            entry.OnRefreshRequest { vm.load(true) }
            MarketScreen(onOpen = { nav.navigate(ListingRoute(it)) }, onCreate = { nav.navigate(ListingEditRoute()) }, vm = vm)
        }
        composable<ListingRoute> { entry ->
            val vm: ListingDetailViewModel = hiltViewModel()
            entry.OnRefreshRequest(vm::load)
            ListingDetailScreen(
                vm = vm,
                onBack = { nav.popAndRefresh() },
                onEdit = { nav.navigate(ListingEditRoute(it)) },
                onOpenProfile = postNav.openProfile,
                onOpenChat = openChat,
            )
        }
        composable<ListingEditRoute> {
            ListingEditScreen(onBack = { nav.popBackStack() }, onSaved = { id, created -> nav.afterSave(ListingRoute(id), created) })
        }
        composable<EventsRoute> { entry ->
            val vm: EventsViewModel = hiltViewModel()
            entry.OnRefreshRequest { vm.load(true) }
            EventsScreen(onOpen = { nav.navigate(EventRoute(it)) }, onCreate = { nav.navigate(EventEditRoute()) }, vm = vm)
        }
        composable<EventRoute> { entry ->
            val vm: EventDetailViewModel = hiltViewModel()
            entry.OnRefreshRequest(vm::load)
            EventDetailScreen(
                onBack = { nav.popAndRefresh() }, onEdit = { nav.navigate(EventEditRoute(it)) },
                onOpenProfile = postNav.openProfile, vm = vm,
            )
        }
        composable<EventEditRoute> {
            EventEditScreen(onBack = { nav.popBackStack() }, onSaved = { id, created -> nav.afterSave(EventRoute(id), created) })
        }
        composable<LostFoundRoute> { entry ->
            if (entry.toRoute<LostFoundRoute>().itemId == null) {
                val vm: LostFoundListViewModel = hiltViewModel()
                entry.OnRefreshRequest { vm.load(true) }
                LostFoundListScreen(
                    onBack = { nav.popBackStack() },
                    onOpen = { nav.navigate(LostFoundRoute(it)) },
                    onCreate = { nav.navigate(LostFoundEditRoute()) },
                    vm = vm,
                )
            } else {
                val vm: LostFoundDetailViewModel = hiltViewModel()
                entry.OnRefreshRequest(vm::load)
                LostFoundDetailScreen(
                    vm = vm,
                    onBack = { nav.popAndRefresh() },
                    onEdit = { nav.navigate(LostFoundEditRoute(it)) },
                    onOpenProfile = postNav.openProfile,
                    onOpenChat = openChat,
                )
            }
        }
        composable<LostFoundEditRoute> {
            LostFoundEditScreen(onBack = { nav.popBackStack() }, onSaved = { id, created -> nav.afterSave(LostFoundRoute(id), created) })
        }
        composable<ServicesRoute> { entry ->
            val vm: ServicesViewModel = hiltViewModel()
            entry.OnRefreshRequest(vm::refresh)
            ServicesScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenService = { nav.navigate(ServiceRoute(it)) },
                onOpenHelp = { nav.navigate(HelpRoute(it)) },
                onCreateService = { nav.navigate(ServiceEditRoute()) },
                onAskHelp = { nav.navigate(HelpEditRoute) },
            )
        }
        composable<ServiceRoute> { entry ->
            val vm: ServiceDetailViewModel = hiltViewModel()
            entry.OnRefreshRequest(vm::load)
            ServiceDetailScreen(
                vm = vm,
                onBack = { nav.popAndRefresh() },
                onEdit = { nav.navigate(ServiceEditRoute(it)) },
                onOpenProfile = postNav.openProfile,
                onOpenChat = openChat,
            )
        }
        composable<ServiceEditRoute> {
            ServiceEditScreen(onBack = { nav.popBackStack() }, onSaved = { id, created -> nav.afterSave(ServiceRoute(id), created) })
        }
        composable<HelpRoute> {
            HelpDetailScreen(onBack = { nav.popAndRefresh() }, onOpenProfile = postNav.openProfile, onOpenChat = openChat)
        }
        composable<HelpEditRoute> {
            HelpEditScreen(onBack = { nav.popBackStack() }, onSaved = { id -> nav.afterSave(HelpRoute(id), created = true) })
        }
        composable<AlertsRoute> { entry ->
            val vm: AlertsViewModel = hiltViewModel()
            entry.OnRefreshRequest { vm.load(true); root.refreshBadges() }
            AlertsScreen(onCreate = { nav.navigate(AlertCreateRoute) }, onOpenProfile = postNav.openProfile, onChanged = root::refreshBadges, vm = vm)
        }
        composable<AlertCreateRoute> {
            AlertCreateScreen(onBack = { nav.popBackStack() }, onDone = { nav.popAndRefresh() })
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
        composable<PostRoute> { entry ->
            val vm: PostDetailViewModel = hiltViewModel()
            entry.OnRefreshRequest(vm::load)
            PostDetailScreen(postNav, onBack = { nav.popAndRefresh() }, vm = vm)
        }
        composable<ComposerRoute> {
            // Refresh whatever list or post we came back to.
            ComposerScreen(onBack = { nav.popBackStack() }, onDone = { nav.popAndRefresh() })
        }

        composable<MenuRoute> {
            MenuScreen(me, onBack = { nav.popBackStack() }, onNavigate = { nav.navigate(it) }, onSignOut = root::signOut)
        }
        composable<ProfileRoute> { entry ->
            val postsVm: ProfilePostsViewModel = hiltViewModel()
            entry.OnRefreshRequest { postsVm.posts.reload(refresh = true) }
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
        composable<ModerationRoute> { entry ->
            val vm: ModerationViewModel = hiltViewModel()
            entry.OnRefreshRequest { vm.load(true) }
            ModerationScreen(onBack = { nav.popBackStack() }, onReview = { nav.navigate(ReportReviewRoute(it)) }, vm = vm)
        }
        composable<ReportReviewRoute> {
            ReportReviewScreen(
                onBack = { nav.popBackStack() },
                onDone = { nav.popAndRefresh() },
                onOpen = { nav.navigate(it) },
                onOpenProfile = postNav.openProfile,
            )
        }
    }
}

private const val REFRESH = "refresh"

/** Go back and ask the screen we return to to reload (after saves, deletes, status / RSVP changes). */
private fun NavHostController.popAndRefresh() {
    popBackStack()
    currentBackStackEntry?.savedStateHandle?.set(REFRESH, true)
}

/** After a create/edit form: close it, refresh the previous screen, and open the new item. */
private fun NavHostController.afterSave(detail: Any, created: Boolean) {
    popAndRefresh()
    if (created) navigate(detail)
}

/**
 * Runs [action] when another screen asked this entry to refresh. The flag lives on the entry's own
 * savedStateHandle (not the ViewModel's), so it must be observed here.
 */
@Composable
private fun NavBackStackEntry.OnRefreshRequest(action: () -> Unit) {
    val requested by savedStateHandle.getStateFlow(REFRESH, false).collectAsStateWithLifecycle()
    LaunchedEffect(requested) {
        if (requested) {
            savedStateHandle[REFRESH] = false
            action()
        }
    }
}

@Composable
private fun Placeholder(label: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(label)) }
}
