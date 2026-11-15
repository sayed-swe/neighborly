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

@Composable
fun NeighborlyNavHost(nav: NavHostController, me: Profile, root: RootViewModel) {
    val postNav = remember(nav) {
        PostNav(
            openPost = { nav.navigate(PostRoute(it)) },
            openProfile = { nav.navigate(ProfileRoute(userId = it)) },
            editPost = { nav.navigate(ComposerRoute(editPostId = it)) },
        )
    }
    NavHost(nav, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                nav = postNav,
                onCompose = { photo, loc, ask -> nav.navigate(ComposerRoute(withPhoto = photo, withLocation = loc, ask = ask)) },
                onOpen = { nav.navigate(it) },
            )
        }
        composable<MapRoute> { Placeholder(R.string.tab_map) }
        composable<MarketRoute> { Placeholder(R.string.tab_market) }
        composable<EventsRoute> { Placeholder(R.string.tab_events) }
        composable<AlertsRoute> { Placeholder(R.string.tab_alerts) }

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

@Composable
private fun Placeholder(label: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(label)) }
}
