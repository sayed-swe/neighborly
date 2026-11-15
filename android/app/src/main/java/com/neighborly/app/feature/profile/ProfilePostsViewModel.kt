package com.neighborly.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neighborly.app.core.designsystem.UiMessage
import com.neighborly.app.core.designsystem.toUiMessage
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.data.model.FeedPost
import com.neighborly.app.data.repository.FeedQuery
import com.neighborly.app.data.repository.FeedRepo
import com.neighborly.app.feature.post.PagedPosts
import com.neighborly.app.feature.post.PostListActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A user's posts on their profile page. */
@HiltViewModel
class ProfilePostsViewModel @Inject constructor(
    private val feed: FeedRepo,
    private val session: SessionManager,
) : ViewModel(), PostListActions {
    private var userId: String? = null
    private val _messages = Channel<UiMessage>(Channel.BUFFERED)

    val myId get() = session.currentUserId.orEmpty()
    val canModerate get() = session.currentProfile?.isModerator == true

    val posts = PagedPosts(viewModelScope, feed) { page -> feed.feed(page, FeedQuery(userId = userId)) }
    val messages = merge(posts.messages, _messages.receiveAsFlow())

    fun bind(id: String) {
        if (userId == id) return
        userId = id
        posts.reload()
    }

    override fun react(post: FeedPost, type: String?) = posts.react(post.id, type)
    override fun delete(postId: Long) {
        viewModelScope.launch {
            feed.delete(postId).onSuccess { posts.remove(postId) }.onFailure { _messages.send(it.toUiMessage()) }
        }
    }
    override fun onShared(postId: Long, toFeed: Boolean) { if (toFeed) posts.bumpShares(postId) }
}
