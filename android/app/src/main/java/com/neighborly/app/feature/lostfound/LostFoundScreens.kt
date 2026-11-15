package com.neighborly.app.feature.lostfound

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
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
import com.neighborly.app.core.designsystem.Pill
import com.neighborly.app.core.designsystem.PillTone
import com.neighborly.app.core.designsystem.PhotoBox
import com.neighborly.app.core.designsystem.Refreshable
import com.neighborly.app.core.designsystem.StatusPill
import com.neighborly.app.core.location.LocationProvider
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.core.util.Format
import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.CommentTarget
import com.neighborly.app.data.model.LostFoundItem
import com.neighborly.app.data.model.ReportTarget
import com.neighborly.app.data.repository.ChatRepo
import com.neighborly.app.data.repository.LostFoundRepo
import com.neighborly.app.data.repository.StorageRepo
import com.neighborly.app.feature.common.BackTopBar
import com.neighborly.app.feature.common.DateField
import com.neighborly.app.feature.common.DetailScaffold
import com.neighborly.app.feature.common.DetailViewModel
import com.neighborly.app.feature.common.FormScaffold
import com.neighborly.app.feature.common.LocationField
import com.neighborly.app.feature.common.MenuAction
import com.neighborly.app.feature.common.PhotoField
import com.neighborly.app.feature.common.ReportSheet
import com.neighborly.app.navigation.LostFoundEditRoute
import com.neighborly.app.navigation.LostFoundRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import javax.inject.Inject

/* ---------------- list ---------------- */

data class LostFoundListState(
    val tab: Int = 0, // 0 lost, 1 found
    val showResolved: Boolean = false,
    val items: Load<List<LostFoundItem>> = Load.Loading,
    val refreshing: Boolean = false,
)

@HiltViewModel
class LostFoundListViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: LostFoundRepo,
) : ViewModel() {
    private val _state = MutableStateFlow(LostFoundListState())
    val state = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            savedState.getStateFlow("refresh", false).collect { if (it) { savedState["refresh"] = false; load(true) } }
        }
    }

    fun load(refresh: Boolean = false) {
        val s = _state.value
        _state.update { if (refresh) it.copy(refreshing = true) else it.copy(items = Load.Loading) }
        viewModelScope.launch {
            val r = repo.list(type = if (s.tab == 0) "lost" else "found", status = if (s.showResolved) "resolved" else "open")
            _state.update { it.copy(refreshing = false, items = r.fold({ v -> Load.Data(v) }, { e -> Load.Error(e.message.orEmpty()) })) }
        }
    }

    fun setTab(t: Int) { _state.update { it.copy(tab = t) }; load() }
    fun toggleResolved() { _state.update { it.copy(showResolved = !it.showResolved) }; load() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LostFoundListScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, onCreate: () -> Unit, vm: LostFoundListViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.menu_lost_found), onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreate, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.lf_report)) })
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            PrimaryTabRow(selectedTabIndex = s.tab) {
                Tab(s.tab == 0, { vm.setTab(0) }, text = { Text(stringResource(R.string.lf_lost)) })
                Tab(s.tab == 1, { vm.setTab(1) }, text = { Text(stringResource(R.string.lf_found)) })
            }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                FilterChip(s.showResolved, vm::toggleResolved, label = { Text(stringResource(R.string.lf_show_reunited)) })
            }
            Refreshable(s.refreshing, { vm.load(true) }) {
                LazyColumn(contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (val items = s.items) {
                        Load.Loading -> item { ListSkeleton(3) }
                        is Load.Error -> item { ErrorState(items.message, { vm.load() }) }
                        is Load.Data -> if (items.value.isEmpty()) item {
                            EmptyState(stringResource(R.string.lf_empty), icon = Icons.Outlined.TravelExplore,
                                actionLabel = stringResource(R.string.lf_report), onAction = onCreate)
                        } else items(items.value, key = { it.id }) { i -> LostFoundRow(i) { onOpen(i.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun LostFoundRow(i: LostFoundItem, onClick: () -> Unit) {
    NbCard(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                if (i.imageUrl.isNullOrBlank()) Icon(Icons.Outlined.TravelExplore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                else PhotoBox(i.imageUrl, Modifier.fillMaxSize(), stringResource(R.string.cd_item_photo))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(stringResource(if (i.reportType == "lost") R.string.lf_lost else R.string.lf_found), if (i.reportType == "lost") PillTone.Red else PillTone.Green)
                    if (i.status != "open") StatusPill(i.status)
                }
                Text(i.itemName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                MetaText(i.location, maxLines = 1)
                MetaText(listOfNotNull(i.dateOccurred?.let { Format.date(it) }, Format.timeAgo(i.createdAt)).joinToString(" · "))
            }
        }
    }
}

/* ---------------- detail ---------------- */

@HiltViewModel
class LostFoundDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: LostFoundRepo,
    chats: ChatRepo,
    session: SessionManager,
) : DetailViewModel<LostFoundItem>(chats, session) {
    val id: Long = savedState.toRoute<LostFoundRoute>().itemId ?: 0L
    init { load() }
    override suspend fun fetch() = repo.get(id)
    override fun ownerId(item: LostFoundItem) = item.userId
    fun resolve() = mutate { repo.resolve(id) }
    fun delete() = remove { repo.delete(id) }
}

@Composable
fun LostFoundDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenChat: (Long, String) -> Unit,
    vm: LostFoundDetailViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    CollectMessages(vm.messages)
    LaunchedEffect(Unit) { vm.openChat.collect { (id, name) -> onOpenChat(id, name) } }
    LaunchedEffect(s.deleted) { if (s.deleted) onBack() }
    val item = (s.item as? Load.Data)?.value
    val actions = buildList {
        if (item != null && item.userId == vm.myId) add(MenuAction(stringResource(R.string.action_edit), Icons.Outlined.Edit) { onEdit(vm.id) })
        if (item != null && vm.isOwnerOrMod(item)) add(MenuAction(stringResource(R.string.action_delete), Icons.Outlined.Delete) { confirmDelete = true })
        if (item != null && item.userId != vm.myId) add(MenuAction(stringResource(R.string.action_report), Icons.Outlined.Flag) { reporting = true })
    }
    DetailScaffold(
        title = item?.itemName ?: stringResource(R.string.menu_lost_found),
        onBack = onBack,
        actions = actions,
        commentTarget = if (item != null) CommentTarget.LOST_FOUND to vm.id else null,
        commentsTitle = R.string.questions_title,
        commentPlaceholder = R.string.question_placeholder,
        onOpenProfile = onOpenProfile,
        overlay = {
            if (confirmDelete) ConfirmDialog(stringResource(R.string.confirm_delete_title), stringResource(R.string.confirm_delete_text),
                stringResource(R.string.action_delete), vm::delete, { confirmDelete = false })
            if (reporting) ReportSheet(ReportTarget.LOST_FOUND, vm.id.toString(), onDismiss = { reporting = false })
        },
    ) {
        when (val st = s.item) {
            Load.Loading -> item { ListSkeleton(1) }
            is Load.Error -> item { ErrorState(st.message, vm::load) }
            is Load.Data -> item {
                val i = st.value
                NbCard {
                    if (!i.imageUrl.isNullOrBlank()) PhotoBox(i.imageUrl, Modifier.fillMaxWidth().aspectRatio(4f / 3f), stringResource(R.string.cd_item_photo))
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill(stringResource(if (i.reportType == "lost") R.string.lf_lost else R.string.lf_found), if (i.reportType == "lost") PillTone.Red else PillTone.Green)
                            StatusPill(if (i.status == "resolved") "resolved" else "open")
                        }
                        Text(i.itemName, style = MaterialTheme.typography.titleLarge)
                        LocationPill(i.location)
                        i.dateOccurred?.let { MetaText(stringResource(R.string.lf_date, Format.date(it))) }
                        if (i.description.isNotBlank()) Text(i.description)
                        i.contactInfo?.takeIf { it.isNotBlank() }?.let { MetaText(stringResource(R.string.listing_contact, it)) }
                        Row(Modifier.clickable { onOpenProfile(i.userId) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(i.author?.avatarUrl, i.author?.fullName, 36.dp)
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(i.author?.fullName.orEmpty(), style = MaterialTheme.typography.titleSmall)
                                MetaText(Format.timeAgo(i.createdAt))
                            }
                        }
                        if (i.userId != vm.myId) {
                            Button(onClick = { vm.message(i.userId, i.author?.fullName.orEmpty()) }, enabled = !s.busy,
                                modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                Icon(Icons.AutoMirrored.Outlined.Chat, null)
                                Text(stringResource(R.string.action_message), Modifier.padding(start = 8.dp))
                            }
                        }
                        if (vm.isOwnerOrMod(i) && i.status == "open") {
                            OutlinedButton(onClick = vm::resolve, enabled = !s.busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                Icon(Icons.Outlined.CheckCircle, null)
                                Text(stringResource(R.string.lf_mark_reunited), Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- create / edit ---------------- */

data class LostFoundForm(
    val type: String = "lost",
    val itemName: String = "",
    val description: String = "",
    val location: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val date: LocalDate? = null,
    val contact: String = "",
    val newPhoto: Uri? = null,
    val imageUrl: String? = null,
    val busy: Boolean = false,
    val locating: Boolean = false,
    val error: String? = null,
    val savedId: Long? = null,
) {
    val valid get() = itemName.trim().length >= 2 && location.isNotBlank()
}

@HiltViewModel
class LostFoundEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: LostFoundRepo,
    private val storage: StorageRepo,
    private val locationProvider: LocationProvider,
    private val session: SessionManager,
) : ViewModel() {
    val editId = savedState.toRoute<LostFoundEditRoute>().id
    private val _state = MutableStateFlow(LostFoundForm())
    val state = _state.asStateFlow()

    init {
        editId?.let { id ->
            viewModelScope.launch {
                repo.get(id).onSuccess { i ->
                    if (i != null) _state.value = LostFoundForm(
                        type = i.reportType, itemName = i.itemName, description = i.description, location = i.location,
                        latitude = i.latitude, longitude = i.longitude, date = i.dateOccurred?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                        contact = i.contactInfo.orEmpty(), imageUrl = i.imageUrl,
                    )
                }
            }
        }
    }

    fun edit(t: LostFoundForm.() -> LostFoundForm) = _state.update { it.t().copy(error = null) }

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
                val image = f.newPhoto?.let { storage.uploadImage(it, "lostfound").getOrThrow() } ?: f.imageUrl
                val values = buildJsonObject {
                    put("report_type", f.type)
                    put("item_name", f.itemName.trim())
                    put("description", f.description.trim())
                    put("location", f.location.trim())
                    put("latitude", f.latitude)
                    put("longitude", f.longitude)
                    put("date_occurred", f.date?.toString())
                    put("contact_info", f.contact.trim().ifBlank { null })
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
fun LostFoundEditScreen(onBack: () -> Unit, onSaved: (Long, Boolean) -> Unit, vm: LostFoundEditViewModel = hiltViewModel()) {
    val f by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(f.savedId) { f.savedId?.let { onSaved(it, vm.editId == null) } }
    FormScaffold(
        title = stringResource(if (vm.editId == null) R.string.lf_report else R.string.lf_edit),
        onBack = onBack,
        submitLabel = stringResource(if (vm.editId == null) R.string.action_publish else R.string.action_save),
        canSubmit = f.valid, busy = f.busy, error = f.error, onSubmit = vm::save,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(f.type == "lost", { vm.edit { copy(type = "lost") } }, label = { Text(stringResource(R.string.lf_i_lost)) })
            FilterChip(f.type == "found", { vm.edit { copy(type = "found") } }, label = { Text(stringResource(R.string.lf_i_found)) })
        }
        PhotoField(f.newPhoto, f.imageUrl, { u -> vm.edit { copy(newPhoto = u) } }, { vm.edit { copy(newPhoto = null, imageUrl = null) } })
        NbTextField(f.itemName, { v -> vm.edit { copy(itemName = v.take(150)) } }, stringResource(R.string.lf_item_name))
        NbTextField(f.description, { v -> vm.edit { copy(description = v.take(3000)) } }, stringResource(R.string.field_description),
            singleLine = false, minLines = 3)
        DateField(stringResource(R.string.lf_when), f.date) { d -> vm.edit { copy(date = d) } }
        LocationField(f.location, f.latitude, f.longitude, { v -> vm.edit { copy(location = v) } }, vm::useMyLocation,
            { vm.edit { copy(latitude = null, longitude = null) } }, label = stringResource(R.string.lf_where), locating = f.locating)
        NbTextField(f.contact, { v -> vm.edit { copy(contact = v.take(120)) } }, stringResource(R.string.field_contact))
    }
}
