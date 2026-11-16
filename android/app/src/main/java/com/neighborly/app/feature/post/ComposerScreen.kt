package com.neighborly.app.feature.post

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.neighborly.app.core.location.LocationProvider
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.Category
import com.neighborly.app.data.model.Neighborhood
import com.neighborly.app.data.repository.FeedRepo
import com.neighborly.app.data.repository.LookupRepo
import com.neighborly.app.data.repository.NewPost
import com.neighborly.app.data.repository.StorageRepo
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

    /** Same check as is_admin() in the database: admins and moderators may post announcements. */
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
            appResult { location.current() }
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

@Composable
fun ComposerScreen(onBack: () -> Unit, onDone: () -> Unit, vm: ComposerViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.done) { if (s.done) onDone() }
    ComposerContent(s, vm.me, vm.isAdmin, onBack, vm::submit, vm::edit, vm::useMyLocation)
}
