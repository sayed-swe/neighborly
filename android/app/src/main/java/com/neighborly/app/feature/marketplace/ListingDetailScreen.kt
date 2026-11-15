package com.neighborly.app.feature.marketplace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.toRoute
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.Avatar
import com.neighborly.app.core.designsystem.CollectMessages
import com.neighborly.app.core.designsystem.ConfirmDialog
import com.neighborly.app.core.designsystem.ErrorState
import com.neighborly.app.core.designsystem.ListSkeleton
import com.neighborly.app.core.designsystem.Load
import com.neighborly.app.core.designsystem.LocationPill
import com.neighborly.app.core.designsystem.MetaText
import com.neighborly.app.core.designsystem.NbCard
import com.neighborly.app.core.designsystem.Pill
import com.neighborly.app.core.designsystem.PhotoBox
import com.neighborly.app.core.designsystem.StatusPill
import com.neighborly.app.core.util.Format
import com.neighborly.app.data.model.CommentTarget
import com.neighborly.app.data.model.Enums
import com.neighborly.app.data.model.Listing
import com.neighborly.app.data.model.ReportTarget
import com.neighborly.app.data.repository.ChatRepo
import com.neighborly.app.data.repository.ListingRepo
import com.neighborly.app.feature.common.DetailScaffold
import com.neighborly.app.feature.common.DetailViewModel
import com.neighborly.app.feature.common.MenuAction
import com.neighborly.app.feature.common.ReportSheet
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.navigation.ListingRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ListingDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: ListingRepo,
    chats: ChatRepo,
    session: SessionManager,
) : DetailViewModel<Listing>(chats, session) {
    val id = savedState.toRoute<ListingRoute>().id

    init { load() }

    override suspend fun fetch() = repo.get(id)
    override fun ownerId(item: Listing) = item.userId

    fun setStatus(status: String) = mutate { repo.setStatus(id, status) }
    fun delete() = remove { repo.delete(id) }
}

@Composable
fun ListingDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenChat: (Long, String) -> Unit,
    vm: ListingDetailViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    CollectMessages(vm.messages)
    LaunchedEffect(Unit) { vm.openChat.collect { (id, name) -> onOpenChat(id, name) } }
    LaunchedEffect(s.deleted) { if (s.deleted) onBack() }
    val item = (s.item as? Load.Data)?.value
    val mine = item != null && vm.isOwnerOrMod(item)

    val actions = buildList {
        if (item != null && item.userId == vm.myId) add(MenuAction(stringResource(R.string.action_edit), Icons.Outlined.Edit) { onEdit(vm.id) })
        if (mine) add(MenuAction(stringResource(R.string.action_delete), Icons.Outlined.Delete) { confirmDelete = true })
        if (item != null && item.userId != vm.myId) add(MenuAction(stringResource(R.string.action_report), Icons.Outlined.Flag) { reporting = true })
    }

    DetailScaffold(
        title = item?.title ?: stringResource(R.string.tab_market),
        onBack = onBack,
        actions = actions,
        commentTarget = if (item != null) CommentTarget.LISTING to vm.id else null,
        commentsTitle = R.string.questions_title,
        commentPlaceholder = R.string.question_placeholder,
        onOpenProfile = onOpenProfile,
        overlay = {
            if (confirmDelete) ConfirmDialog(stringResource(R.string.confirm_delete_title), stringResource(R.string.confirm_delete_text),
                stringResource(R.string.action_delete), vm::delete, { confirmDelete = false })
            if (reporting) ReportSheet(ReportTarget.LISTING, vm.id.toString(), onDismiss = { reporting = false })
        },
    ) {
        when (val st = s.item) {
            Load.Loading -> item { ListSkeleton(1) }
            is Load.Error -> item { ErrorState(st.message, vm::load) }
            is Load.Data -> item { ListingBody(st.value, vm, mine, onOpenProfile) }
        }
    }
}

@Composable
private fun ListingBody(l: Listing, vm: ListingDetailViewModel, canManage: Boolean, onOpenProfile: (String) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    NbCard {
        if (!l.imageUrl.isNullOrBlank()) PhotoBox(l.imageUrl, Modifier.fillMaxWidth().aspectRatio(1f), stringResource(R.string.cd_item_photo))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(Format.price(l.price, l.listingType), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(l.title, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(listingTypeLabel(l.listingType))
                StatusPill(l.status)
                l.itemCondition?.let { Pill(Format.labelize(it)) }
                l.category?.let { Pill(it) }
            }
            MetaText(listOfNotNull(l.neighborhood?.name, Format.timeAgo(l.createdAt)).joinToString(" · "))
            l.locationName?.takeIf { it.isNotBlank() }?.let { LocationPill(it) }
            if (l.description.isNotBlank()) Text(l.description, style = MaterialTheme.typography.bodyMedium)
            l.contactInfo?.takeIf { it.isNotBlank() }?.let { MetaText(stringResource(R.string.listing_contact, it)) }

            Row(
                Modifier.fillMaxWidth().clickable { onOpenProfile(l.userId) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(l.author?.avatarUrl, l.author?.fullName, 40.dp)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(l.author?.fullName.orEmpty(), style = MaterialTheme.typography.titleSmall)
                    MetaText(stringResource(R.string.listing_seller))
                }
            }
            if (l.userId != vm.myId) {
                Button(onClick = { vm.message(l.userId, l.author?.fullName.orEmpty()) }, enabled = !s.busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.Chat, null)
                    Text(stringResource(R.string.listing_message_seller), Modifier.padding(start = 8.dp))
                }
            }
            if (canManage) {
                Text(stringResource(R.string.listing_status), style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Enums.LISTING_STATUSES) { st ->
                        FilterChip(l.status == st, { vm.setStatus(st) }, label = { Text(Format.labelize(st)) }, enabled = !s.busy)
                    }
                }
            }
        }
    }
}
