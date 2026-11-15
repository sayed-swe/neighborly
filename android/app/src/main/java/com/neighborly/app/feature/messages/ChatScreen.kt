package com.neighborly.app.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.Avatar
import com.neighborly.app.core.designsystem.CollectMessages
import com.neighborly.app.core.designsystem.EmptyState
import com.neighborly.app.core.designsystem.ErrorState
import com.neighborly.app.core.designsystem.ListSkeleton
import com.neighborly.app.core.designsystem.Load
import com.neighborly.app.core.designsystem.LocalExtraColors
import com.neighborly.app.core.designsystem.MetaText
import com.neighborly.app.core.designsystem.UiMessage
import com.neighborly.app.core.designsystem.toUiMessage
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.core.util.Format
import com.neighborly.app.data.model.Message
import com.neighborly.app.data.model.UserRef
import com.neighborly.app.data.repository.ChatRepo
import com.neighborly.app.navigation.ChatRoute
import com.neighborly.app.navigation.postIdIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: Load<List<Message>> = Load.Loading,
    val other: UserRef? = null,
    val text: String = "",
    val sending: Boolean = false,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val chats: ChatRepo,
    private val session: SessionManager,
) : ViewModel() {
    private val route = savedState.toRoute<ChatRoute>()
    val conversationId = route.conversationId
    val title = route.title
    val myId get() = session.currentUserId

    private val _state = MutableStateFlow(ChatUiState())
    val state = _state.asStateFlow()
    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        load()
        viewModelScope.launch {
            chats.members(conversationId).onSuccess { m -> _state.update { it.copy(other = m.firstOrNull { x -> x.userId != myId }?.user) } }
        }
    }

    fun load() {
        viewModelScope.launch {
            chats.messages(conversationId)
                .onSuccess { list -> _state.update { it.copy(messages = Load.Data(list)) }; chats.markRead(conversationId) }
                .onFailure { e -> _state.update { it.copy(messages = Load.Error(e.message.orEmpty())) } }
        }
    }

    /** Realtime: append new rows (dedup by id), mark read while open. */
    suspend fun watch() {
        chats.newMessages(conversationId).collect { m ->
            append(m)
            if (m.senderId != myId) chats.markRead(conversationId)
        }
    }

    private fun append(m: Message) = _state.update { s ->
        val list = (s.messages as? Load.Data)?.value.orEmpty()
        if (list.any { it.id == m.id }) s else s.copy(messages = Load.Data(list + m))
    }

    fun onText(v: String) = _state.update { it.copy(text = v.take(4000)) }

    fun send() {
        val body = _state.value.text.trim()
        if (body.isEmpty() || _state.value.sending) return
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            chats.send(conversationId, body)
                .onSuccess { m -> append(m); _state.update { it.copy(text = "", sending = false) } }
                .onFailure { e -> _state.update { it.copy(sending = false) }; _messages.send(e.toUiMessage()) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onBack: () -> Unit, onOpenProfile: (String) -> Unit, onOpenPost: (Long) -> Unit, vm: ChatViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    CollectMessages(vm.messages)
    LaunchedEffect(Unit) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.watch() } }
    val list = (s.messages as? Load.Data)?.value.orEmpty()
    LaunchedEffect(list.size) { if (list.isNotEmpty()) listState.animateScrollToItem(list.size - 1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(enabled = s.other != null) { s.other?.id?.let(onOpenProfile) },
                    ) {
                        Avatar(s.other?.avatarUrl, s.other?.fullName ?: vm.title, 36.dp)
                        Text(s.other?.fullName ?: vm.title, Modifier.padding(start = 10.dp), maxLines = 1)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = s.text, onValueChange = vm::onText,
                        placeholder = { Text(stringResource(R.string.chat_placeholder)) },
                        shape = RoundedCornerShape(24.dp), maxLines = 5, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = vm::send, enabled = s.text.isNotBlank() && !s.sending) {
                        Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.cd_send),
                            tint = if (s.text.isNotBlank()) MaterialTheme.colorScheme.primary else LocalExtraColors.current.secondaryText)
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            when (val m = s.messages) {
                Load.Loading -> ListSkeleton(3)
                is Load.Error -> ErrorState(m.message, vm::load)
                is Load.Data -> if (m.value.isEmpty()) {
                    EmptyState(stringResource(R.string.chat_empty, s.other?.fullName ?: vm.title))
                } else LazyColumn(state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(m.value, key = { it.id }) { msg -> Bubble(msg, msg.senderId == vm.myId, onOpenPost) }
                }
            }
        }
    }
}

@Composable
private fun Bubble(m: Message, mine: Boolean, onOpenPost: (Long) -> Unit) {
    val postId = postIdIn(m.body)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            Surface(
                color = if (mine) MaterialTheme.colorScheme.primary else LocalExtraColors.current.bubble,
                contentColor = if (mine) Color.White else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.widthIn(max = 300.dp).clickable(enabled = postId != null) { postId?.let(onOpenPost) },
            ) {
                Text(
                    m.body,
                    textDecoration = if (postId != null) TextDecoration.Underline else null,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
            MetaText(Format.time(m.createdAt), Modifier.padding(horizontal = 6.dp))
        }
    }
}
