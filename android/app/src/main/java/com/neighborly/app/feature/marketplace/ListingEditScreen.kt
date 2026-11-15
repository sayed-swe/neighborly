package com.neighborly.app.feature.marketplace

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.neighborly.app.core.designsystem.NbTextField
import com.neighborly.app.core.designsystem.SelectField
import com.neighborly.app.core.location.LocationProvider
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.core.util.Format
import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.Enums
import com.neighborly.app.data.repository.ListingRepo
import com.neighborly.app.data.repository.StorageRepo
import com.neighborly.app.feature.common.FormScaffold
import com.neighborly.app.feature.common.LocationField
import com.neighborly.app.feature.common.PhotoField
import com.neighborly.app.navigation.ListingEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

data class ListingForm(
    val type: String = "sell",
    val title: String = "",
    val description: String = "",
    val category: String? = null,
    val condition: String? = null,
    val price: String = "",
    val contact: String = "",
    val locationName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val newPhoto: Uri? = null,
    val imageUrl: String? = null,
    val busy: Boolean = false,
    val locating: Boolean = false,
    val error: String? = null,
    val savedId: Long? = null,
) {
    val valid get() = title.trim().length >= 2 && (price.isBlank() || price.toDoubleOrNull()?.let { it >= 0 } == true)
}

@HiltViewModel
class ListingEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: ListingRepo,
    private val storage: StorageRepo,
    private val location: LocationProvider,
    private val session: SessionManager,
) : ViewModel() {
    val editId = savedState.toRoute<ListingEditRoute>().id
    private val _state = MutableStateFlow(ListingForm())
    val state = _state.asStateFlow()

    init {
        editId?.let { id ->
            viewModelScope.launch {
                repo.get(id).onSuccess { l ->
                    if (l != null) _state.value = ListingForm(
                        type = l.listingType, title = l.title, description = l.description, category = l.category,
                        condition = l.itemCondition, price = l.price?.let { p -> if (p % 1.0 == 0.0) p.toLong().toString() else p.toString() }.orEmpty(),
                        contact = l.contactInfo.orEmpty(), locationName = l.locationName.orEmpty(),
                        latitude = l.latitude, longitude = l.longitude, imageUrl = l.imageUrl,
                    )
                }
            }
        }
    }

    fun edit(t: ListingForm.() -> ListingForm) = _state.update { it.t().copy(error = null) }

    fun useMyLocation() {
        _state.update { it.copy(locating = true) }
        viewModelScope.launch {
            appResult { location.current() }
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
                val image = f.newPhoto?.let { storage.uploadImage(it, "listing").getOrThrow() } ?: f.imageUrl
                val values = buildJsonObject {
                    put("listing_type", f.type)
                    put("title", f.title.trim())
                    put("description", f.description.trim())
                    put("category", f.category)
                    put("item_condition", f.condition)
                    put("price", if (f.type == "free") null else f.price.toDoubleOrNull())
                    put("contact_info", f.contact.trim().ifBlank { null })
                    put("location_name", f.locationName.trim().ifBlank { null })
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
fun ListingEditScreen(onBack: () -> Unit, onSaved: (Long, Boolean) -> Unit, vm: ListingEditViewModel = hiltViewModel()) {
    val f by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(f.savedId) { f.savedId?.let { onSaved(it, vm.editId == null) } }
    FormScaffold(
        title = stringResource(if (vm.editId == null) R.string.listing_new else R.string.listing_edit),
        onBack = onBack,
        submitLabel = stringResource(if (vm.editId == null) R.string.action_publish else R.string.action_save),
        canSubmit = f.valid,
        busy = f.busy,
        error = f.error,
        onSubmit = vm::save,
    ) {
        Text(stringResource(R.string.listing_type), style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Enums.LISTING_TYPES) { t -> FilterChip(f.type == t, { vm.edit { copy(type = t) } }, label = { Text(listingTypeLabel(t)) }) }
        }
        PhotoField(f.newPhoto, f.imageUrl, { u -> vm.edit { copy(newPhoto = u) } }, { vm.edit { copy(newPhoto = null, imageUrl = null) } })
        NbTextField(f.title, { v -> vm.edit { copy(title = v.take(200)) } }, stringResource(R.string.field_title))
        if (f.type != "free") {
            NbTextField(f.price, { v -> vm.edit { copy(price = v.filter { it.isDigit() || it == '.' }) } },
                stringResource(R.string.field_price), keyboardType = KeyboardType.Decimal, supporting = stringResource(R.string.hint_price))
        }
        SelectField(stringResource(R.string.field_category), Enums.LISTING_CATEGORIES, f.category, { it },
            { c -> vm.edit { copy(category = c) } }, noneLabel = stringResource(R.string.composer_no_category))
        SelectField(stringResource(R.string.field_condition), Enums.CONDITIONS, f.condition, { Format.labelize(it) },
            { c -> vm.edit { copy(condition = c) } }, noneLabel = stringResource(R.string.not_specified))
        NbTextField(f.description, { v -> vm.edit { copy(description = v.take(5000)) } }, stringResource(R.string.field_description),
            singleLine = false, minLines = 4)
        NbTextField(f.contact, { v -> vm.edit { copy(contact = v.take(120)) } }, stringResource(R.string.field_contact))
        LocationField(f.locationName, f.latitude, f.longitude, { v -> vm.edit { copy(locationName = v) } }, vm::useMyLocation,
            { vm.edit { copy(latitude = null, longitude = null) } }, locating = f.locating)
    }
}
