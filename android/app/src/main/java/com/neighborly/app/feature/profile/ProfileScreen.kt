package com.neighborly.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.Avatar
import com.neighborly.app.core.designsystem.Load
import com.neighborly.app.core.designsystem.LoadContent
import com.neighborly.app.core.designsystem.MetaText
import com.neighborly.app.core.designsystem.NbCard
import com.neighborly.app.core.designsystem.Pill
import com.neighborly.app.core.designsystem.PillTone
import com.neighborly.app.core.designsystem.PhotoBox
import com.neighborly.app.core.designsystem.Refreshable
import com.neighborly.app.core.designsystem.SecondaryButton
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.core.util.Format
import com.neighborly.app.data.model.Profile
import com.neighborly.app.data.model.ReportTarget
import com.neighborly.app.data.repository.ChatRepo
import com.neighborly.app.data.repository.ProfileRepo
import com.neighborly.app.feature.common.ReportSheet
import com.neighborly.app.navigation.ProfileRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val profile: Load<Profile> = Load.Loading,
    val isMe: Boolean = false,
    val refreshing: Boolean = false,
    val startingChat: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val profiles: ProfileRepo,
    private val chats: ChatRepo,
    private val session: SessionManager,
) : ViewModel() {
    private val route = savedState.toRoute<ProfileRoute>()
    private val _state = MutableStateFlow(ProfileUiState())
    val state = _state.asStateFlow()
    private val _openChat = MutableSharedFlow<Pair<Long, String>>()
    val openChat = _openChat.asSharedFlow()

    init { load() }

    fun load(refresh: Boolean = false) {
        _state.update { if (refresh) it.copy(refreshing = true) else it.copy(profile = Load.Loading) }
        viewModelScope.launch {
            val id = route.userId ?: if (route.username == null) session.currentUserId else null
            val result = if (id != null) profiles.getProfile(id) else profiles.getByUsername(route.username.orEmpty())
            result.onSuccess { p ->
                _state.update {
                    it.copy(
                        profile = if (p == null) Load.Error("Profile not found") else Load.Data(p),
                        isMe = p?.id == session.currentUserId,
                        refreshing = false,
                    )
                }
            }.onFailure { e -> _state.update { it.copy(profile = Load.Error(e.message.orEmpty()), refreshing = false) } }
        }
    }

    fun message(p: Profile) {
        _state.update { it.copy(startingChat = true) }
        viewModelScope.launch {
            chats.start(p.id)
                .onSuccess { _openChat.emit(it to p.fullName) }
                .onFailure { e -> _state.update { it.copy(message = e.message) } }
            _state.update { it.copy(startingChat = false) }
        }
    }

    val userId: String? get() = (state.value.profile as? Load.Data)?.value?.id
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenChat: (Long, String) -> Unit,
    extraContent: LazyListScope.(userId: String) -> Unit = {},
    onUserLoaded: (String) -> Unit = {},
    overlay: @Composable () -> Unit = {},
    vm: ProfileViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var reporting by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.openChat.collect { (id, name) -> onOpenChat(id, name) } }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text((s.profile as? Load.Data)?.value?.fullName ?: stringResource(R.string.profile_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
        )
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LoadContent(s.profile, onRetry = { vm.load() }) { p ->
                androidx.compose.runtime.LaunchedEffect(p.id) { onUserLoaded(p.id) }
                Refreshable(s.refreshing, { vm.load(refresh = true) }) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            ProfileHeader(
                                p, s.isMe, s.startingChat,
                                onEdit = onEdit,
                                onMessage = { vm.message(p) },
                                onReport = { reporting = true },
                            )
                        }
                        item { AboutCard(p) }
                        extraContent(p.id)
                    }
                }
                if (reporting) ReportSheet(ReportTarget.USER, p.id, onDismiss = { reporting = false })
            }
            overlay()
        }
    }
}

@Composable
private fun ProfileHeader(
    p: Profile,
    isMe: Boolean,
    startingChat: Boolean,
    onEdit: () -> Unit,
    onMessage: () -> Unit,
    onReport: () -> Unit,
) {
    NbCard {
        Box {
            Box(Modifier.fillMaxWidth().height(150.dp).background(MaterialTheme.colorScheme.primaryContainer)) {
                PhotoBox(p.coverUrl, Modifier.fillMaxSize(), stringResource(R.string.cd_cover_photo))
            }
            Avatar(
                p.avatarUrl, p.fullName, size = 96.dp,
                modifier = Modifier.padding(start = 16.dp).offset(y = 100.dp)
                    .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 52.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p.fullName, style = MaterialTheme.typography.titleLarge)
                RoleBadge(p.role)
            }
            MetaText("@${p.username}")
            if (!p.bio.isNullOrBlank()) Text(p.bio, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                if (isMe) {
                    Button(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, null); Text(stringResource(R.string.profile_edit), Modifier.padding(start = 6.dp))
                    }
                } else {
                    Button(onClick = onMessage, enabled = !startingChat) {
                        Icon(Icons.AutoMirrored.Outlined.Chat, null); Text(stringResource(R.string.action_message), Modifier.padding(start = 6.dp))
                    }
                    SecondaryButton(stringResource(R.string.action_report), onReport, icon = Icons.Outlined.Flag)
                }
            }
        }
    }
}

@Composable
fun RoleBadge(role: String?) {
    when (role) {
        "admin" -> Pill(stringResource(R.string.badge_admin), PillTone.Red)
        "moderator" -> Pill(stringResource(R.string.badge_moderator), PillTone.Blue)
        "service_provider" -> Pill(stringResource(R.string.badge_pro), PillTone.Green)
    }
}

@Composable
private fun AboutCard(p: Profile) {
    NbCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.profile_about), style = MaterialTheme.typography.titleMedium)
            p.neighborhood?.name?.let { AboutRow(Icons.Outlined.Home, stringResource(R.string.profile_lives_in, it)) }
            p.address?.takeIf { it.isNotBlank() }?.let { AboutRow(Icons.Outlined.Place, it) }
            p.phone?.takeIf { it.isNotBlank() }?.let { AboutRow(Icons.Outlined.Phone, it) }
            p.createdAt?.let { MetaText(stringResource(R.string.profile_joined, Format.date(it))) }
        }
    }
}

@Composable
private fun AboutRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
