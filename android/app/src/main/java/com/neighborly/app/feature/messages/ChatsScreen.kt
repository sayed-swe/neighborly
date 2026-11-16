package com.neighborly.app.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.Avatar
import com.neighborly.app.core.designsystem.EmptyState
import com.neighborly.app.core.designsystem.ErrorState
import com.neighborly.app.core.designsystem.ListSkeleton
import com.neighborly.app.core.designsystem.Load
import com.neighborly.app.core.designsystem.MetaText
import com.neighborly.app.core.designsystem.NbCard
import com.neighborly.app.core.designsystem.Refreshable
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.core.util.Format
import com.neighborly.app.data.model.ConversationSummary
import com.neighborly.app.data.repository.ChatRepo
import com.neighborly.app.feature.common.BackTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val chats: ChatRepo,
    private val session: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow<Load<List<ConversationSummary>>>(Load.Loading)
    val state = _state.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()
    val myId get() = session.currentUserId

    fun load(refresh: Boolean = false) {
        if (refresh) _refreshing.value = true
        viewModelScope.launch {
            chats.conversations()
                .onSuccess { _state.value = Load.Data(it) }
                .onFailure { if (_state.value !is Load.Data) _state.value = Load.Error(it.message.orEmpty()) }
            _refreshing.value = false
        }
    }

    /** Keeps the inbox live while it's on screen. */
    suspend fun watch() {
        val me = myId ?: return
        chats.anyNewMessage(me, onSubscribed = { load() }).collect { load() }
    }
}

@Composable
fun ChatsScreen(onBack: () -> Unit, onOpen: (Long, String) -> Unit, vm: ChatsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.load()
            vm.watch()
        }
    }
    Scaffold(topBar = { BackTopBar(stringResource(R.string.menu_messages), onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Refreshable(refreshing, { vm.load(true) }) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    when (val l = s) {
                        Load.Loading -> item { ListSkeleton(4) }
                        is Load.Error -> item { ErrorState(l.message, { vm.load() }) }
                        is Load.Data -> if (l.value.isEmpty()) item {
                            EmptyState(stringResource(R.string.chats_empty), text = stringResource(R.string.chats_empty_text),
                                icon = Icons.AutoMirrored.Outlined.Chat)
                        } else items(l.value, key = { it.conversationId }) { c -> ChatRow(c, vm.myId) { onOpen(c.conversationId, c.otherName) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatRow(c: ConversationSummary, myId: String?, onClick: () -> Unit) {
    val unread = c.unreadCount > 0
    NbCard(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.otherAvatar, c.otherName, 52.dp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(c.otherName, style = MaterialTheme.typography.titleSmall, fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold)
                val prefix = if (c.lastSenderId == myId && c.lastMessage != null) stringResource(R.string.chats_you_prefix) else ""
                Text(
                    prefix + (c.lastMessage ?: stringResource(R.string.chats_no_messages)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                MetaText(Format.timeAgo(c.lastMessageAt))
                if (unread) Badge(Modifier.padding(top = 4.dp)) { Text(c.unreadCount.toString()) }
            }
        }
    }
}
