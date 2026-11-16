package com.neighborly.app.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neighborly.app.core.FeedScope
import com.neighborly.app.core.Prefs
import com.neighborly.app.core.designsystem.UiMessage
import com.neighborly.app.core.designsystem.toUiMessage
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.core.util.Format
import com.neighborly.app.data.model.FeedPost
import com.neighborly.app.data.repository.AlertRepo
import com.neighborly.app.data.repository.EventRepo
import com.neighborly.app.data.repository.FeedQuery
import com.neighborly.app.data.repository.FeedRepo
import com.neighborly.app.data.repository.ListingRepo
import com.neighborly.app.data.repository.LostFoundRepo
import com.neighborly.app.feature.post.PagedPosts
import com.neighborly.app.feature.post.PostListActions
import com.neighborly.app.navigation.AlertsRoute
import com.neighborly.app.navigation.EventRoute
import com.neighborly.app.navigation.ListingRoute
import com.neighborly.app.navigation.LostFoundRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NearbyKind { ALERT, EVENT, LISTING, LOST_FOUND }

data class NearbyCard(
    val kind: NearbyKind,
    val id: Long,
    val title: String,
    val image: String?,
    val personName: String?,
    val personAvatar: String?,
    val route: Any,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val feed: FeedRepo,
    private val alerts: AlertRepo,
    private val events: EventRepo,
    private val listings: ListingRepo,
    private val lostFound: LostFoundRepo,
    private val prefs: Prefs,
    private val session: SessionManager,
) : ViewModel(), PostListActions {

    private val _scope = MutableStateFlow(FeedScope.NEIGHBORHOOD)
    val scope = _scope.asStateFlow()

    private val _nearby = MutableStateFlow<List<NearbyCard>>(emptyList())
    val nearby = _nearby.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)

    val me get() = session.currentProfile
    val canModerate get() = me?.isModerator == true

    val posts = PagedPosts(viewModelScope, feed, FeedRepo.PAGE_SIZE.toInt()) { page ->
        val hood = if (_scope.value == FeedScope.NEIGHBORHOOD) me?.neighborhoodId else null
        feed.feed(page, FeedQuery(neighborhoodId = hood))
    }
    val messages = merge(posts.messages, _messages.receiveAsFlow())

    init {
        viewModelScope.launch {
            _scope.value = prefs.feedScope.first()
            posts.reload()
        }
        loadNearby()
    }

    fun setScope(scope: FeedScope) {
        if (scope == _scope.value) return
        _scope.value = scope
        viewModelScope.launch { prefs.setFeedScope(scope) }
        posts.reload()
    }

    fun refresh() {
        posts.reload(refresh = true)
        loadNearby()
    }

    /** Same mix as NearbyStrip.jsx: 2 alerts, 2 events, 3 listings, 2 lost & found. */
    private fun loadNearby() {
        viewModelScope.launch {
            val a = async { alerts.list("active").getOrDefault(emptyList()) }
            val e = async { events.list(upcoming = true).getOrDefault(emptyList()) }
            val l = async { listings.list().getOrDefault(emptyList()) }
            val lf = async { lostFound.list().getOrDefault(emptyList()) }
            _nearby.value = buildList {
                a.await().take(2).forEach { add(NearbyCard(NearbyKind.ALERT, it.id, it.title, null, it.author?.fullName, it.author?.avatarUrl, AlertsRoute)) }
                e.await().take(2).forEach { add(NearbyCard(NearbyKind.EVENT, it.id, it.title, it.imageUrl, it.creator?.fullName, it.creator?.avatarUrl, EventRoute(it.id))) }
                l.await().take(3).forEach {
                    add(NearbyCard(NearbyKind.LISTING, it.id, "${Format.price(it.price, it.listingType)} · ${it.title}", it.imageUrl,
                        it.author?.fullName, it.author?.avatarUrl, ListingRoute(it.id)))
                }
                lf.await().take(2).forEach {
                    val prefix = if (it.reportType == "lost") "Lost" else "Found"
                    add(NearbyCard(NearbyKind.LOST_FOUND, it.id, "$prefix: ${it.itemName}", it.imageUrl,
                        it.author?.fullName, it.author?.avatarUrl, LostFoundRoute(it.id)))
                }
            }
        }
    }

    override fun react(post: FeedPost, type: String?) = posts.react(post.id, type)

    override fun delete(postId: Long) {
        viewModelScope.launch {
            feed.delete(postId)
                .onSuccess { posts.remove(postId); _messages.send(UiMessage.Res(com.neighborly.app.R.string.deleted)) }
                .onFailure { _messages.send(it.toUiMessage()) }
        }
    }

    override fun onShared(postId: Long, toFeed: Boolean) {
        if (toFeed) {
            posts.bumpShares(postId)
            posts.reload(refresh = true)
        }
    }
}
