package com.neighborly.app.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.neighborly.app.R
import com.neighborly.app.core.FeedScope
import com.neighborly.app.core.designsystem.Avatar
import com.neighborly.app.core.designsystem.CollectMessages
import com.neighborly.app.core.designsystem.EmptyState
import com.neighborly.app.core.designsystem.ErrorState
import com.neighborly.app.core.designsystem.ListSkeleton
import com.neighborly.app.core.designsystem.LocalExtraColors
import com.neighborly.app.core.designsystem.MetaText
import com.neighborly.app.core.designsystem.NbCard
import com.neighborly.app.core.designsystem.PostCallbacks
import com.neighborly.app.core.designsystem.PostCard
import com.neighborly.app.core.designsystem.Refreshable
import com.neighborly.app.core.util.Format
import com.neighborly.app.data.model.Profile
import com.neighborly.app.feature.post.PostNav
import com.neighborly.app.feature.post.PostsState
import com.neighborly.app.feature.post.rememberPostOverlays

@Composable
fun HomeScreen(
    nav: PostNav,
    onCompose: (photo: Boolean, location: Boolean, ask: Boolean) -> Unit,
    onOpen: (Any) -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val posts by vm.posts.state.collectAsStateWithLifecycle()
    val scope by vm.scope.collectAsStateWithLifecycle()
    val nearby by vm.nearby.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val overlays = rememberPostOverlays(vm, nav, coroutineScope)
    val listState = rememberLazyListState()
    CollectMessages(vm.messages)
    InfiniteScroll(listState, posts, vm.posts::loadMore)
    val me = vm.me ?: return

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Refreshable(posts.refreshing, vm::refresh) {
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "nearby") { NearbyStrip(me, nearby, onCreate = { onCompose(false, false, false) }, onOpen = onOpen) }
                item(key = "composer") { ComposerCard(me, onCompose) }
                item(key = "scope") {
                    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(scope == FeedScope.NEIGHBORHOOD, { vm.setScope(FeedScope.NEIGHBORHOOD) },
                            label = { Text(me.neighborhood?.name ?: stringResource(R.string.scope_neighborhood)) })
                        FilterChip(scope == FeedScope.ALL, { vm.setScope(FeedScope.ALL) }, label = { Text(stringResource(R.string.scope_all)) })
                    }
                }
                feedItems(posts, me.id, vm.canModerate, overlays.callbacks, onRetry = { vm.posts.reload() },
                    emptyAction = { onCompose(false, false, false) })
            }
        }
        overlays.render()
    }
}

/** Post list body shared by Home, Search and Profile: skeleton, error, empty, posts and paging footer. */
fun LazyListScope.feedItems(
    posts: PostsState,
    myId: String,
    canModerate: Boolean,
    callbacks: PostCallbacks,
    onRetry: () -> Unit,
    emptyAction: (() -> Unit)? = null,
    emptyTitle: Int = R.string.feed_empty_title,
) {
    when {
        posts.loading -> item(key = "skeleton") { ListSkeleton(3) }
        posts.error != null && posts.posts.isEmpty() -> item(key = "error") { ErrorState(posts.error, onRetry) }
        posts.posts.isEmpty() -> item(key = "empty") {
            EmptyState(
                stringResource(emptyTitle),
                text = stringResource(R.string.feed_empty_text),
                actionLabel = emptyAction?.let { stringResource(R.string.nearby_create_post) },
                onAction = emptyAction,
            )
        }
        else -> {
            items(posts.posts, key = { "p-${it.id}" }) { p -> PostCard(p, myId, canModerate, callbacks) }
            item(key = "footer") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    if (posts.loadingMore) CircularProgressIndicator(Modifier.size(24.dp))
                    else if (posts.endReached) MetaText(stringResource(R.string.feed_end))
                }
            }
        }
    }
}

@Composable
fun InfiniteScroll(listState: LazyListState, posts: PostsState, loadMore: () -> Unit) {
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, posts.posts.size) { if (nearEnd) loadMore() }
}

@Composable
private fun ComposerCard(me: Profile, onCompose: (Boolean, Boolean, Boolean) -> Unit) {
    NbCard(Modifier.padding(horizontal = 0.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(me.avatarUrl, me.fullName, 42.dp)
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.padding(start = 10.dp).weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(50))
                    .clickable { onCompose(false, false, false) },
            ) {
                Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(stringResource(R.string.composer_placeholder, Format.firstName(me.fullName)),
                        color = LocalExtraColors.current.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth()) {
            ComposerAction(Icons.Outlined.AddPhotoAlternate, stringResource(R.string.composer_photo), Color(0xFF3D7457)) { onCompose(true, false, false) }
            ComposerAction(Icons.Outlined.Place, stringResource(R.string.composer_location), LocalExtraColors.current.accent) { onCompose(false, true, false) }
            ComposerAction(Icons.Outlined.QuestionAnswer, stringResource(R.string.composer_ask), Color(0xFFB7791F)) { onCompose(false, false, true) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ComposerAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.weight(1f).heightIn(min = 48.dp).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint)
        Text(label, style = MaterialTheme.typography.labelLarge, color = LocalExtraColors.current.secondaryText, modifier = Modifier.padding(start = 6.dp))
    }
}

/* ---------------- Nearby now ---------------- */

private data class KindStyle(val label: Int, val icon: ImageVector, val top: Color, val bottom: Color)

private fun styleOf(kind: NearbyKind) = when (kind) {
    NearbyKind.ALERT -> KindStyle(R.string.nearby_alert, Icons.Outlined.Warning, Color(0xFFF0624D), Color(0xFFA3201A))
    NearbyKind.EVENT -> KindStyle(R.string.nearby_event, Icons.Outlined.CalendarMonth, Color(0xFFF5A623), Color(0xFFC25E00))
    NearbyKind.LISTING -> KindStyle(R.string.nearby_for_sale, Icons.Outlined.LocalOffer, Color(0xFF4B9BFF), Color(0xFF1453B8))
    NearbyKind.LOST_FOUND -> KindStyle(R.string.nearby_lost_found, Icons.Outlined.TravelExplore, Color(0xFFA07BE8), Color(0xFF5A2FB0))
}

@Composable
private fun NearbyStrip(me: Profile, cards: List<NearbyCard>, onCreate: () -> Unit, onOpen: (Any) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(stringResource(R.string.nearby_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "create") { CreateCard(me, onCreate) }
            items(cards, key = { "${it.kind}-${it.id}" }) { c -> StoryCard(c) { onOpen(c.route) } }
        }
    }
}

private val cardModifier = Modifier.size(width = 110.dp, height = 200.dp)

@Composable
private fun CreateCard(me: Profile, onCreate: () -> Unit) {
    Box(cardModifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surface).clickable(onClick = onCreate)) {
        Column {
            Box(Modifier.fillMaxWidth().height(140.dp).background(MaterialTheme.colorScheme.primaryContainer)) {
                if (me.avatarUrl != null) AsyncImage(me.avatarUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Avatar(null, me.fullName, 64.dp, Modifier.align(Alignment.Center))
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Text(stringResource(R.string.nearby_create_post), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 10.dp))
            }
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 122.dp).size(36.dp)
                .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, null) }
        }
    }
}

@Composable
private fun StoryCard(c: NearbyCard, onClick: () -> Unit) {
    val st = styleOf(c.kind)
    Box(
        cardModifier.clip(MaterialTheme.shapes.small)
            .background(Brush.verticalGradient(listOf(st.top, st.bottom)))
            .clickable(onClick = onClick),
    ) {
        if (c.image != null) {
            AsyncImage(c.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))))
        }
        Box(
            Modifier.padding(8.dp).size(36.dp).clip(CircleShape).border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (c.personName != null) Avatar(c.personAvatar, c.personName, 30.dp)
            else Icon(st.icon, null, tint = st.bottom, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
            Text(stringResource(st.label), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(c.title, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
