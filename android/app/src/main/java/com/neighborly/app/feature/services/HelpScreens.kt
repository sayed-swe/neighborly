package com.neighborly.app.feature.services

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.neighborly.app.core.designsystem.CollectMessages
import com.neighborly.app.core.designsystem.ConfirmDialog
import com.neighborly.app.core.designsystem.ErrorState
import com.neighborly.app.core.designsystem.ListSkeleton
import com.neighborly.app.core.designsystem.Load
import com.neighborly.app.core.designsystem.MetaText
import com.neighborly.app.core.designsystem.NbCard
import com.neighborly.app.core.designsystem.NbTextField
import com.neighborly.app.core.designsystem.Pill
import com.neighborly.app.core.designsystem.SectionTitle
import com.neighborly.app.core.designsystem.StatusPill
import com.neighborly.app.core.designsystem.UiMessage
import com.neighborly.app.core.location.LocationProvider
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.core.util.Format
import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.CommentTarget
import com.neighborly.app.data.model.Enums
import com.neighborly.app.data.model.HelpRequest
import com.neighborly.app.data.model.ReportTarget
import com.neighborly.app.data.repository.ChatRepo
import com.neighborly.app.data.repository.HelpRepo
import com.neighborly.app.feature.common.DetailScaffold
import com.neighborly.app.feature.common.DetailViewModel
import com.neighborly.app.feature.common.FormScaffold
import com.neighborly.app.feature.common.CoordinatesField
import com.neighborly.app.feature.common.MenuAction
import com.neighborly.app.feature.common.ReportSheet
import com.neighborly.app.navigation.HelpRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/* ---------------- detail ---------------- */

@HiltViewModel
class HelpDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: HelpRepo,
    chats: ChatRepo,
    session: SessionManager,
) : DetailViewModel<HelpRequest>(chats, session) {
    val id = savedState.toRoute<HelpRoute>().id
    init { load() }
    override suspend fun fetch() = repo.get(id)
    override fun ownerId(item: HelpRequest) = item.userId

    fun setStatus(s: String) = mutate { repo.setStatus(id, s) }
    fun delete() = remove { repo.delete(id) }
    fun offer(message: String) = mutate {
        repo.respond(id, message).onSuccess { send(UiMessage.Res(R.string.help_offer_sent)) }
    }
}

@Composable
fun HelpDetailScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenChat: (Long, String) -> Unit,
    vm: HelpDetailViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var offer by rememberSaveable { mutableStateOf("") }
    CollectMessages(vm.messages)
    LaunchedEffect(Unit) { vm.openChat.collect { (id, name) -> onOpenChat(id, name) } }
    LaunchedEffect(s.deleted) { if (s.deleted) onBack() }
    val h = (s.item as? Load.Data)?.value
    val actions = buildList {
        if (h != null && vm.isOwnerOrMod(h)) add(MenuAction(stringResource(R.string.action_delete), Icons.Outlined.Delete) { confirmDelete = true })
        if (h != null && h.userId != vm.myId) add(MenuAction(stringResource(R.string.action_report), Icons.Outlined.Flag) { reporting = true })
    }
    DetailScaffold(
        title = h?.title ?: stringResource(R.string.menu_help),
        onBack = onBack,
        actions = actions,
        commentTarget = if (h != null) CommentTarget.HELP to vm.id else null,
        onOpenProfile = onOpenProfile,
        overlay = {
            if (confirmDelete) ConfirmDialog(stringResource(R.string.confirm_delete_title), stringResource(R.string.confirm_delete_text),
                stringResource(R.string.action_delete), vm::delete, { confirmDelete = false })
            if (reporting) ReportSheet(ReportTarget.HELP_REQUEST, vm.id.toString(), onDismiss = { reporting = false })
        },
    ) {
        when (val st = s.item) {
            Load.Loading -> item { ListSkeleton(1) }
            is Load.Error -> item { ErrorState(st.message, vm::load) }
            is Load.Data -> {
                val x = st.value
                item {
                    NbCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Pill(Format.labelize(x.helpType), helpTypeTone(x.helpType)); StatusPill(x.status)
                            }
                            Text(x.title, style = MaterialTheme.typography.titleLarge)
                            if (x.details.isNotBlank()) Text(x.details)
                            Row(Modifier.clickable { onOpenProfile(x.userId) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(x.author?.avatarUrl, x.author?.fullName, 36.dp)
                                Column(Modifier.padding(start = 10.dp)) {
                                    Text(x.author?.fullName.orEmpty(), style = MaterialTheme.typography.titleSmall)
                                    MetaText(listOfNotNull(x.neighborhood?.name, Format.timeAgo(x.createdAt)).joinToString(" · "))
                                }
                            }
                            if (vm.isOwnerOrMod(x)) {
                                Text(stringResource(R.string.help_update_status), style = MaterialTheme.typography.titleSmall)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(listOf("open", "in_progress", "resolved", "closed")) { st2 ->
                                        FilterChip(x.status == st2, { vm.setStatus(st2) }, label = { Text(Format.labelize(st2)) }, enabled = !s.busy)
                                    }
                                }
                            } else if (x.status == "open" || x.status == "in_progress") {
                                NbTextField(offer, { offer = it.take(2000) }, stringResource(R.string.help_offer_hint), singleLine = false, minLines = 2)
                                Button(
                                    onClick = { vm.offer(offer.trim()); offer = "" },
                                    enabled = offer.isNotBlank() && !s.busy,
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                ) {
                                    Icon(Icons.Outlined.VolunteerActivism, null)
                                    Text(stringResource(R.string.help_offer), Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                }
                item { SectionTitle(stringResource(R.string.help_offers_title, x.responses.size)) }
                items(x.responses.size) { i ->
                    val r = x.responses[i]
                    NbCard {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Avatar(r.responder?.avatarUrl, r.responder?.fullName, 36.dp, Modifier.clickable { r.responder?.id?.let(onOpenProfile) })
                            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                Text(r.responder?.fullName.orEmpty(), style = MaterialTheme.typography.titleSmall)
                                Text(r.message)
                                MetaText(Format.timeAgo(r.createdAt))
                            }
                            val rid = r.responder?.id
                            if (x.userId == vm.myId && rid != null && rid != vm.myId) {
                                IconButton(onClick = { vm.message(rid, r.responder.fullName) }) {
                                    Icon(Icons.AutoMirrored.Outlined.Chat, stringResource(R.string.action_message), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- ask for help ---------------- */

data class HelpForm(
    val type: String = "other",
    val title: String = "",
    val details: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val busy: Boolean = false,
    val locating: Boolean = false,
    val error: String? = null,
    val savedId: Long? = null,
) {
    val valid get() = title.trim().length >= 2
}

@HiltViewModel
class HelpEditViewModel @Inject constructor(
    private val repo: HelpRepo,
    private val locationProvider: LocationProvider,
    private val session: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(HelpForm())
    val state = _state.asStateFlow()

    fun edit(t: HelpForm.() -> HelpForm) = _state.update { it.t().copy(error = null) }

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
                put("help_type", f.type)
                put("title", f.title.trim())
                put("details", f.details.trim())
                put("latitude", f.latitude)
                put("longitude", f.longitude)
                put("neighborhood_id", session.currentProfile?.neighborhoodId)
            }).onSuccess { id -> _state.update { it.copy(busy = false, savedId = id) } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }
}

@Composable
fun HelpEditScreen(onBack: () -> Unit, onSaved: (Long) -> Unit, vm: HelpEditViewModel = hiltViewModel()) {
    val f by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(f.savedId) { f.savedId?.let(onSaved) }
    FormScaffold(
        title = stringResource(R.string.help_ask), onBack = onBack, submitLabel = stringResource(R.string.action_post),
        canSubmit = f.valid, busy = f.busy, error = f.error, onSubmit = vm::save,
    ) {
        Text(stringResource(R.string.help_type), style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Enums.HELP_TYPES) { t -> FilterChip(f.type == t, { vm.edit { copy(type = t) } }, label = { Text(Format.labelize(t)) }) }
        }
        NbTextField(f.title, { v -> vm.edit { copy(title = v.take(200)) } }, stringResource(R.string.help_what))
        NbTextField(f.details, { v -> vm.edit { copy(details = v.take(5000)) } }, stringResource(R.string.field_description),
            singleLine = false, minLines = 4)
        CoordinatesField(f.latitude, f.longitude, vm::useMyLocation, { vm.edit { copy(latitude = null, longitude = null) } }, f.locating)
    }
}
