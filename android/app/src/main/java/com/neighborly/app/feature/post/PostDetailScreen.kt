package com.neighborly.app.feature.post

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.CollectMessages
import com.neighborly.app.core.designsystem.Load
import com.neighborly.app.core.designsystem.LoadContent
import com.neighborly.app.core.designsystem.PostCard
import com.neighborly.app.core.designsystem.SectionTitle
import com.neighborly.app.core.designsystem.UiMessage
import com.neighborly.app.core.designsystem.toUiMessage
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.data.model.CommentTarget
import com.neighborly.app.data.model.FeedPost
import com.neighborly.app.data.repository.FeedRepo
import com.neighborly.app.navigation.PostRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PostDetailUiState(val post: Load<FeedPost> = Load.Loading, val deleted: Boolean = false)

@HiltViewModel
class PostDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val feed: FeedRepo,
    private val session: SessionManager,
) : ViewModel(), PostListActions {
    val postId = savedState.toRoute<PostRoute>().postId
    private val _state = MutableStateFlow(PostDetailUiState())
    val state = _state.asStateFlow()
    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
    val myId get() = session.currentUserId
    val canModerate get() = session.currentProfile?.isModerator == true

    init { load() }

    fun load() {
        viewModelScope.launch {
            feed.post(postId)
                .onSuccess { p -> _state.update { it.copy(post = if (p == null) Load.Error("This post is no longer available") else Load.Data(p)) } }
                .onFailure { e -> _state.update { it.copy(post = Load.Error(e.message.orEmpty())) } }
        }
    }

    private fun current() = (_state.value.post as? Load.Data)?.value

    private fun set(p: FeedPost) = _state.update { it.copy(post = Load.Data(p)) }

    fun bumpComments(delta: Int) {
        current()?.let { set(it.copy(commentCount = (it.commentCount + delta).coerceAtLeast(0))) }
    }

    override fun react(post: FeedPost, type: String?) {
        val before = current() ?: return
        set(before.withReaction(type))
        viewModelScope.launch {
            feed.setReaction(post.id, type).onFailure { e -> set(before); _messages.trySend(e.toUiMessage()) }
        }
    }

    override fun delete(postId: Long) {
        viewModelScope.launch {
            feed.delete(postId)
                .onSuccess { _state.update { it.copy(deleted = true) } }
                .onFailure { e -> _messages.trySend(e.toUiMessage()) }
        }
    }

    override fun onShared(postId: Long, toFeed: Boolean) {
        if (toFeed && postId == this.postId) current()?.let { set(it.copy(shareCount = it.shareCount + 1)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    nav: PostNav,
    onBack: () -> Unit,
    vm: PostDetailViewModel = hiltViewModel(),
    comments: CommentsViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val cs by comments.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val overlays = rememberPostOverlays(vm, nav, scope)
    remember(vm.postId) {
        comments.onCountChange = vm::bumpComments
        comments.bind(CommentTarget.POST, vm.postId)
    }
    CollectMessages(vm.messages)
    CollectMessages(comments.messages)
    LaunchedEffect(s.deleted) { if (s.deleted) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((s.post as? Load.Data)?.value?.authorName?.let { stringResource(R.string.post_by, it) } ?: stringResource(R.string.post_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
        bottomBar = {
            if (s.post is Load.Data) {
                CommentInputBar(cs, comments::onText, comments::send, comments::cancelReply, stringResource(R.string.comment_placeholder))
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LoadContent(s.post, onRetry = vm::load) { post ->
                Column {
                    LazyColumn {
                        item { PostCard(post, vm.myId, vm.canModerate, overlays.callbacks, expanded = true) }
                        item { SectionTitle(stringResource(R.string.comments_title)) }
                        commentItems(
                            cs, comments.myId, comments.canModerate,
                            onLike = comments::toggleLike, onReply = comments::startReply, onDelete = comments::delete,
                            onOpenProfile = nav.openProfile, onRetry = comments::load,
                        )
                    }
                }
            }
            overlays.render()
        }
    }
}
