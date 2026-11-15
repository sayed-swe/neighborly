package com.neighborly.app.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.neighborly.app.data.model.Reaction

/** The 7-emoji picker shown on long-press of Like. */
@Composable
fun ReactionPicker(onPick: (Reaction) -> Unit, onDismiss: () -> Unit) {
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(0, -170),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(shape = RoundedCornerShape(50), shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Reaction.entries.forEach { r ->
                    Box(
                        Modifier.size(48.dp).clickable { onPick(r) }.semantics { contentDescription = r.label },
                        contentAlignment = Alignment.Center,
                    ) { Text(r.emoji, fontSize = 28.sp) }
                }
            }
        }
    }
}

/** Up to three overlapping reaction emojis, like the stats row on the web. */
@Composable
fun ReactionStack(types: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
        types.take(3).forEach { Text(Reaction.of(it).emoji, fontSize = 15.sp) }
    }
}
