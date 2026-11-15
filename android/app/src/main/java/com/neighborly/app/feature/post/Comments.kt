package com.neighborly.app.feature.post

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.Avatar
import com.neighborly.app.core.designsystem.Load
import com.neighborly.app.core.designsystem.LocalExtraColors
import com.neighborly.app.core.designsystem.MetaText
import com.neighborly.app.core.designsystem.NbTextStyles
import com.neighborly.app.core.designsystem.UiMessage
import com.neighborly.app.core.designsystem.toUiMessage
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.core.util.Format
import com.neighborly.app.data.model.Comment
import com.neighborly.app.data.model.CommentTarget
import com.neighborly.app.data.model.UserIdOnly
import com.neighborly.app.data.repository.CommentRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CommentNode(val comment: Comment, val replies: List<Comment>)

data class CommentsUiState(
    val comments: Load<List<Comment>> = Load.Loading,
    val text: String = "",
    val replyTo: Comment? = null,
    val sending: Boolean = false,
) {
    /** One level of replies, like the web. */
    val tree: List<CommentNode>
        get() {
            val list = (comments as? Load.Data)?.value ?: return emptyList()
            return list.filter { it.parentId == null }.map { c -> CommentNode(c, list.filter { it.parentId == c.id }) }
        }
}

/** Comments / Q&A for any target (post, listing, lost & found, event, help request). */
@HiltViewModel
class CommentsViewModel @Inject constructor(
    private val repo: CommentRepo,
    private val session: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(CommentsUiState())
    val state = _state.asStateFlow()
    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** +n / -n whenever the count changes, so the parent can update its counter. */
    var onCountChange: (Int) -> Unit = {}

    private var target: Pair<CommentTarget, Long>? = null
    val myId: String? get() = session.currentUserId
    val canModerate: Boolean get() = session.currentProfile?.isModerator == true

    fun bind(t: CommentTarget, id: Long) {
        if (target == t to id) return
        target = t to id
        load()
    }

    fun load() {
        val (t, id) = target ?: return
        viewModelScope.launch {
            repo.list(t, id)
                .onSuccess { list -> _state.update { it.copy(comments = Load.Data(list)) } }
                .onFailure { e -> _state.update { it.copy(comments = Load.Error(e.message.orEmpty())) } }
        }
    }

    fun onText(v: String) = _state.update { it.copy(text = v.take(2000)) }

    fun startReply(c: Comment) {
        // Replying to a reply attaches to its top-level parent.
        val all = (_state.value.comments as? Load.Data)?.value.orEmpty()
        val parent = c.parentId?.let { pid -> all.firstOrNull { it.id == pid } } ?: c
        _state.update { it.copy(replyTo = parent, text = "@${Format.firstName(c.author?.fullName)} ") }
    }

    fun cancelReply() = _state.update { it.copy(replyTo = null, text = "") }

    fun send() {
        val (t, id) = target ?: return
        val s = _state.value
        val content = s.text.trim()
        if (content.isEmpty() || s.sending) return
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            repo.add(t, id, content, s.replyTo?.id)
                .onSuccess { c ->
                    _state.update { st ->
                        val list = (st.comments as? Load.Data)?.value.orEmpty()
                        st.copy(comments = Load.Data(list + c), text = "", replyTo = null, sending = false)
                    }
                    onCountChange(1)
                }
                .onFailure { e ->
                    _state.update { it.copy(sending = false) }
                    _messages.trySend(e.toUiMessage())
                }
        }
    }

    fun toggleLike(c: Comment) {
        val me = myId ?: return
        val liked = c.reactions.any { it.userId == me }
        fun patch(likedNow: Boolean) = _state.update { st ->
            val list = (st.comments as? Load.Data)?.value.orEmpty().map { x ->
                if (x.id != c.id) x else x.copy(
                    reactions = if (likedNow) x.reactions.filterNot { it.userId == me } + UserIdOnly(me)
                    else x.reactions.filterNot { it.userId == me },
                )
            }
            st.copy(comments = Load.Data(list))
        }
        patch(!liked)
        viewModelScope.launch {
            repo.setLiked(c.id, !liked).onFailure { e -> patch(liked); _messages.trySend(e.toUiMessage()) }
        }
    }

    fun delete(c: Comment) {
        viewModelScope.launch {
            repo.delete(c.id)
                .onSuccess {
                    var removed = 0
                    _state.update { st ->
                        val list = (st.comments as? Load.Data)?.value.orEmpty()
                        val kept = list.filterNot { it.id == c.id || it.parentId == c.id }
                        removed = list.size - kept.size
                        st.copy(comments = Load.Data(kept))
                    }
                    onCountChange(-removed)
                }
                .onFailure { e -> _messages.trySend(e.toUiMessage()) }
        }
    }
}

/** Renders the comment tree as LazyColumn items. */
fun LazyListScope.commentItems(
    state: CommentsUiState,
    myId: String?,
    canModerate: Boolean,
    onLike: (Comment) -> Unit,
    onReply: (Comment) -> Unit,
    onDelete: (Comment) -> Unit,
    onOpenProfile: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when (val c = state.comments) {
        Load.Loading -> item { CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp)) }
        is Load.Error -> item {
            TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.action_retry)) }
        }
        is Load.Data -> if (c.value.isEmpty()) {
            item { MetaText(stringResource(R.string.comments_empty), Modifier.padding(16.dp)) }
        } else items(state.tree, key = { "c-${it.comment.id}" }) { node ->
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                CommentRow(node.comment, false, myId, canModerate, onLike, onReply, onDelete, onOpenProfile)
                node.replies.forEach { r ->
                    Row { Spacer(Modifier.width(44.dp)); CommentRow(r, true, myId, canModerate, onLike, onReply, onDelete, onOpenProfile) }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    c: Comment,
    isReply: Boolean,
    myId: String?,
    canModerate: Boolean,
    onLike: (Comment) -> Unit,
    onReply: (Comment) -> Unit,
    onDelete: (Comment) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val liked = c.reactions.any { it.userId == myId }
    Row(Modifier.padding(vertical = 4.dp)) {
        Avatar(c.author?.avatarUrl, c.author?.fullName, if (isReply) 28.dp else 34.dp,
            Modifier.clickable { onOpenProfile(c.userId) })
        Column(Modifier.padding(start = 8.dp)) {
            Surface(color = LocalExtraColors.current.bubble, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(c.author?.fullName.orEmpty(), style = NbTextStyles.name.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                        modifier = Modifier.clickable { onOpenProfile(c.userId) })
                    Text(c.content, style = NbTextStyles.post)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MetaText(Format.timeAgo(c.createdAt), Modifier.padding(start = 12.dp, end = 4.dp))
                CommentTool(stringResource(R.string.post_like), liked) { onLike(c) }
                CommentTool(stringResource(R.string.comment_reply), false) { onReply(c) }
                if (c.reactions.isNotEmpty()) MetaText("👍 ${c.reactions.size}")
                if (c.userId == myId || canModerate) CommentTool(stringResource(R.string.action_delete), false) { onDelete(c) }
            }
        }
    }
}

@Composable
private fun CommentTool(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (on) MaterialTheme.colorScheme.primary else LocalExtraColors.current.secondaryText,
        modifier = Modifier.heightIn(min = 36.dp).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
    )
}

/** Sticky input bar under a comment list. */
@Composable
fun CommentInputBar(
    state: CommentsUiState,
    onText: (String) -> Unit,
    onSend: () -> Unit,
    onCancelReply: () -> Unit,
    placeholder: String,
    focusRequester: FocusRequester = FocusRequester(),
) {
    Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
            state.replyTo?.let { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetaText(stringResource(R.string.comment_replying_to, r.author?.fullName.orEmpty()), Modifier.weight(1f).padding(start = 8.dp))
                    TextButton(onClick = onCancelReply) { Text(stringResource(R.string.action_cancel)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.text,
                    onValueChange = onText,
                    placeholder = { Text(placeholder) },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                )
                IconButton(onClick = onSend, enabled = state.text.isNotBlank() && !state.sending) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.action_send),
                        tint = if (state.text.isNotBlank()) MaterialTheme.colorScheme.primary else LocalExtraColors.current.secondaryText)
                }
            }
        }
    }
}

@Composable
fun BubbleBackground(content: @Composable () -> Unit) {
    Surface(Modifier.background(MaterialTheme.colorScheme.background)) { content() }
}
