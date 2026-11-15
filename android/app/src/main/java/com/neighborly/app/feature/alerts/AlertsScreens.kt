package com.neighborly.app.feature.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.Avatar
import com.neighborly.app.core.designsystem.CollectMessages
import com.neighborly.app.core.designsystem.EmptyState
import com.neighborly.app.core.designsystem.ErrorState
import com.neighborly.app.core.designsystem.ListSkeleton
import com.neighborly.app.core.designsystem.Load
import com.neighborly.app.core.designsystem.LocationPill
import com.neighborly.app.core.designsystem.MetaText
import com.neighborly.app.core.designsystem.NbCard
import com.neighborly.app.core.designsystem.NbTextField
import com.neighborly.app.core.designsystem.Pill
import com.neighborly.app.core.designsystem.Refreshable
import com.neighborly.app.core.designsystem.StatusPill
import com.neighborly.app.core.designsystem.UiMessage
import com.neighborly.app.core.designsystem.toUiMessage
import com.neighborly.app.core.designsystem.toneFor
import com.neighborly.app.core.location.LocationProvider
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.core.util.Format
import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.EmergencyAlert
import com.neighborly.app.data.model.Enums
import com.neighborly.app.data.repository.AlertRepo
import com.neighborly.app.feature.common.FormScaffold
import com.neighborly.app.feature.common.LocationField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/* ---------------- list ---------------- */

data class AlertsState(val status: String? = "active", val items: Load<List<EmergencyAlert>> = Load.Loading, val refreshing: Boolean = false)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: AlertRepo,
    private val session: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(AlertsState())
    val state = _state.asStateFlow()
    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
    val myId get() = session.currentUserId
    val canModerate get() = session.currentProfile?.isModerator == true

    /** Lets the root refresh the Alerts badge. */
    var onChanged: () -> Unit = {}

    init {
        load()
        viewModelScope.launch {
            savedState.getStateFlow("refresh", false).collect { if (it) { savedState["refresh"] = false; load(true); onChanged() } }
        }
    }

    fun load(refresh: Boolean = false) {
        _state.update { if (refresh) it.copy(refreshing = true) else it.copy(items = Load.Loading) }
        viewModelScope.launch {
            val r = repo.list(_state.value.status)
            _state.update { it.copy(refreshing = false, items = r.fold({ v -> Load.Data(v) }, { e -> Load.Error(e.message.orEmpty()) })) }
        }
    }

    fun setStatus(s: String?) { _state.update { it.copy(status = s) }; load() }

    fun resolve(a: EmergencyAlert) {
        viewModelScope.launch {
            repo.setStatus(a.id, "resolved")
                .onSuccess { load(true); onChanged(); _messages.send(UiMessage.Res(R.string.alerts_resolved_done)) }
                .onFailure { _messages.send(it.toUiMessage()) }
        }
    }
}

@Composable
fun AlertsScreen(onCreate: () -> Unit, onOpenProfile: (String) -> Unit, onChanged: () -> Unit, vm: AlertsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    vm.onChanged = onChanged
    CollectMessages(vm.messages)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Refreshable(s.refreshing, { vm.load(true) }) {
            LazyColumn(contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(s.status == "active", { vm.setStatus("active") }, label = { Text(stringResource(R.string.help_active)) }) }
                        item { FilterChip(s.status == "resolved", { vm.setStatus("resolved") }, label = { Text(stringResource(R.string.help_resolved)) }) }
                        item { FilterChip(s.status == null, { vm.setStatus(null) }, label = { Text(stringResource(R.string.filter_all)) }) }
                    }
                }
                when (val l = s.items) {
                    Load.Loading -> item { ListSkeleton(3) }
                    is Load.Error -> item { ErrorState(l.message, { vm.load() }) }
                    is Load.Data -> if (l.value.isEmpty()) item {
                        EmptyState(stringResource(R.string.alerts_empty), text = stringResource(R.string.alerts_empty_text), icon = Icons.Outlined.CheckCircle)
                    } else items(l.value, key = { it.id }) { a ->
                        AlertCard(a, canResolve = a.status == "active" && (a.createdBy == vm.myId || vm.canModerate),
                            onResolve = { vm.resolve(a) }, onOpenProfile = onOpenProfile)
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onCreate,
            icon = { Icon(Icons.Outlined.Warning, null) },
            text = { Text(stringResource(R.string.alerts_raise)) },
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

private fun severityColor(sev: String) = when (sev) {
    "critical" -> Color(0xFF7F1D1D)
    "high" -> Color(0xFFD93025)
    "medium" -> Color(0xFFE0663A)
    else -> Color(0xFF2F6DB5)
}

@Composable
private fun AlertCard(
    a: EmergencyAlert,
    canResolve: Boolean,
    onResolve: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    NbCard {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(severityColor(a.severity)))
            Column(Modifier.padding(12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Warning, null, tint = severityColor(a.severity))
                    Pill(Format.labelize(a.severity), toneFor(a.severity))
                    if (a.status != "active") StatusPill(a.status)
                }
                Text(a.title, style = MaterialTheme.typography.titleMedium)
                if (a.message.isNotBlank()) Text(a.message)
                a.locationName?.takeIf { it.isNotBlank() }?.let { LocationPill(it) }
                Row(Modifier.clickable { onOpenProfile(a.createdBy) }, verticalAlignment = Alignment.CenterVertically) {
                    Avatar(a.author?.avatarUrl, a.author?.fullName, 28.dp, Modifier.padding(end = 8.dp))
                    MetaText(listOfNotNull(a.author?.fullName, a.neighborhood?.name, Format.timeAgo(a.createdAt)).joinToString(" · "), Modifier.weight(1f))
                }
                Row {
                    if (canResolve) TextButton(onClick = onResolve) {
                        Icon(Icons.Outlined.CheckCircle, null); Text(stringResource(R.string.alerts_resolve), Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

/* ---------------- raise an alert ---------------- */

data class AlertForm(
    val title: String = "",
    val message: String = "",
    val severity: String = "medium",
    val locationName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val busy: Boolean = false,
    val locating: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
) {
    val valid get() = title.trim().length >= 2 && message.isNotBlank()
}

@HiltViewModel
class AlertCreateViewModel @Inject constructor(
    private val repo: AlertRepo,
    private val locationProvider: LocationProvider,
    private val session: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(AlertForm())
    val state = _state.asStateFlow()

    fun edit(t: AlertForm.() -> AlertForm) = _state.update { it.t().copy(error = null) }

    fun useMyLocation() {
        _state.update { it.copy(locating = true) }
        viewModelScope.launch {
            appResult { locationProvider.current() }
                .onSuccess { p -> _state.update { it.copy(latitude = p.lat, longitude = p.lng, locating = false) } }
                .onFailure { e -> _state.update { it.copy(locating = false, error = e.message) } }
        }
    }

    fun save() {
        val f = _state.value
        if (!f.valid || f.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            repo.create(buildJsonObject {
                put("title", f.title.trim())
                put("message", f.message.trim())
                put("severity", f.severity)
                put("location_name", f.locationName.trim().ifBlank { null })
                put("latitude", f.latitude)
                put("longitude", f.longitude)
                put("neighborhood_id", session.currentProfile?.neighborhoodId)
            }).onSuccess { _state.update { it.copy(busy = false, done = true) } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }
}

@Composable
fun AlertCreateScreen(onBack: () -> Unit, onDone: () -> Unit, vm: AlertCreateViewModel = hiltViewModel()) {
    val f by vm.state.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(f.done) { if (f.done) onDone() }
    FormScaffold(
        title = stringResource(R.string.alerts_raise), onBack = onBack, submitLabel = stringResource(R.string.alerts_send),
        canSubmit = f.valid, busy = f.busy, error = f.error, onSubmit = vm::save,
    ) {
        Text(stringResource(R.string.alerts_hint), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.alerts_severity), style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Enums.SEVERITIES) { s -> FilterChip(f.severity == s, { vm.edit { copy(severity = s) } }, label = { Text(Format.labelize(s)) }) }
        }
        NbTextField(f.title, { v -> vm.edit { copy(title = v.take(200)) } }, stringResource(R.string.field_title))
        NbTextField(f.message, { v -> vm.edit { copy(message = v.take(3000)) } }, stringResource(R.string.alerts_message), singleLine = false, minLines = 4)
        LocationField(f.locationName, f.latitude, f.longitude, { v -> vm.edit { copy(locationName = v) } }, vm::useMyLocation,
            { vm.edit { copy(latitude = null, longitude = null) } }, locating = f.locating)
    }
}
