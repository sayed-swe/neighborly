package com.neighborly.app.feature.events

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.Avatar
import com.neighborly.app.core.designsystem.CollectMessages
import com.neighborly.app.core.designsystem.ConfirmDialog
import com.neighborly.app.core.designsystem.EmptyState
import com.neighborly.app.core.designsystem.ErrorState
import com.neighborly.app.core.designsystem.ListSkeleton
import com.neighborly.app.core.designsystem.Load
import com.neighborly.app.core.designsystem.LocationPill
import com.neighborly.app.core.designsystem.MetaText
import com.neighborly.app.core.designsystem.NbCard
import com.neighborly.app.core.designsystem.NbTextField
import com.neighborly.app.core.designsystem.PhotoBox
import com.neighborly.app.core.designsystem.Refreshable
import com.neighborly.app.core.designsystem.StatusPill
import com.neighborly.app.core.location.LocationProvider
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.core.util.Format
import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.CommentTarget
import com.neighborly.app.data.model.Event
import com.neighborly.app.data.model.ReportTarget
import com.neighborly.app.data.repository.ChatRepo
import com.neighborly.app.data.repository.EventRepo
import com.neighborly.app.data.repository.StorageRepo
import com.neighborly.app.feature.common.DateTimeField
import com.neighborly.app.feature.common.DetailScaffold
import com.neighborly.app.feature.common.DetailViewModel
import com.neighborly.app.feature.common.FormScaffold
import com.neighborly.app.feature.common.LocationField
import com.neighborly.app.feature.common.MenuAction
import com.neighborly.app.feature.common.PhotoField
import com.neighborly.app.feature.common.ReportSheet
import com.neighborly.app.feature.common.toIso
import com.neighborly.app.feature.common.toLocalDateTime
import com.neighborly.app.navigation.EventEditRoute
import com.neighborly.app.navigation.EventRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/* ---------------- list ---------------- */

data class EventsState(val upcoming: Boolean = true, val items: Load<List<Event>> = Load.Loading, val refreshing: Boolean = false)

@HiltViewModel
class EventsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: EventRepo,
    private val session: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(EventsState())
    val state = _state.asStateFlow()
    val myId get() = session.currentUserId

    init {
        load()
    }

    fun load(refresh: Boolean = false) {
        _state.update { if (refresh) it.copy(refreshing = true) else it.copy(items = Load.Loading) }
        viewModelScope.launch {
            val r = repo.list(_state.value.upcoming)
            _state.update { it.copy(refreshing = false, items = r.fold({ v -> Load.Data(v) }, { e -> Load.Error(e.message.orEmpty()) })) }
        }
    }

    fun setUpcoming(v: Boolean) { _state.update { it.copy(upcoming = v) }; load() }
}

@Composable
fun EventsScreen(onOpen: (Long) -> Unit, onCreate: () -> Unit, vm: EventsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column {
            PrimaryTabRow(selectedTabIndex = if (s.upcoming) 0 else 1) {
                Tab(s.upcoming, { vm.setUpcoming(true) }, text = { Text(stringResource(R.string.events_upcoming)) })
                Tab(!s.upcoming, { vm.setUpcoming(false) }, text = { Text(stringResource(R.string.events_past)) })
            }
            Refreshable(s.refreshing, { vm.load(true) }) {
                LazyColumn(contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (val items = s.items) {
                        Load.Loading -> item { ListSkeleton(3) }
                        is Load.Error -> item { ErrorState(items.message, { vm.load() }) }
                        is Load.Data -> if (items.value.isEmpty()) item {
                            EmptyState(stringResource(R.string.events_empty), icon = Icons.Outlined.CalendarMonth,
                                actionLabel = stringResource(R.string.events_create), onAction = onCreate)
                        } else items(items.value, key = { it.id }) { e -> EventRow(e, vm.myId) { onOpen(e.id) } }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onCreate, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.events_create)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

private val monthFmt = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())

@Composable
private fun DateBlock(startsAt: String) {
    val d = Format.parseInstant(startsAt)?.atZone(ZoneId.systemDefault())
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.size(56.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(d?.format(monthFmt)?.uppercase().orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            Text(d?.dayOfMonth?.toString().orEmpty(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun EventRow(e: Event, myId: String?, onClick: () -> Unit) {
    NbCard(Modifier.clickable(onClick = onClick)) {
        if (!e.imageUrl.isNullOrBlank()) PhotoBox(e.imageUrl, Modifier.fillMaxWidth().aspectRatio(2.4f))
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            DateBlock(e.startsAt)
            Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                MetaText(Format.dateTime(e.startsAt))
                MetaText(e.location, maxLines = 1)
                val mine = e.participants.firstOrNull { it.userId == myId }?.rsvp
                MetaText(
                    listOfNotNull(
                        stringResource(R.string.events_going_count, e.goingCount),
                        stringResource(R.string.events_interested_count, e.interestedCount),
                        mine?.let { stringResource(if (it == "going") R.string.events_you_going else R.string.events_you_interested) },
                    ).joinToString(" · "),
                )
            }
            if (e.status == "cancelled") StatusPill(e.status)
        }
    }
}

/* ---------------- detail ---------------- */

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: EventRepo,
    chats: ChatRepo,
    session: SessionManager,
) : DetailViewModel<Event>(chats, session) {
    val id = savedState.toRoute<EventRoute>().id
    init { load() }
    override suspend fun fetch() = repo.get(id)
    override fun ownerId(item: Event) = item.createdBy

    /** Tapping the current RSVP again removes it. */
    fun rsvp(current: String?, choice: String) = mutate { repo.setRsvp(id, if (current == choice) null else choice) }
    fun cancel() = mutate { repo.cancel(id) }
    fun delete() = remove { repo.delete(id) }
}

@Composable
fun EventDetailScreen(onBack: () -> Unit, onEdit: (Long) -> Unit, onOpenProfile: (String) -> Unit, vm: EventDetailViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    CollectMessages(vm.messages)
    LaunchedEffect(s.deleted) { if (s.deleted) onBack() }
    val e = (s.item as? Load.Data)?.value
    val actions = buildList {
        if (e != null && e.createdBy == vm.myId) add(MenuAction(stringResource(R.string.action_edit), Icons.Outlined.Edit) { onEdit(vm.id) })
        if (e != null && vm.isOwnerOrMod(e) && e.status != "cancelled") add(MenuAction(stringResource(R.string.events_cancel), Icons.Outlined.Block) { confirmCancel = true })
        if (e != null && vm.isOwnerOrMod(e)) add(MenuAction(stringResource(R.string.action_delete), Icons.Outlined.Delete) { confirmDelete = true })
        if (e != null && e.createdBy != vm.myId) add(MenuAction(stringResource(R.string.action_report), Icons.Outlined.Flag) { reporting = true })
    }
    DetailScaffold(
        title = e?.title ?: stringResource(R.string.tab_events),
        onBack = onBack,
        actions = actions,
        commentTarget = if (e != null) CommentTarget.EVENT to vm.id else null,
        onOpenProfile = onOpenProfile,
        overlay = {
            if (confirmDelete) ConfirmDialog(stringResource(R.string.confirm_delete_title), stringResource(R.string.confirm_delete_text),
                stringResource(R.string.action_delete), vm::delete, { confirmDelete = false })
            if (confirmCancel) ConfirmDialog(stringResource(R.string.events_cancel_title), stringResource(R.string.events_cancel_text),
                stringResource(R.string.events_cancel), vm::cancel, { confirmCancel = false })
            if (reporting) ReportSheet(ReportTarget.EVENT, vm.id.toString(), onDismiss = { reporting = false })
        },
    ) {
        when (val st = s.item) {
            Load.Loading -> item { ListSkeleton(1) }
            is Load.Error -> item { ErrorState(st.message, vm::load) }
            is Load.Data -> item { EventBody(st.value, vm.myId, s.busy, vm::rsvp, onOpenProfile) }
        }
    }
}

@Composable
private fun EventBody(e: Event, myId: String?, busy: Boolean, onRsvp: (String?, String) -> Unit, onOpenProfile: (String) -> Unit) {
    val mine = e.participants.firstOrNull { it.userId == myId }?.rsvp
    val full = e.maxParticipants != null && e.goingCount >= e.maxParticipants && mine != "going"
    NbCard {
        if (!e.imageUrl.isNullOrBlank()) PhotoBox(e.imageUrl, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DateBlock(e.startsAt)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.titleLarge)
                    MetaText(Format.dateTime(e.startsAt) + (e.endsAt?.let { " – " + Format.dateTime(it) } ?: ""))
                }
            }
            StatusPill(e.status)
            LocationPill(e.location)
            if (e.description.isNotBlank()) Text(e.description)
            MetaText(
                listOfNotNull(
                    stringResource(R.string.events_going_count, e.goingCount),
                    stringResource(R.string.events_interested_count, e.interestedCount),
                    e.maxParticipants?.let { stringResource(R.string.events_capacity, it) },
                ).joinToString(" · "),
            )
            if (e.status != "cancelled" && e.status != "completed") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RsvpButton(stringResource(R.string.events_going), Icons.Outlined.CheckCircle, mine == "going", !busy && !full, Modifier.weight(1f)) { onRsvp(mine, "going") }
                    RsvpButton(stringResource(R.string.events_interested), Icons.Outlined.Star, mine == "interested", !busy, Modifier.weight(1f)) { onRsvp(mine, "interested") }
                }
                if (full) MetaText(stringResource(R.string.events_full))
            }
            Row(Modifier.clickable { onOpenProfile(e.createdBy) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(e.creator?.avatarUrl, e.creator?.fullName, 36.dp)
                Column(Modifier.padding(start = 10.dp)) {
                    Text(e.creator?.fullName.orEmpty(), style = MaterialTheme.typography.titleSmall)
                    MetaText(stringResource(R.string.events_host))
                }
            }
            if (e.participants.isNotEmpty()) {
                Text(stringResource(R.string.events_people), style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(e.participants, key = { it.userId }) { p ->
                        Column(Modifier.clickable { onOpenProfile(p.userId) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Avatar(p.user?.avatarUrl, p.user?.fullName, 44.dp)
                            MetaText(Format.firstName(p.user?.fullName), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RsvpButton(label: String, icon: ImageVector, on: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (on) Button(onClick, modifier.height(48.dp), enabled = enabled) { Icon(icon, null); Text(label, Modifier.padding(start = 6.dp)) }
    else OutlinedButton(onClick, modifier.height(48.dp), enabled = enabled, colors = ButtonDefaults.outlinedButtonColors()) {
        Icon(icon, null); Text(label, Modifier.padding(start = 6.dp))
    }
}

/* ---------------- create / edit ---------------- */

data class EventForm(
    val title: String = "",
    val description: String = "",
    val startsAt: LocalDateTime? = null,
    val endsAt: LocalDateTime? = null,
    val location: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val maxParticipants: String = "",
    val newPhoto: Uri? = null,
    val imageUrl: String? = null,
    val busy: Boolean = false,
    val locating: Boolean = false,
    val error: String? = null,
    val savedId: Long? = null,
) {
    val datesValid get() = startsAt != null && (endsAt == null || !endsAt.isBefore(startsAt))
    val valid get() = title.trim().length >= 2 && location.isNotBlank() && datesValid
}

@HiltViewModel
class EventEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: EventRepo,
    private val storage: StorageRepo,
    private val locationProvider: LocationProvider,
    private val session: SessionManager,
) : ViewModel() {
    val editId = savedState.toRoute<EventEditRoute>().id
    private val _state = MutableStateFlow(EventForm())
    val state = _state.asStateFlow()

    init {
        editId?.let { id ->
            viewModelScope.launch {
                repo.get(id).onSuccess { e ->
                    if (e != null) _state.value = EventForm(
                        title = e.title, description = e.description, startsAt = e.startsAt.toLocalDateTime(),
                        endsAt = e.endsAt?.toLocalDateTime(), location = e.location, latitude = e.latitude, longitude = e.longitude,
                        maxParticipants = e.maxParticipants?.toString().orEmpty(), imageUrl = e.imageUrl,
                    )
                }
            }
        }
    }

    fun edit(t: EventForm.() -> EventForm) = _state.update { it.t().copy(error = null) }

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
            runCatching {
                val image = f.newPhoto?.let { storage.uploadImage(it, "event").getOrThrow() } ?: f.imageUrl
                val values = buildJsonObject {
                    put("title", f.title.trim())
                    put("description", f.description.trim())
                    put("starts_at", f.startsAt!!.toIso())
                    put("ends_at", f.endsAt?.toIso())
                    put("location", f.location.trim())
                    put("latitude", f.latitude)
                    put("longitude", f.longitude)
                    put("max_participants", f.maxParticipants.toIntOrNull()?.takeIf { it > 0 })
                    put("image_url", image)
                    if (editId == null) put("neighborhood_id", session.currentProfile?.neighborhoodId)
                }
                if (editId != null) { repo.update(editId, values).getOrThrow(); editId } else repo.create(values).getOrThrow()
            }.onSuccess { id -> _state.update { it.copy(busy = false, savedId = id) } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }
}

@Composable
fun EventEditScreen(onBack: () -> Unit, onSaved: (Long, Boolean) -> Unit, vm: EventEditViewModel = hiltViewModel()) {
    val f by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(f.savedId) { f.savedId?.let { onSaved(it, vm.editId == null) } }
    FormScaffold(
        title = stringResource(if (vm.editId == null) R.string.events_create else R.string.events_edit),
        onBack = onBack,
        submitLabel = stringResource(if (vm.editId == null) R.string.action_create else R.string.action_save),
        canSubmit = f.valid, busy = f.busy, error = f.error, onSubmit = vm::save,
    ) {
        PhotoField(f.newPhoto, f.imageUrl, { u -> vm.edit { copy(newPhoto = u) } }, { vm.edit { copy(newPhoto = null, imageUrl = null) } })
        NbTextField(f.title, { v -> vm.edit { copy(title = v.take(200)) } }, stringResource(R.string.field_title))
        DateTimeField(stringResource(R.string.events_starts), f.startsAt, { d -> vm.edit { copy(startsAt = d) } })
        DateTimeField(stringResource(R.string.events_ends), f.endsAt, { d -> vm.edit { copy(endsAt = d) } }, clearable = true)
        if (!f.datesValid && f.startsAt != null) Text(stringResource(R.string.events_dates_invalid), color = MaterialTheme.colorScheme.error)
        NbTextField(f.description, { v -> vm.edit { copy(description = v.take(5000)) } }, stringResource(R.string.field_description),
            singleLine = false, minLines = 4)
        LocationField(f.location, f.latitude, f.longitude, { v -> vm.edit { copy(location = v) } }, vm::useMyLocation,
            { vm.edit { copy(latitude = null, longitude = null) } }, label = stringResource(R.string.events_where), locating = f.locating)
        NbTextField(f.maxParticipants, { v -> vm.edit { copy(maxParticipants = v.filter { it.isDigit() }.take(6)) } },
            stringResource(R.string.events_max), keyboardType = KeyboardType.Number)
    }
}
