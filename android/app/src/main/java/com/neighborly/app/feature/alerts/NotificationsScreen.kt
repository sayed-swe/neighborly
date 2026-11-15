package com.neighborly.app.feature.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.neighborly.app.core.designsystem.LocalExtraColors
import com.neighborly.app.core.designsystem.MetaText
import com.neighborly.app.core.designsystem.Refreshable
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.core.util.Format
import com.neighborly.app.data.model.AppNotification
import com.neighborly.app.data.repository.NotificationRepo
import com.neighborly.app.feature.common.BackTopBar
import com.neighborly.app.feature.common.MenuAction
import com.neighborly.app.navigation.webLinkToRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repo: NotificationRepo,
    private val session: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow<Load<List<AppNotification>>>(Load.Loading)
    val state = _state.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    /** Lets the root refresh the badge after a read. */
    var onChanged: () -> Unit = {}

    init { load() }

    fun load(refresh: Boolean = false) {
        if (refresh) _refreshing.value = true
        viewModelScope.launch {
            repo.list()
                .onSuccess { _state.value = Load.Data(it) }
                .onFailure { if (_state.value !is Load.Data) _state.value = Load.Error(it.message.orEmpty()) }
            _refreshing.value = false
        }
    }

    /** Realtime: prepend new notifications while the screen is visible. */
    suspend fun watch() {
        val me = session.currentUserId ?: return
        repo.inserts(me).collect { n ->
            _state.update { s ->
                val list = (s as? Load.Data)?.value.orEmpty()
                if (list.any { it.id == n.id }) s else Load.Data(listOf(n) + list)
            }
        }
    }

    private fun patch(transform: (AppNotification) -> AppNotification) =
        _state.update { s -> (s as? Load.Data)?.let { Load.Data(it.value.map(transform)) } ?: s }

    fun markRead(n: AppNotification) {
        if (n.isRead) return
        patch { if (it.id == n.id) it.copy(isRead = true) else it }
        viewModelScope.launch { repo.markRead(n.id); onChanged() }
    }

    fun markAllRead() {
        patch { it.copy(isRead = true) }
        viewModelScope.launch { repo.markAllRead(); onChanged() }
    }
}

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpen: (Any) -> Unit,
    onChanged: () -> Unit,
    vm: NotificationsViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    vm.onChanged = onChanged
    LaunchedEffect(Unit) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.watch() } }

    Scaffold(topBar = {
        BackTopBar(
            stringResource(R.string.menu_notifications), onBack,
            listOf(MenuAction(stringResource(R.string.notifications_mark_all), Icons.Outlined.DoneAll, vm::markAllRead)),
        )
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Refreshable(refreshing, { vm.load(true) }) {
                LazyColumn {
                    when (val l = s) {
                        Load.Loading -> item { ListSkeleton(5) }
                        is Load.Error -> item { ErrorState(l.message, { vm.load() }) }
                        is Load.Data -> if (l.value.isEmpty()) item {
                            EmptyState(stringResource(R.string.notifications_empty), icon = Icons.Outlined.Notifications)
                        } else items(l.value, key = { it.id }) { n ->
                            NotificationRow(n) {
                                vm.markRead(n)
                                webLinkToRoute(n.link)?.let(onOpen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: AppNotification, onClick: () -> Unit) {
    val bg = if (n.isRead) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(n.actor?.avatarUrl, n.actor?.fullName ?: stringResource(R.string.app_name), 52.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(n.message, style = MaterialTheme.typography.bodyMedium, fontWeight = if (n.isRead) FontWeight.Normal else FontWeight.SemiBold)
            MetaText(Format.timeAgo(n.createdAt))
        }
        if (!n.isRead) Box(Modifier.padding(start = 8.dp).size(10.dp).clip(CircleShape).background(LocalExtraColors.current.accent))
    }
}
