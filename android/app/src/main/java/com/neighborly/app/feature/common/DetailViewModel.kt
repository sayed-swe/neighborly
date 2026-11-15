package com.neighborly.app.feature.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neighborly.app.core.designsystem.Load
import com.neighborly.app.core.designsystem.UiMessage
import com.neighborly.app.core.designsystem.toUiMessage
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.data.repository.ChatRepo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState<T>(
    val item: Load<T> = Load.Loading,
    val busy: Boolean = false,
    val deleted: Boolean = false,
)

/**
 * Shared detail-screen logic: load one row, run owner actions (status change, delete) and
 * "Message …" (start_conversation). RLS refusals come back as friendly messages.
 */
abstract class DetailViewModel<T>(
    private val chats: ChatRepo,
    private val session: SessionManager,
) : ViewModel() {
    protected val _state = MutableStateFlow(DetailUiState<T>())
    val state = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _openChat = MutableSharedFlow<Pair<Long, String>>()
    val openChat = _openChat.asSharedFlow()

    val myId: String? get() = session.currentUserId
    val canModerate: Boolean get() = session.currentProfile?.isModerator == true

    protected abstract suspend fun fetch(): Result<T?>
    protected abstract fun ownerId(item: T): String

    fun isOwnerOrMod(item: T) = ownerId(item) == myId || canModerate

    fun load() {
        viewModelScope.launch {
            fetch()
                .onSuccess { v -> _state.update { it.copy(item = if (v == null) Load.Error("This item is no longer available") else Load.Data(v)) } }
                .onFailure { e -> _state.update { it.copy(item = Load.Error(e.message.orEmpty())) } }
        }
    }

    protected fun send(m: UiMessage) { _messages.trySend(m) }

    /** Runs a write, then reloads. */
    protected fun mutate(block: suspend () -> Result<Unit>) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            block()
                .onSuccess { load() }
                .onFailure { send(it.toUiMessage()) }
            _state.update { it.copy(busy = false) }
        }
    }

    protected fun remove(block: suspend () -> Result<Unit>) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            block()
                .onSuccess { _state.update { it.copy(deleted = true, busy = false) } }
                .onFailure { e -> send(e.toUiMessage()); _state.update { it.copy(busy = false) } }
        }
    }

    fun message(otherUserId: String, name: String) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            chats.start(otherUserId)
                .onSuccess { _openChat.emit(it to name) }
                .onFailure { send(it.toUiMessage()) }
            _state.update { it.copy(busy = false) }
        }
    }
}
