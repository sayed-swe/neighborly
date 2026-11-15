package com.neighborly.app.feature.moderation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.neighborly.app.core.designsystem.EmptyState
import com.neighborly.app.core.designsystem.ErrorState
import com.neighborly.app.core.designsystem.ListSkeleton
import com.neighborly.app.core.designsystem.Load
import com.neighborly.app.core.designsystem.MetaText
import com.neighborly.app.core.designsystem.NbCard
import com.neighborly.app.core.designsystem.NbTextField
import com.neighborly.app.core.designsystem.Pill
import com.neighborly.app.core.designsystem.PillTone
import com.neighborly.app.core.designsystem.PhotoBox
import com.neighborly.app.core.designsystem.Refreshable
import com.neighborly.app.core.designsystem.SectionTitle
import com.neighborly.app.core.designsystem.StatusPill
import com.neighborly.app.core.designsystem.UiMessage
import com.neighborly.app.core.designsystem.toUiMessage
import com.neighborly.app.core.util.Format
import com.neighborly.app.data.model.AdminStats
import com.neighborly.app.data.model.Report
import com.neighborly.app.data.model.ReviewTarget
import com.neighborly.app.data.repository.ModAction
import com.neighborly.app.data.repository.ModTarget
import com.neighborly.app.data.repository.ModerationRepo
import com.neighborly.app.data.repository.field
import com.neighborly.app.data.repository.label
import com.neighborly.app.feature.common.BackTopBar
import com.neighborly.app.navigation.EventRoute
import com.neighborly.app.navigation.HelpRoute
import com.neighborly.app.navigation.ListingRoute
import com.neighborly.app.navigation.LostFoundRoute
import com.neighborly.app.navigation.PostRoute
import com.neighborly.app.navigation.ReportReviewRoute
import com.neighborly.app.navigation.ServiceRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/* ---------------- queue + dashboard ---------------- */

data class ModerationState(
    val open: Boolean = true,
    val reports: Load<List<Report>> = Load.Loading,
    val stats: Load<AdminStats> = Load.Loading,
    val refreshing: Boolean = false,
)

@HiltViewModel
class ModerationViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: ModerationRepo,
) : ViewModel() {
    private val _state = MutableStateFlow(ModerationState())
    val state = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            savedState.getStateFlow("refresh", false).collect { if (it) { savedState["refresh"] = false; load(true) } }
        }
    }

    fun load(refresh: Boolean = false) {
        _state.update { if (refresh) it.copy(refreshing = true) else it.copy(reports = Load.Loading) }
        viewModelScope.launch {
            val r = async { repo.reports(_state.value.open) }
            val st = async { repo.stats() }
            _state.update {
                it.copy(
                    refreshing = false,
                    reports = r.await().fold({ v -> Load.Data(v) }, { e -> Load.Error(e.message.orEmpty()) }),
                    stats = st.await().fold({ v -> Load.Data(v) }, { e -> Load.Error(e.message.orEmpty()) }),
                )
            }
        }
    }

    fun setOpen(open: Boolean) { _state.update { it.copy(open = open) }; load() }
}

@Composable
fun ModerationScreen(onBack: () -> Unit, onReview: (Long) -> Unit, vm: ModerationViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(topBar = { BackTopBar(stringResource(R.string.menu_moderation), onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.mod_reports)) })
                Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.mod_dashboard)) })
            }
            Refreshable(s.refreshing, { vm.load(true) }) {
                if (tab == 0) ReportsQueue(s, vm::setOpen, vm::load, onReview) else Dashboard(s.stats, vm::load)
            }
        }
    }
}

@Composable
private fun ReportsQueue(s: ModerationState, setOpen: (Boolean) -> Unit, retry: () -> Unit, onReview: (Long) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilterChip(s.open, { setOpen(true) }, label = { Text(stringResource(R.string.mod_open)) })
                androidx.compose.material3.FilterChip(!s.open, { setOpen(false) }, label = { Text(stringResource(R.string.mod_closed)) })
            }
        }
        when (val l = s.reports) {
            Load.Loading -> item { ListSkeleton(3) }
            is Load.Error -> item { ErrorState(l.message, { retry() }) }
            is Load.Data -> if (l.value.isEmpty()) item {
                EmptyState(stringResource(R.string.mod_empty), text = if (s.open) stringResource(R.string.mod_empty_text) else null, icon = Icons.Outlined.Flag)
            } else items(l.value, key = { it.id }) { r -> ReportRow(r) { onReview(r.id) } }
        }
    }
}

@Composable
private fun ReportRow(r: Report, onClick: () -> Unit) {
    NbCard(Modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("#${r.id}", style = MaterialTheme.typography.labelMedium)
                Pill(Format.labelize(r.targetType))
                Pill(Format.labelize(r.reason), PillTone.Red)
                StatusPill(r.status)
            }
            r.details?.takeIf { it.isNotBlank() }?.let { Text("“$it”", maxLines = 2) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(r.reporter?.avatarUrl, r.reporter?.fullName, 24.dp)
                MetaText(
                    listOfNotNull(r.reporter?.fullName, Format.timeAgo(r.createdAt),
                        r.actionTaken.takeIf { it != "none" }?.let(Format::labelize),
                        r.reviewer?.fullName?.let { "by $it" }).joinToString(" · "),
                    Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Dashboard(stats: Load<AdminStats>, retry: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (stats) {
            Load.Loading -> item { ListSkeleton(2) }
            is Load.Error -> item { ErrorState(stats.message, retry) }
            is Load.Data -> {
                val a = stats.value
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) {
                        listOf(
                            R.string.stat_users to a.users, R.string.stat_active_users to a.activeUsers,
                            R.string.stat_restricted to a.bannedUsers, R.string.stat_posts to a.posts,
                            R.string.stat_hidden_posts to a.hiddenPosts, R.string.stat_comments to a.comments,
                            R.string.stat_reactions to a.reactions, R.string.stat_shares to a.shares,
                            R.string.stat_listings to a.listings, R.string.stat_lost_found to a.lostFound,
                            R.string.stat_events to a.events, R.string.stat_services to a.services,
                            R.string.stat_help to a.helpRequests, R.string.stat_alerts to a.alertsActive,
                            R.string.stat_messages to a.messages, R.string.stat_reports to a.reportsPending,
                        ).forEach { (label, value) -> StatCard(stringResource(label), value, Modifier.weight(1f)) }
                    }
                }
                item { BarChart(stringResource(R.string.stat_posts_by_day), a.postsByDay.map { it.day.takeLast(5) to (it.posts ?: 0) }) }
                item { BarChart(stringResource(R.string.stat_signups_by_day), a.signupsByDay.map { it.day.takeLast(5) to (it.users ?: 0) }) }
                item {
                    NbCard {
                        SectionTitle(stringResource(R.string.stat_by_neighborhood))
                        a.usersByNeighborhood.forEach { n ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Text(n.name, Modifier.weight(1f)); Text(n.users.toString(), fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Box(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: Int, modifier: Modifier) {
    NbCard(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(Format.compact(value), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            MetaText(label)
        }
    }
}

/** Simple bar chart (last 14 days) drawn with plain boxes. */
@Composable
private fun BarChart(title: String, data: List<Pair<String, Int>>) {
    val max = (data.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    NbCard {
        SectionTitle(title)
        Row(
            Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            data.forEach { (_, v) ->
                Box(
                    Modifier.weight(1f).fillMaxHeight(v.toFloat() / max).heightIn(min = 2.dp)
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            MetaText(data.firstOrNull()?.first.orEmpty(), Modifier.weight(1f))
            MetaText(data.lastOrNull()?.first.orEmpty())
        }
    }
}

/* ---------------- review one report ---------------- */

data class ReviewState(
    val report: Load<Report> = Load.Loading,
    val target: ReviewTarget? = null,
    val targetLoading: Boolean = true,
    val note: String = "",
    val busy: Boolean = false,
    val done: Boolean = false,
)

@HiltViewModel
class ReportReviewViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: ModerationRepo,
) : ViewModel() {
    private val id = savedState.toRoute<ReportReviewRoute>().reportId
    private val _state = MutableStateFlow(ReviewState())
    val state = _state.asStateFlow()
    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            repo.report(id).onSuccess { r ->
                if (r == null) { _state.update { it.copy(report = Load.Error("Report not found")) }; return@onSuccess }
                _state.update { it.copy(report = Load.Data(r), note = r.adminNote.orEmpty()) }
                val t = repo.loadTarget(r).getOrNull()
                _state.update { it.copy(target = t, targetLoading = false) }
            }.onFailure { e -> _state.update { it.copy(report = Load.Error(e.message.orEmpty())) } }
        }
    }

    fun onNote(v: String) = _state.update { it.copy(note = v) }

    fun close(action: ModAction) {
        val s = _state.value
        val r = (s.report as? Load.Data)?.value ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            repo.close(r, s.target ?: ReviewTarget(null, null), action, s.note)
                .onSuccess { _messages.send(UiMessage.Res(R.string.mod_closed_done)); _state.update { it.copy(busy = false, done = true) } }
                .onFailure { e -> _messages.send(e.toUiMessage()); _state.update { it.copy(busy = false) } }
        }
    }
}

private fun contentRoute(type: String, id: String): Any? {
    val n = id.toLongOrNull() ?: return null
    return when (ModTarget.of(type)) {
        ModTarget.POST -> PostRoute(n)
        ModTarget.LISTING -> ListingRoute(n)
        ModTarget.LOST_FOUND -> LostFoundRoute(n)
        ModTarget.EVENT -> EventRoute(n)
        ModTarget.HELP -> HelpRoute(n)
        ModTarget.SERVICE -> ServiceRoute(n)
        else -> null
    }
}

@Composable
fun ReportReviewScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpen: (Any) -> Unit,
    onOpenProfile: (String) -> Unit,
    vm: ReportReviewViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var confirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<ModAction?>(null) }
    CollectMessages(vm.messages)
    LaunchedEffect(s.done) { if (s.done) onDone() }
    val r = (s.report as? Load.Data)?.value
    confirm?.let { action ->
        com.neighborly.app.core.designsystem.ConfirmDialog(
            title = stringResource(R.string.mod_confirm_title),
            text = stringResource(
                when (action) {
                    ModAction.BAN -> R.string.mod_confirm_ban
                    ModAction.SUSPEND -> R.string.mod_confirm_suspend
                    else -> R.string.mod_confirm_remove
                },
            ),
            confirmLabel = stringResource(R.string.action_done),
            onConfirm = { vm.close(action) },
            onDismiss = { confirm = null },
        )
    }
    Scaffold(topBar = { BackTopBar(r?.let { stringResource(R.string.mod_report_n, it.id) } ?: stringResource(R.string.mod_reports), onBack) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (val rl = s.report) {
                Load.Loading -> item { ListSkeleton(2) }
                is Load.Error -> item { ErrorState(rl.message, vm::load) }
                is Load.Data -> {
                    val rep = rl.value
                    val t = ModTarget.of(rep.targetType)
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill(Format.labelize(rep.targetType)); Pill(Format.labelize(rep.reason), PillTone.Red); StatusPill(rep.status)
                        }
                    }
                    rep.details?.takeIf { it.isNotBlank() }?.let { d -> item { Text("“$d” — ${rep.reporter?.fullName.orEmpty()}") } }
                    item {
                        NbCard {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetaText(stringResource(R.string.mod_reported_content))
                                val target = s.target
                                when {
                                    s.targetLoading -> ListSkeleton(1)
                                    target?.row == null -> Text(stringResource(R.string.mod_gone))
                                    else -> {
                                        Text(target.label(rep.targetType).ifBlank { stringResource(R.string.mod_no_text) })
                                        target.field("image_url")?.let { PhotoBox(it, Modifier.fillMaxWidth().height(200.dp)) }
                                        target.field("status")?.let { StatusPill(it) }
                                        contentRoute(rep.targetType, rep.targetId)?.let { route ->
                                            Text(stringResource(R.string.mod_open_in_app), color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.clickable { onOpen(route) }.padding(vertical = 8.dp))
                                        }
                                    }
                                }
                                target?.owner?.let { o ->
                                    Row(Modifier.clickable { onOpenProfile(o.id) }, verticalAlignment = Alignment.CenterVertically) {
                                        Avatar(o.avatarUrl, o.fullName, 32.dp)
                                        Text(stringResource(R.string.mod_posted_by, o.fullName), Modifier.padding(horizontal = 8.dp).weight(1f, fill = false))
                                        StatusPill(o.status)
                                    }
                                }
                            }
                        }
                    }
                    item { NbTextField(s.note, vm::onNote, stringResource(R.string.mod_note), singleLine = false, minLines = 2) }
                    item {
                        val owner = s.target?.owner
                        val canHide = t.hide != null && s.target?.row != null
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionButton(stringResource(R.string.mod_dismiss), Icons.Outlined.Cancel, !s.busy, outlined = true) { vm.close(ModAction.NONE) }
                            if (owner != null) ActionButton(stringResource(R.string.mod_warn), Icons.Outlined.Flag, !s.busy, outlined = true) { vm.close(ModAction.WARN) }
                            if (canHide) ActionButton(stringResource(R.string.mod_remove), Icons.Outlined.VisibilityOff, !s.busy) { confirm = ModAction.REMOVE }
                            if (owner != null) ActionButton(stringResource(R.string.mod_suspend), Icons.Outlined.PauseCircle, !s.busy, outlined = true) { confirm = ModAction.SUSPEND }
                            if (owner != null) ActionButton(stringResource(R.string.mod_ban), Icons.Outlined.Block, !s.busy, danger = true) { confirm = ModAction.BAN }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, icon: ImageVector, enabled: Boolean, outlined: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val mod = Modifier.fillMaxWidth().height(48.dp)
    if (outlined) OutlinedButton(onClick, mod, enabled = enabled) { Icon(icon, null); Text(label, Modifier.padding(start = 8.dp)) }
    else Button(
        onClick, mod, enabled = enabled,
        colors = if (danger) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
    ) { Icon(icon, null); Text(label, Modifier.padding(start = 8.dp)) }
}
