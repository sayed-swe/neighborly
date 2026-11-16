package com.neighborly.app.feature.post

import com.neighborly.app.core.designsystem.UiMessage
import com.neighborly.app.core.designsystem.toUiMessage
import com.neighborly.app.data.model.FeedPost
import com.neighborly.app.data.repository.FeedSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Optimistic reaction update: what the card shows right after tapping, before the server answers. */
fun FeedPost.withReaction(type: String?): FeedPost {
    val old = myReaction
    if (old == type) return this
    val count = when {
        old == null && type != null -> reactionCount + 1
        old != null && type == null -> (reactionCount - 1).coerceAtLeast(0)
        else -> reactionCount
    }
    var top = topReactions
    if (type != null && type !in top) top = (listOf(type) + top).take(3)
    if (old != null && old != type && count > 0 && reactionCount == 1) top = top - old
    if (count == 0) top = emptyList()
    return copy(myReaction = type, reactionCount = count, topReactions = top)
}

data class PostsState(
    val posts: List<FeedPost> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
)

/**
 * Paged post list shared by Home, Search and Profile: infinite paging plus optimistic reactions
 * with rollback, local delete and share-count bumps.
 */
class PagedPosts(
    private val scope: CoroutineScope,
    private val source: FeedSource,
    private val pageSize: Int = 10,
    private val loader: suspend (page: Int) -> Result<List<FeedPost>>,
) {
    private val _state = MutableStateFlow(PostsState())
    val state: StateFlow<PostsState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private var page = 0
    private var job: Job? = null

    fun reload(refresh: Boolean = false) {
        job?.cancel()
        page = 0
        _state.update {
            if (refresh) it.copy(refreshing = true, error = null)
            else PostsState(loading = true)
        }
        job = scope.launch {
            loader(0)
                .onSuccess { list ->
                    _state.value = PostsState(posts = list, loading = false, endReached = list.size < pageSize)
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, refreshing = false, error = e.message) }
                }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.endReached || s.refreshing) return
        _state.update { it.copy(loadingMore = true) }
        job = scope.launch {
            loader(page + 1)
                .onSuccess { list ->
                    page += 1
                    _state.update { st ->
                        val known = st.posts.map { it.id }.toSet()
                        st.copy(posts = st.posts + list.filter { it.id !in known }, loadingMore = false, endReached = list.size < pageSize)
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(loadingMore = false) }
                    _messages.trySend(e.toUiMessage())
                }
        }
    }

    fun react(postId: Long, type: String?) {
        val before = _state.value.posts.firstOrNull { it.id == postId } ?: return
        replace(before.withReaction(type))
        scope.launch {
            source.setReaction(postId, type).onFailure { e ->
                replace(before) // roll back
                _messages.trySend(e.toUiMessage())
            }
        }
    }

    fun replace(post: FeedPost) = _state.update { s -> s.copy(posts = s.posts.map { if (it.id == post.id) post else it }) }

    fun remove(postId: Long) = _state.update { s -> s.copy(posts = s.posts.filterNot { it.id == postId }) }

    fun bumpShares(postId: Long) = _state.update { s ->
        s.copy(posts = s.posts.map { if (it.id == postId) it.copy(shareCount = it.shareCount + 1) else it })
    }
}
