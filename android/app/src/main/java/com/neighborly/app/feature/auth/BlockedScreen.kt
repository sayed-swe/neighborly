package com.neighborly.app.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.NbCard

/** Shown when profiles.status != 'active' (suspended / banned). */
@Composable
fun BlockedScreen(status: String, onSignOut: () -> Unit) {
    AuthScaffold {
        NbCard {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(
                    stringResource(if (status == "banned") R.string.blocked_banned_title else R.string.blocked_suspended_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(stringResource(R.string.blocked_text), textAlign = TextAlign.Center)
                Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(stringResource(R.string.action_log_out))
                }
            }
        }
    }
}
