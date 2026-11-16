package com.neighborly.app.feature.common

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.MetaText
import com.neighborly.app.core.designsystem.NbTextField
import com.neighborly.app.core.designsystem.SecondaryButton
import com.neighborly.app.core.location.rememberLocationRequest
import java.util.Locale

/** Photo Picker field: shows the picked/new photo or the existing URL, with remove. */
@Composable
fun PhotoField(newPhoto: Uri?, existingUrl: String?, onPick: (Uri) -> Unit, onRemove: () -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onPick(uri) }
    val shown: Any? = newPhoto ?: existingUrl
    if (shown == null) {
        SecondaryButton(
            stringResource(R.string.action_add_photo),
            { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            icon = Icons.Outlined.AddPhotoAlternate,
        )
    } else {
        Box {
            AsyncImage(
                shown, stringResource(R.string.cd_item_photo), contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(MaterialTheme.shapes.small),
            )
            FilledTonalIconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape)) {
                Icon(Icons.Outlined.Close, stringResource(R.string.action_remove_photo))
            }
        }
    }
}

/** Location name + "Use my location" (fills latitude / longitude). */
@Composable
fun LocationField(
    name: String,
    latitude: Double?,
    longitude: Double?,
    onName: (String) -> Unit,
    onUseMyLocation: () -> Unit,
    onClearCoords: () -> Unit,
    label: String = stringResource(R.string.field_location_name),
    locating: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NbTextField(name, { onName(it.take(255)) }, label)
        CoordinatesField(latitude, longitude, onUseMyLocation, onClearCoords, locating)
    }
}

/** "Use my location" button + the picked coordinates (used for the map). */
@Composable
fun CoordinatesField(
    latitude: Double?,
    longitude: Double?,
    onUseMyLocation: () -> Unit,
    onClear: () -> Unit,
    locating: Boolean = false,
) {
    val request = rememberLocationRequest(onGranted = onUseMyLocation)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton(stringResource(R.string.action_use_my_location), request, icon = Icons.Outlined.MyLocation, enabled = !locating)
        if (latitude != null && longitude != null) {
            MetaText("%.4f, %.4f".format(Locale.US, latitude, longitude), Modifier.weight(1f))
            FilledTonalIconButton(onClick = onClear) { Icon(Icons.Outlined.Close, stringResource(R.string.action_clear)) }
        }
    }
}
