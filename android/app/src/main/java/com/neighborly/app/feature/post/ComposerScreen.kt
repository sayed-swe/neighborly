package com.neighborly.app.feature.post

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.neighborly.app.core.designsystem.LocalExtraColors
import com.neighborly.app.core.designsystem.NbTextStyles
import com.neighborly.app.core.designsystem.SelectField
import com.neighborly.app.core.location.LocationProvider
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.core.util.Format
import com.neighborly.app.data.model.Category
import com.neighborly.app.data.model.Neighborhood
import com.neighborly.app.data.repository.FeedRepo
import com.neighborly.app.data.repository.LookupRepo
import com.neighborly.app.data.repository.NewPost
import com.neighborly.app.data.repository.StorageRepo
import com.neighborly.app.feature.common.LocationField
import com.neighborly.app.feature.common.PhotoField
import com.neighborly.app.navigation.ComposerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ComposerUiState(
    val editing: Boolean = false,
    val body: String = "",
    val title: String = "",
    val announcement: Boolean = false,
    val newPhoto: Uri? = null,
    val keepImage: String? = null,
    val showPhoto: Boolean = false,
    val showLocation: Boolean = false,
    val locationName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val category: Category? = null,
    /** null = all neighborhoods */
    val neighborhood: Neighborhood? = null,
    val categories: List<Category> = emptyList(),
    val neighborhoods: List<Neighborhood> = emptyList(),
    val sharedPostId: Long? = null,
    val locating: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
) {
    val canSubmit get() = body.isNotBlank() || newPhoto != null || keepImage != null || sharedPostId != null
}

@HiltViewModel
class ComposerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val feed: FeedRepo,
    private val lookups: LookupRepo,
    private val storage: StorageRepo,
    private val location: LocationProvider,
    private val session: SessionManager,
) : ViewModel() {
    private val route = savedState.toRoute<ComposerRoute>()
    private val _state = MutableStateFlow(
        ComposerUiState(showPhoto = route.withPhoto, showLocation = route.withLocation, editing = route.editPostId != null),
    )
    val state = _state.asStateFlow()
    val me = session.currentProfile
    val isAdmin get() = me?.isModerator == true

    init {
        viewModelScope.launch {
            val cats = lookups.postCategories().getOrDefault(emptyList())
            val hoods = lookups.neighborhoods().getOrDefault(emptyList())
            _state.update { s ->
                s.copy(
                    categories = cats,
                    neighborhoods = hoods,
                    neighborhood = s.neighborhood ?: if (route.editPostId == null) hoods.firstOrNull { it.id == me?.neighborhoodId } else null,
                    // "Ask" preselects a question-style category when one exists.
                    category = s.category ?: if (route.ask) cats.firstOrNull { c ->
                        listOf("question", "ask", "help", "recommend").any { c.name.contains(it, ignoreCase = true) }
                    } else null,
                )
            }
            route.editPostId?.let { id ->
                feed.post(id).onSuccess { p ->
                    if (p != null) _state.update { s ->
                        s.copy(
                            body = p.body, title = p.title.orEmpty(), announcement = p.postType == "announcement",
                            keepImage = p.imageUrl, showPhoto = p.imageUrl != null,
                            showLocation = p.latitude != null || !p.locationName.isNullOrBlank(),
                            locationName = p.locationName.orEmpty(), latitude = p.latitude, longitude = p.longitude,
                            category = cats.firstOrNull { it.id == p.categoryId },
                            neighborhood = hoods.firstOrNull { it.id == p.neighborhoodId },
                            sharedPostId = p.sharedPostId,
                        )
                    }
                }
            }
        }
    }

    fun edit(t: ComposerUiState.() -> ComposerUiState) = _state.update { it.t().copy(error = null) }

    fun useMyLocation() {
        _state.update { it.copy(locating = true) }
        viewModelScope.launch {
            com.neighborly.app.core.util.appResult { location.current() }
                .onSuccess { p -> _state.update { it.copy(latitude = p.lat, longitude = p.lng, locating = false) } }
                .onFailure { e -> _state.update { it.copy(locating = false, error = e.message) } }
        }
    }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit || s.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val image = s.newPhoto?.let { storage.uploadImage(it, "post").getOrThrow() } ?: s.keepImage
                val post = NewPost(
                    body = s.body.trim(),
                    title = if (s.announcement && s.title.isNotBlank()) s.title.trim() else null,
                    imageUrl = image,
                    categoryId = s.category?.id,
                    neighborhoodId = s.neighborhood?.id,
                    locationName = if (s.showLocation) s.locationName.trim().ifBlank { null } else null,
                    latitude = if (s.showLocation) s.latitude else null,
                    longitude = if (s.showLocation) s.longitude else null,
                    postType = if (isAdmin && s.announcement) "announcement" else "post",
                )
                val id = route.editPostId
                if (id != null) feed.update(id, post).getOrThrow() else feed.create(post).getOrThrow()
            }.onSuccess { _state.update { it.copy(busy = false, done = true) } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerScreen(onBack: () -> Unit, onDone: () -> Unit, vm: ComposerViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.done) { if (s.done) onDone() }
    val me = vm.me
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(if (s.editing) R.string.composer_edit_title else R.string.composer_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            actions = {
                Button(onClick = vm::submit, enabled = s.canSubmit && !s.busy, modifier = Modifier.padding(end = 8.dp)) {
                    Text(stringResource(if (s.busy) R.string.posting else if (s.editing) R.string.action_save else R.string.action_post))
                }
            },
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(me?.avatarUrl, me?.fullName, 44.dp)
                Text(me?.fullName.orEmpty(), style = NbTextStyles.name)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectField(
                    label = stringResource(R.string.composer_audience),
                    options = s.neighborhoods,
                    selected = s.neighborhood,
                    optionLabel = { "🏘️ ${it.name}" },
                    onSelect = { n -> vm.edit { copy(neighborhood = n) } },
                    noneLabel = stringResource(R.string.composer_all_neighborhoods),
                    modifier = Modifier.weight(1f),
                )
                SelectField(
                    label = stringResource(R.string.composer_category),
                    options = s.categories,
                    selected = s.category,
                    optionLabel = { it.name },
                    onSelect = { c -> vm.edit { copy(category = c) } },
                    noneLabel = stringResource(R.string.composer_no_category),
                    modifier = Modifier.weight(1f),
                )
            }
            if (vm.isAdmin) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(s.announcement, { v -> vm.edit { copy(announcement = v) } })
                    Text(stringResource(R.string.composer_announcement))
                }
                if (s.announcement) {
                    OutlinedTextField(s.title, { v -> vm.edit { copy(title = v.take(200)) } },
                        label = { Text(stringResource(R.string.composer_announcement_title)) }, modifier = Modifier.fillMaxWidth())
                }
            }
            OutlinedTextField(
                value = s.body,
                onValueChange = { v -> vm.edit { copy(body = v.take(5000)) } },
                placeholder = { Text(stringResource(R.string.composer_placeholder, Format.firstName(me?.fullName))) },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            if (s.showPhoto) {
                PhotoField(s.newPhoto, s.keepImage, onPick = { u -> vm.edit { copy(newPhoto = u) } },
                    onRemove = { vm.edit { copy(newPhoto = null, keepImage = null) } })
            }
            if (s.showLocation) {
                LocationField(
                    s.locationName, s.latitude, s.longitude,
                    onName = { v -> vm.edit { copy(locationName = v) } },
                    onUseMyLocation = vm::useMyLocation,
                    onClearCoords = { vm.edit { copy(latitude = null, longitude = null) } },
                    label = stringResource(R.string.composer_where),
                    locating = s.locating,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.composer_add_to_post), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.edit { copy(showPhoto = true) } }) {
                    Icon(Icons.Outlined.AddPhotoAlternate, stringResource(R.string.composer_photo), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { vm.edit { copy(showLocation = true) } }) {
                    Icon(Icons.Outlined.Place, stringResource(R.string.composer_location), tint = LocalExtraColors.current.accent)
                }
            }
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
