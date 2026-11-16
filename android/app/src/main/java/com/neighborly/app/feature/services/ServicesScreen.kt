package com.neighborly.app.feature.services

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.neighborly.app.core.designsystem.EmptyState
import com.neighborly.app.core.designsystem.ErrorState
import com.neighborly.app.core.designsystem.ListSkeleton
import com.neighborly.app.core.designsystem.Load
import com.neighborly.app.core.designsystem.MetaText
import com.neighborly.app.core.designsystem.NbCard
import com.neighborly.app.core.designsystem.Pill
import com.neighborly.app.core.designsystem.PillTone
import com.neighborly.app.core.designsystem.Refreshable
import com.neighborly.app.core.designsystem.StatusPill
import com.neighborly.app.core.util.Format
import com.neighborly.app.data.model.Category
import com.neighborly.app.data.model.HelpRequest
import com.neighborly.app.data.model.Service
import com.neighborly.app.data.repository.HelpRepo
import com.neighborly.app.data.repository.LookupRepo
import com.neighborly.app.data.repository.ServiceRepo
import com.neighborly.app.feature.common.BackTopBar
import com.neighborly.app.navigation.ServicesRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServicesState(
    val helpTab: Boolean = false,
    val search: String = "",
    val category: Category? = null,
    val categories: List<Category> = emptyList(),
    val services: Load<List<Service>> = Load.Loading,
    /** "active" | "resolved" | null (all) */
    val helpStatus: String? = "active",
    val help: Load<List<HelpRequest>> = Load.Loading,
    val refreshing: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ServicesViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val services: ServiceRepo,
    private val help: HelpRepo,
    private val lookups: LookupRepo,
) : ViewModel() {
    private val _state = MutableStateFlow(ServicesState(helpTab = savedState.toRoute<ServicesRoute>().helpTab))
    val state = _state.asStateFlow()
    private val search = MutableStateFlow("")

    init {
        viewModelScope.launch { _state.update { it.copy(categories = lookups.serviceCategories().getOrDefault(emptyList())) } }
        loadServices(); loadHelp()
        viewModelScope.launch { search.drop(1).debounce(350).collect { loadServices() } }
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        if (_state.value.helpTab) loadHelp() else loadServices()
    }

    fun loadServices() {
        val s = _state.value
        viewModelScope.launch {
            val r = services.list(s.category?.id, s.search)
            _state.update { it.copy(refreshing = false, services = r.fold({ v -> Load.Data(v) }, { e -> Load.Error(e.message.orEmpty()) })) }
        }
    }

    fun loadHelp() {
        viewModelScope.launch {
            val r = help.list(_state.value.helpStatus)
            _state.update { it.copy(refreshing = false, help = r.fold({ v -> Load.Data(v) }, { e -> Load.Error(e.message.orEmpty()) })) }
        }
    }

    fun setTab(help: Boolean) = _state.update { it.copy(helpTab = help) }
    fun setSearch(q: String) { _state.update { it.copy(search = q) }; search.value = q }
    fun setCategory(c: Category?) { _state.update { it.copy(category = c, services = Load.Loading) }; loadServices() }
    fun setHelpStatus(s: String?) { _state.update { it.copy(helpStatus = s, help = Load.Loading) }; loadHelp() }
}

@Composable
fun ServicesScreen(
    onBack: () -> Unit,
    onOpenService: (Long) -> Unit,
    onOpenHelp: (Long) -> Unit,
    onCreateService: () -> Unit,
    onAskHelp: () -> Unit,
    vm: ServicesViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.services_title), onBack) },
        floatingActionButton = {
            if (s.helpTab) ExtendedFloatingActionButton(onClick = onAskHelp, icon = { Icon(Icons.Outlined.VolunteerActivism, null) },
                text = { Text(stringResource(R.string.help_ask)) })
            else ExtendedFloatingActionButton(onClick = onCreateService, icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text(stringResource(R.string.services_offer)) })
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            PrimaryTabRow(selectedTabIndex = if (s.helpTab) 1 else 0) {
                Tab(!s.helpTab, { vm.setTab(false) }, text = { Text(stringResource(R.string.menu_services)) })
                Tab(s.helpTab, { vm.setTab(true) }, text = { Text(stringResource(R.string.menu_help)) })
            }
            Refreshable(s.refreshing, vm::refresh) {
                LazyColumn(contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.helpTab) helpList(s, vm, onOpenHelp, onAskHelp) else serviceList(s, vm, onOpenService, onCreateService)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.serviceList(
    s: ServicesState, vm: ServicesViewModel, onOpen: (Long) -> Unit, onCreate: () -> Unit,
) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(s.search, vm::setSearch, placeholder = { Text(stringResource(R.string.services_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(s.category == null, { vm.setCategory(null) }, label = { Text(stringResource(R.string.filter_all)) }) }
                items(s.categories, key = { it.id }) { c ->
                    FilterChip(s.category?.id == c.id, { vm.setCategory(c) }, label = { Text(listOfNotNull(c.icon, c.name).joinToString(" ")) })
                }
            }
        }
    }
    when (val l = s.services) {
        Load.Loading -> item { ListSkeleton(3) }
        is Load.Error -> item { ErrorState(l.message, vm::loadServices) }
        is Load.Data -> if (l.value.isEmpty()) item {
            EmptyState(stringResource(R.string.services_empty), icon = Icons.Outlined.Handyman,
                actionLabel = stringResource(R.string.services_offer), onAction = onCreate)
        } else items(l.value, key = { "s-${it.id}" }) { sv -> ServiceRow(sv) { onOpen(sv.id) } }
    }
}

@Composable
private fun ServiceRow(sv: Service, onClick: () -> Unit) {
    val context = LocalContext.current
    NbCard(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(sv.provider?.avatarUrl, sv.provider?.fullName, 44.dp)
            Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(sv.serviceName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    if (sv.isVerified) Icon(Icons.Outlined.Verified, stringResource(R.string.badge_verified), tint = MaterialTheme.colorScheme.primary)
                }
                MetaText(listOfNotNull(sv.category?.let { listOfNotNull(it.icon, it.name).joinToString(" ") }, sv.provider?.fullName).joinToString(" · "), maxLines = 1)
                sv.priceInfo?.takeIf { it.isNotBlank() }?.let { MetaText(it, maxLines = 1) }
            }
            FilledTonalIconButton(onClick = { dial(context, sv.contactPhone) }) {
                Icon(Icons.Outlined.Phone, stringResource(R.string.action_call))
            }
        }
    }
}

fun dial(context: android.content.Context, phone: String) {
    if (phone.isBlank()) return
    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}")))
}

private fun androidx.compose.foundation.lazy.LazyListScope.helpList(
    s: ServicesState, vm: ServicesViewModel, onOpen: (Long) -> Unit, onAsk: () -> Unit,
) {
    item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(s.helpStatus == "active", { vm.setHelpStatus("active") }, label = { Text(stringResource(R.string.help_active)) }) }
            item { FilterChip(s.helpStatus == "resolved", { vm.setHelpStatus("resolved") }, label = { Text(stringResource(R.string.help_resolved)) }) }
            item { FilterChip(s.helpStatus == null, { vm.setHelpStatus(null) }, label = { Text(stringResource(R.string.filter_all)) }) }
        }
    }
    when (val l = s.help) {
        Load.Loading -> item { ListSkeleton(3) }
        is Load.Error -> item { ErrorState(l.message, vm::loadHelp) }
        is Load.Data -> if (l.value.isEmpty()) item {
            EmptyState(stringResource(R.string.help_empty), icon = Icons.Outlined.VolunteerActivism,
                actionLabel = stringResource(R.string.help_ask), onAction = onAsk)
        } else items(l.value, key = { "h-${it.id}" }) { h -> HelpRow(h) { onOpen(h.id) } }
    }
}

@Composable
fun helpTypeTone(type: String) = if (type == "medical") PillTone.Red else PillTone.Blue

@Composable
private fun HelpRow(h: HelpRequest, onClick: () -> Unit) {
    NbCard(Modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(Format.labelize(h.helpType), helpTypeTone(h.helpType))
                StatusPill(h.status)
            }
            Text(h.title, style = MaterialTheme.typography.titleSmall)
            if (h.details.isNotBlank()) Text(h.details, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            MetaText(
                listOfNotNull(h.author?.fullName, h.neighborhood?.name, Format.timeAgo(h.createdAt),
                    stringResource(R.string.help_offers_count, h.responses.size)).joinToString(" · "),
            )
        }
    }
}
