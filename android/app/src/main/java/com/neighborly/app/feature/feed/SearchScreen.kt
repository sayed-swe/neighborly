package com.neighborly.app.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.Avatar
import com.neighborly.app.core.designsystem.CollectMessages
import com.neighborly.app.core.designsystem.MetaText
import com.neighborly.app.core.designsystem.NbCard
import com.neighborly.app.core.designsystem.SectionTitle
import com.neighborly.app.core.designsystem.UiMessage
import com.neighborly.app.core.designsystem.toUiMessage
import com.neighborly.app.core.location.LocationProvider
import com.neighborly.app.core.location.rememberLocationRequest
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.core.util.Geo
import com.neighborly.app.core.util.LatLng
import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.Category
import com.neighborly.app.data.model.FeedPost
import com.neighborly.app.data.model.UserRef
import com.neighborly.app.data.repository.FeedQuery
import com.neighborly.app.data.repository.FeedRepo
import com.neighborly.app.data.repository.LookupRepo
import com.neighborly.app.data.repository.ProfileRepo
import com.neighborly.app.feature.post.PagedPosts
import com.neighborly.app.feature.post.PostListActions
import com.neighborly.app.feature.post.PostNav
import com.neighborly.app.feature.post.rememberPostOverlays
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchFilters(
    val text: String = "",
    val category: Category? = null,
    val myNeighborhood: Boolean = false,
    val near: LatLng? = null,
    val radiusKm: Int = 3,
    val locating: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val feed: FeedRepo,
    private val lookups: LookupRepo,
    private val profiles: ProfileRepo,
    private val location: LocationProvider,
    private val session: SessionManager,
) : ViewModel(), PostListActions {
    private val _filters = MutableStateFlow(SearchFilters())
    val filters = _filters.asStateFlow()
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories = _categories.asStateFlow()
    private val _people = MutableStateFlow<List<UserRef>>(emptyList())
    val people = _people.asStateFlow()
    private val _messages = Channel<UiMessage>(Channel.BUFFERED)

    val myId get() = session.currentUserId.orEmpty()
    val canModerate get() = session.currentProfile?.isModerator == true

    val posts = PagedPosts(viewModelScope, feed) { page ->
        val f = _filters.value
        feed.feed(
            page,
            FeedQuery(
                neighborhoodId = if (f.myNeighborhood) session.currentProfile?.neighborhoodId else null,
                categoryId = f.category?.id,
                search = f.text,
                near = f.near,
                radiusKm = f.radiusKm,
            ),
        )
    }
    val messages = merge(posts.messages, _messages.receiveAsFlow())

    init {
        viewModelScope.launch { _categories.value = lookups.postCategories().getOrDefault(emptyList()) }
        posts.reload()
        viewModelScope.launch {
            _filters.drop(1).debounce(350).collect { f ->
                posts.reload()
                _people.value = if (f.text.length >= 2) profiles.searchPeople(f.text).getOrDefault(emptyList()) else emptyList()
            }
        }
    }

    fun update(t: SearchFilters.() -> SearchFilters) = _filters.update { it.t() }

    fun nearMe() {
        _filters.update { it.copy(locating = true) }
        viewModelScope.launch {
            appResult { location.current() }
                .onSuccess { p -> _filters.update { it.copy(near = p, locating = false) } }
                .onFailure { e -> _filters.update { it.copy(locating = false) }; _messages.send(e.toUiMessage()) }
        }
    }

    override fun react(post: FeedPost, type: String?) = posts.react(post.id, type)
    override fun delete(postId: Long) {
        viewModelScope.launch {
            feed.delete(postId).onSuccess { posts.remove(postId) }.onFailure { _messages.send(it.toUiMessage()) }
        }
    }
    override fun onShared(postId: Long, toFeed: Boolean) { if (toFeed) posts.bumpShares(postId) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(nav: PostNav, onBack: () -> Unit, vm: SearchViewModel = hiltViewModel()) {
    val f by vm.filters.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val posts by vm.posts.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val overlays = rememberPostOverlays(vm, nav, scope)
    val listState = rememberLazyListState()
    val requestLocation = rememberLocationRequest(onGranted = vm::nearMe)
    CollectMessages(vm.messages)
    InfiniteScroll(listState, posts, vm.posts::loadMore)

    Scaffold(topBar = {
        TopAppBar(
            title = {
                OutlinedTextField(
                    value = f.text,
                    onValueChange = { v -> vm.update { copy(text = v) } },
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (f.text.isNotEmpty()) IconButton(onClick = { vm.update { copy(text = "") } }) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.action_clear))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                )
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
        )
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "filters") {
                    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { FilterChip(!f.myNeighborhood, { vm.update { copy(myNeighborhood = false) } }, label = { Text(stringResource(R.string.scope_all)) }) }
                            item { FilterChip(f.myNeighborhood, { vm.update { copy(myNeighborhood = true) } }, label = { Text(stringResource(R.string.scope_neighborhood)) }) }
                            item {
                                FilterChip(
                                    f.near != null,
                                    { if (f.near != null) vm.update { copy(near = null) } else requestLocation() },
                                    label = { Text(stringResource(R.string.search_near_me)) },
                                    leadingIcon = { Icon(Icons.Outlined.MyLocation, null) },
                                    enabled = !f.locating,
                                )
                            }
                        }
                        if (f.near != null) {
                            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(Geo.RADII_KM) { km ->
                                    FilterChip(f.radiusKm == km, { vm.update { copy(radiusKm = km) } }, label = { Text(stringResource(R.string.radius_km, km)) })
                                }
                            }
                        }
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { FilterChip(f.category == null, { vm.update { copy(category = null) } }, label = { Text(stringResource(R.string.filter_all_categories)) }) }
                            items(cats, key = { it.id }) { c ->
                                FilterChip(f.category?.id == c.id, { vm.update { copy(category = c) } }, label = { Text(listOfNotNull(c.icon, c.name).joinToString(" ")) })
                            }
                        }
                    }
                }
                if (people.isNotEmpty()) {
                    item(key = "people") {
                        NbCard {
                            SectionTitle(stringResource(R.string.search_people))
                            people.forEach { u ->
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { nav.openProfile(u.id) }.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Avatar(u.avatarUrl, u.fullName, 40.dp)
                                    Column(Modifier.padding(start = 12.dp)) {
                                        Text(u.fullName)
                                        MetaText(listOfNotNull("@${u.username}", u.neighborhood?.name).joinToString(" · "))
                                    }
                                }
                            }
                        }
                    }
                }
                feedItems(posts, vm.myId, vm.canModerate, overlays.callbacks, onRetry = { vm.posts.reload() }, emptyTitle = R.string.search_empty)
            }
            overlays.render()
        }
    }
}
