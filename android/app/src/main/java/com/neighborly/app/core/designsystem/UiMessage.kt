package com.neighborly.app.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow

/** A one-off message for the snackbar: either a string resource or text from the server. */
sealed interface UiMessage {
    data class Res(@StringRes val id: Int) : UiMessage
    data class Text(val text: String) : UiMessage
}

fun Throwable.toUiMessage(): UiMessage = UiMessage.Text(message ?: "Something went wrong")

val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

/** Shows every message from [messages] in the app-wide snackbar. */
@Composable
fun CollectMessages(messages: Flow<UiMessage>) {
    val host = LocalSnackbar.current
    val context = LocalContext.current
    LaunchedEffect(messages) {
        messages.collect { m ->
            val text = when (m) {
                is UiMessage.Res -> context.getString(m.id)
                is UiMessage.Text -> m.text
            }
            host.showSnackbar(text)
        }
    }
}
