package com.neighborly.app.feature.services

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
import com.neighborly.app.core.designsystem.PhotoBox
import com.neighborly.app.core.designsystem.SelectField
import com.neighborly.app.core.location.LocationProvider
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.Category
import com.neighborly.app.data.model.ReportTarget
import com.neighborly.app.data.model.Service
import com.neighborly.app.data.repository.ChatRepo
import com.neighborly.app.data.repository.LookupRepo
import com.neighborly.app.data.repository.ServiceRepo
import com.neighborly.app.data.repository.StorageRepo
import com.neighborly.app.feature.common.DetailScaffold
import com.neighborly.app.feature.common.DetailViewModel
import com.neighborly.app.feature.common.FormScaffold
import com.neighborly.app.feature.common.CoordinatesField
import com.neighborly.app.feature.common.MenuAction
import com.neighborly.app.feature.common.PhotoField
import com.neighborly.app.feature.common.ReportSheet
import com.neighborly.app.navigation.ServiceEditRoute
import com.neighborly.app.navigation.ServiceRoute
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
class ServiceDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: ServiceRepo,
    chats: ChatRepo,
    session: SessionManager,
) : DetailViewModel<Service>(chats, session) {
    val id = savedState.toRoute<ServiceRoute>().id
    init { load() }
    override suspend fun fetch() = repo.get(id)
    override fun ownerId(item: Service) = item.providerId
    fun delete() = remove { repo.delete(id) }
}

@Composable
fun ServiceDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenChat: (Long, String) -> Unit,
    vm: ServiceDetailViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    CollectMessages(vm.messages)
    LaunchedEffect(Unit) { vm.openChat.collect { (id, name) -> onOpenChat(id, name) } }
    LaunchedEffect(s.deleted) { if (s.deleted) onBack() }
    val sv = (s.item as? Load.Data)?.value
    val actions = buildList {
        if (sv != null && sv.providerId == vm.myId) add(MenuAction(stringResource(R.string.action_edit), Icons.Outlined.Edit) { onEdit(vm.id) })
        if (sv != null && vm.isOwnerOrMod(sv)) add(MenuAction(stringResource(R.string.action_delete), Icons.Outlined.Delete) { confirmDelete = true })
        if (sv != null && sv.providerId != vm.myId) add(MenuAction(stringResource(R.string.action_report), Icons.Outlined.Flag) { reporting = true })
    }
    DetailScaffold(
        title = sv?.serviceName ?: stringResource(R.string.menu_services),
        onBack = onBack,
        actions = actions,
        overlay = {
            if (confirmDelete) ConfirmDialog(stringResource(R.string.confirm_delete_title), stringResource(R.string.confirm_delete_text),
                stringResource(R.string.action_delete), vm::delete, { confirmDelete = false })
            if (reporting) ReportSheet(ReportTarget.SERVICE, vm.id.toString(), onDismiss = { reporting = false })
        },
    ) {
        when (val st = s.item) {
            Load.Loading -> item { ListSkeleton(1) }
            is Load.Error -> item { ErrorState(st.message, vm::load) }
            is Load.Data -> item {
                val x = st.value
                NbCard {
                    if (!x.imageUrl.isNullOrBlank()) PhotoBox(x.imageUrl, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(x.serviceName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f, fill = false))
                            if (x.isVerified) Icon(Icons.Outlined.Verified, stringResource(R.string.badge_verified), tint = MaterialTheme.colorScheme.primary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            x.category?.name?.let { Pill(listOfNotNull(x.category.icon, it).joinToString(" ")) }
                            if (!x.isActive) Pill(stringResource(R.string.services_inactive))
                        }
                        x.priceInfo?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                        if (x.description.isNotBlank()) Text(x.description)
                        MetaText(listOfNotNull(x.contactPhone, x.contactEmail, x.neighborhood?.name).joinToString(" · "))
                        Row(Modifier.clickable { onOpenProfile(x.providerId) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(x.provider?.avatarUrl, x.provider?.fullName, 36.dp)
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(x.provider?.fullName.orEmpty(), style = MaterialTheme.typography.titleSmall)
                                MetaText(stringResource(R.string.services_provider))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { dial(context, x.contactPhone) }, modifier = Modifier.weight(1f).height(48.dp)) {
                                Icon(Icons.Outlined.Phone, null); Text(stringResource(R.string.action_call), Modifier.padding(start = 6.dp))
                            }
                            if (x.providerId != vm.myId) {
                                OutlinedButton(onClick = { vm.message(x.providerId, x.provider?.fullName.orEmpty()) }, enabled = !s.busy,
                                    modifier = Modifier.weight(1f).height(48.dp)) {
                                    Icon(Icons.AutoMirrored.Outlined.Chat, null); Text(stringResource(R.string.action_message), Modifier.padding(start = 6.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- create / edit ---------------- */

data class ServiceForm(
    val name: String = "",
    val category: Category? = null,
    val description: String = "",
    val phone: String = "",
    val email: String = "",
    val priceInfo: String = "",
    val active: Boolean = true,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val newPhoto: Uri? = null,
    val imageUrl: String? = null,
    val categories: List<Category> = emptyList(),
    val busy: Boolean = false,
    val locating: Boolean = false,
    val error: String? = null,
    val savedId: Long? = null,
) {
    val valid get() = name.trim().length >= 2 && category != null && phone.isNotBlank()
}

@HiltViewModel
class ServiceEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: ServiceRepo,
    private val lookups: LookupRepo,
    private val storage: StorageRepo,
    private val locationProvider: LocationProvider,
    private val session: SessionManager,
) : ViewModel() {
    val editId = savedState.toRoute<ServiceEditRoute>().id
    private val _state = MutableStateFlow(ServiceForm(phone = session.currentProfile?.phone.orEmpty()))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val cats = lookups.serviceCategories().getOrDefault(emptyList())
            _state.update { it.copy(categories = cats) }
            editId?.let { id ->
                repo.get(id).onSuccess { x ->
                    if (x != null) _state.update {
                        it.copy(
                            name = x.serviceName, category = cats.firstOrNull { c -> c.id == x.serviceCategoryId }, description = x.description,
                            phone = x.contactPhone, email = x.contactEmail.orEmpty(), priceInfo = x.priceInfo.orEmpty(), active = x.isActive,
                            latitude = x.latitude, longitude = x.longitude, imageUrl = x.imageUrl,
                        )
                    }
                }
            }
        }
    }

    fun edit(t: ServiceForm.() -> ServiceForm) = _state.update { it.t().copy(error = null) }

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
                val image = f.newPhoto?.let { storage.uploadImage(it, "service").getOrThrow() } ?: f.imageUrl
                val values = buildJsonObject {
                    put("service_name", f.name.trim())
                    put("service_category_id", f.category!!.id)
                    put("description", f.description.trim())
                    put("contact_phone", f.phone.trim())
                    put("contact_email", f.email.trim().ifBlank { null })
                    put("price_info", f.priceInfo.trim().ifBlank { null })
                    put("is_active", f.active)
                    put("latitude", f.latitude)
                    put("longitude", f.longitude)
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
fun ServiceEditScreen(onBack: () -> Unit, onSaved: (Long, Boolean) -> Unit, vm: ServiceEditViewModel = hiltViewModel()) {
    val f by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(f.savedId) { f.savedId?.let { onSaved(it, vm.editId == null) } }
    FormScaffold(
        title = stringResource(if (vm.editId == null) R.string.services_offer else R.string.services_edit),
        onBack = onBack,
        submitLabel = stringResource(if (vm.editId == null) R.string.action_publish else R.string.action_save),
        canSubmit = f.valid, busy = f.busy, error = f.error, onSubmit = vm::save,
    ) {
        PhotoField(f.newPhoto, f.imageUrl, { u -> vm.edit { copy(newPhoto = u) } }, { vm.edit { copy(newPhoto = null, imageUrl = null) } })
        NbTextField(f.name, { v -> vm.edit { copy(name = v.take(150)) } }, stringResource(R.string.services_name))
        SelectField(stringResource(R.string.field_category), f.categories, f.category, { listOfNotNull(it.icon, it.name).joinToString(" ") },
            { c -> vm.edit { copy(category = c) } })
        NbTextField(f.description, { v -> vm.edit { copy(description = v.take(3000)) } }, stringResource(R.string.field_description),
            singleLine = false, minLines = 4)
        NbTextField(f.phone, { v -> vm.edit { copy(phone = v.take(20)) } }, stringResource(R.string.field_phone), keyboardType = KeyboardType.Phone)
        NbTextField(f.email, { v -> vm.edit { copy(email = v) } }, stringResource(R.string.field_email), keyboardType = KeyboardType.Email)
        NbTextField(f.priceInfo, { v -> vm.edit { copy(priceInfo = v.take(100)) } }, stringResource(R.string.services_price_info))
        Text(stringResource(R.string.services_area), style = MaterialTheme.typography.titleSmall)
        CoordinatesField(f.latitude, f.longitude, vm::useMyLocation, { vm.edit { copy(latitude = null, longitude = null) } }, f.locating)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.services_active), Modifier.weight(1f))
            Switch(f.active, { v -> vm.edit { copy(active = v) } })
        }
    }
}
