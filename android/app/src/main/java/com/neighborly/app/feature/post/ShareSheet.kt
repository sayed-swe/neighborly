package com.neighborly.app.feature.post

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neighborly.app.BuildConfig
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.Avatar
import com.neighborly.app.core.designsystem.MetaText
import com.neighborly.app.core.designsystem.NbTextField
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.data.model.ConversationSummary
import com.neighborly.app.data.model.FeedPost
import com.neighborly.app.data.repository.ChatRepo
import com.neighborly.app.data.repository.FeedRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

fun postLink(id: Long) = "${BuildConfig.WEB_BASE_URL}/post/$id"

fun copyPostLink(context: Context, id: Long) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Neighborly post", postLink(id)))
}

data class ShareUiState(
    val chats: List<ConversationSummary>? = null,
    val busy: Boolean = false,
    val done: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val feed: FeedRepo,
    private val chats: ChatRepo,
    private val session: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(ShareUiState())
    val state = _state.asStateFlow()

    fun shareNow(postId: Long, caption: String) = run {
        feed.share(postId, caption.trim(), session.currentProfile?.neighborhoodId).map { }
    }

    fun loadChats() {
        if (_state.value.chats != null) return
        viewModelScope.launch {
            chats.conversations()
                .onSuccess { list -> _state.update { it.copy(chats = list) } }
                .onFailure { e -> _state.update { it.copy(chats = emptyList(), error = e.message) } }
        }
    }

    fun sendTo(conversationId: Long, postId: Long) = run { chats.send(conversationId, postLink(postId)).map { } }

    private fun run(block: suspend () -> Result<Unit>) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            block()
                .onSuccess { _state.update { it.copy(busy = false, done = true) } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }
}

/** "Share now" (with caption), "Send in Messages" and "Copy link". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    post: FeedPost,
    onDismiss: () -> Unit,
    onShared: (sharedToFeed: Boolean) -> Unit,
    onCopied: () -> Unit,
    vm: ShareViewModel = hiltViewModel(key = "share-${post.id}"),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var caption by rememberSaveable { mutableStateOf("") }
    var pickChat by rememberSaveable { mutableStateOf(false) }
    var sharedToFeed by rememberSaveable { mutableStateOf(false) }
    // Share the original, not a share of a share.
    val targetId = post.sharedPostId ?: post.id
    LaunchedEffect(s.done) { if (s.done) { onShared(sharedToFeed); onDismiss() } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.share_title), style = MaterialTheme.typography.titleMedium)
            if (pickChat) {
                Text(stringResource(R.string.share_pick_chat), style = MaterialTheme.typography.titleSmall)
                LaunchedEffect(Unit) { vm.loadChats() }
                when (val chats = s.chats) {
                    null -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    else -> if (chats.isEmpty()) MetaText(stringResource(R.string.share_no_chats)) else
                        LazyColumn(Modifier.heightIn(max = 360.dp)) {
                            items(chats, key = { it.conversationId }) { c ->
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = !s.busy) { vm.sendTo(c.conversationId, targetId) },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Avatar(c.otherAvatar, c.otherName, 40.dp)
                                    Text(c.otherName, Modifier.padding(start = 12.dp).weight(1f))
                                    Icon(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.action_send), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                }
            } else {
                NbTextField(caption, { caption = it.take(5000) }, stringResource(R.string.share_caption), singleLine = false, minLines = 2)
                Button(
                    onClick = { sharedToFeed = true; vm.shareNow(targetId, caption) },
                    enabled = !s.busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(Icons.Outlined.Share, null); Text(stringResource(R.string.share_now), Modifier.padding(start = 8.dp))
                }
                HorizontalDivider()
                ShareOption(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.share_send_messages)) { pickChat = true }
                ShareOption(Icons.Outlined.Link, stringResource(R.string.action_copy_link)) {
                    copyPostLink(context, targetId); onCopied(); onDismiss()
                }
            }
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ShareOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
