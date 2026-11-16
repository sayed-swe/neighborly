package com.neighborly.app.feature.post

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.Avatar
import com.neighborly.app.core.designsystem.LocalExtraColors
import com.neighborly.app.core.designsystem.NbTextStyles
import com.neighborly.app.core.designsystem.SelectField
import com.neighborly.app.core.util.Format
import com.neighborly.app.data.model.Profile
import com.neighborly.app.feature.common.LocationField
import com.neighborly.app.feature.common.PhotoField

/** Stateless composer UI, shared by [ComposerScreen] and the UI tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerContent(
    s: ComposerUiState,
    me: Profile?,
    isAdmin: Boolean,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    onEdit: (ComposerUiState.() -> ComposerUiState) -> Unit,
    onUseMyLocation: () -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(if (s.editing) R.string.composer_edit_title else R.string.composer_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            actions = {
                Button(onClick = onSubmit, enabled = s.canSubmit && !s.busy, modifier = Modifier.padding(end = 8.dp).testTag("composer_submit")) {
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
                    onSelect = { n -> onEdit { copy(neighborhood = n) } },
                    noneLabel = stringResource(R.string.composer_all_neighborhoods),
                    modifier = Modifier.weight(1f),
                )
                SelectField(
                    label = stringResource(R.string.composer_category),
                    options = s.categories,
                    selected = s.category,
                    optionLabel = { it.name },
                    onSelect = { c -> onEdit { copy(category = c) } },
                    noneLabel = stringResource(R.string.composer_no_category),
                    modifier = Modifier.weight(1f),
                )
            }
            if (isAdmin) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(s.announcement, { v -> onEdit { copy(announcement = v) } })
                    Text(stringResource(R.string.composer_announcement))
                }
                if (s.announcement) {
                    OutlinedTextField(
                        s.title, { v -> onEdit { copy(title = v.take(200)) } },
                        label = { Text(stringResource(R.string.composer_announcement_title)) }, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            OutlinedTextField(
                value = s.body,
                onValueChange = { v -> onEdit { copy(body = v.take(5000)) } },
                placeholder = { Text(stringResource(R.string.composer_placeholder, Format.firstName(me?.fullName))) },
                minLines = 5,
                modifier = Modifier.fillMaxWidth().testTag("composer_body"),
            )
            if (s.showPhoto) {
                PhotoField(
                    s.newPhoto, s.keepImage, onPick = { u -> onEdit { copy(newPhoto = u) } },
                    onRemove = { onEdit { copy(newPhoto = null, keepImage = null) } },
                )
            }
            if (s.showLocation) {
                LocationField(
                    s.locationName, s.latitude, s.longitude,
                    onName = { v -> onEdit { copy(locationName = v) } },
                    onUseMyLocation = onUseMyLocation,
                    onClearCoords = { onEdit { copy(latitude = null, longitude = null) } },
                    label = stringResource(R.string.composer_where),
                    locating = s.locating,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.composer_add_to_post), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { onEdit { copy(showPhoto = true) } }) {
                    Icon(Icons.Outlined.AddPhotoAlternate, stringResource(R.string.composer_photo), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { onEdit { copy(showLocation = true) } }) {
                    Icon(Icons.Outlined.Place, stringResource(R.string.composer_location), tint = LocalExtraColors.current.accent)
                }
            }
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
