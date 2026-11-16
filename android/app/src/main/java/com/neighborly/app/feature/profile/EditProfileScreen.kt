package com.neighborly.app.feature.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.Avatar
import com.neighborly.app.core.designsystem.NbTextField
import com.neighborly.app.core.designsystem.SecondaryButton
import com.neighborly.app.core.designsystem.SelectField
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.data.model.Neighborhood
import com.neighborly.app.data.repository.LookupRepo
import com.neighborly.app.data.repository.ProfileRepo
import com.neighborly.app.data.repository.StorageRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

data class EditProfileUiState(
    val fullName: String = "",
    val bio: String = "",
    val phone: String = "",
    val address: String = "",
    val neighborhood: Neighborhood? = null,
    val avatarUrl: String? = null,
    val coverUrl: String? = null,
    val newAvatar: Uri? = null,
    val newCover: Uri? = null,
    val neighborhoods: List<Neighborhood> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val session: SessionManager,
    private val profiles: ProfileRepo,
    private val storage: StorageRepo,
    private val lookups: LookupRepo,
) : ViewModel() {
    private val _state = MutableStateFlow(EditProfileUiState())
    val state = _state.asStateFlow()

    init {
        session.currentProfile?.let { p ->
            _state.value = EditProfileUiState(
                fullName = p.fullName, bio = p.bio.orEmpty(), phone = p.phone.orEmpty(), address = p.address.orEmpty(),
                neighborhood = p.neighborhood, avatarUrl = p.avatarUrl, coverUrl = p.coverUrl,
            )
        }
        viewModelScope.launch {
            lookups.neighborhoods().onSuccess { list ->
                _state.update { s -> s.copy(neighborhoods = list, neighborhood = list.firstOrNull { it.id == s.neighborhood?.id } ?: s.neighborhood) }
            }
        }
    }

    fun edit(t: EditProfileUiState.() -> EditProfileUiState) = _state.update { it.t().copy(error = null) }

    fun save() {
        val s = _state.value
        val me = session.currentProfile ?: return
        if (s.fullName.isBlank()) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val avatar = s.newAvatar?.let { storage.uploadImage(it, "avatar").getOrThrow() } ?: s.avatarUrl
                val cover = s.newCover?.let { storage.uploadImage(it, "cover").getOrThrow() } ?: s.coverUrl
                profiles.update(me.id, buildJsonObject {
                    put("full_name", s.fullName.trim())
                    put("bio", s.bio.trim().ifBlank { null })
                    put("phone", s.phone.trim().ifBlank { null })
                    put("address", s.address.trim().ifBlank { null })
                    put("neighborhood_id", s.neighborhood?.id)
                    put("avatar_url", avatar)
                    put("cover_url", cover)
                }).getOrThrow()
            }.onSuccess {
                session.refreshProfile()
                _state.update { it.copy(busy = false, saved = true) }
            }.onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(onBack: () -> Unit, vm: EditProfileViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.saved) { if (s.saved) onBack() }
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.edit { copy(newAvatar = uri) }
    }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.edit { copy(newCover = uri) }
    }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.profile_edit)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().height(140.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small)
                    .clickable { pickCover.launch(imageOnly) },
                contentAlignment = Alignment.Center,
            ) {
                val cover: Any? = s.newCover ?: s.coverUrl
                if (cover != null) AsyncImage(cover, stringResource(R.string.cd_cover_photo), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(140.dp))
                Icon(Icons.Outlined.PhotoCamera, stringResource(R.string.profile_change_cover), tint = MaterialTheme.colorScheme.primary)
            }
            Box(Modifier.align(Alignment.CenterHorizontally).clickable { pickAvatar.launch(imageOnly) }) {
                if (s.newAvatar != null) {
                    AsyncImage(s.newAvatar, stringResource(R.string.cd_avatar), contentScale = ContentScale.Crop,
                        modifier = Modifier.height(88.dp).fillMaxWidth(0.25f))
                } else Avatar(s.avatarUrl, s.fullName, size = 88.dp)
            }
            SecondaryButton(stringResource(R.string.profile_change_avatar), { pickAvatar.launch(imageOnly) },
                Modifier.align(Alignment.CenterHorizontally), icon = Icons.Outlined.PhotoCamera)
            NbTextField(s.fullName, { v -> vm.edit { copy(fullName = v) } }, stringResource(R.string.field_full_name))
            NbTextField(s.bio, { v -> vm.edit { copy(bio = v.take(500)) } }, stringResource(R.string.field_bio), singleLine = false, minLines = 3)
            NbTextField(s.phone, { v -> vm.edit { copy(phone = v.take(20)) } }, stringResource(R.string.field_phone), keyboardType = KeyboardType.Phone)
            NbTextField(s.address, { v -> vm.edit { copy(address = v.take(255)) } }, stringResource(R.string.field_address))
            SelectField(
                label = stringResource(R.string.field_neighborhood),
                options = s.neighborhoods,
                selected = s.neighborhood,
                optionLabel = { it.name },
                onSelect = { n -> vm.edit { copy(neighborhood = n) } },
            )
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = vm::save, enabled = !s.busy && s.fullName.isNotBlank(), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(if (s.busy) R.string.saving else R.string.action_save))
            }
        }
    }
}
