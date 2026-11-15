package com.neighborly.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neighborly.app.core.supabase.AuthState
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.data.repository.AlertRepo
import com.neighborly.app.data.repository.AuthRepo
import com.neighborly.app.data.repository.ChatRepo
import com.neighborly.app.data.repository.NotificationRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Badges(val notifications: Int = 0, val chats: Int = 0, val alerts: Int = 0)

/** App-wide state: who is signed in, plus live badge counts (Realtime). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RootViewModel @Inject constructor(
    private val session: SessionManager,
    private val auth: AuthRepo,
    private val notifications: NotificationRepo,
    private val chats: ChatRepo,
    private val alerts: AlertRepo,
) : ViewModel() {
    val authState: StateFlow<AuthState> = session.state

    private val _badges = MutableStateFlow(Badges())
    val badges: StateFlow<Badges> = _badges.asStateFlow()

    private var liveJobs: List<Job> = emptyList()

    init {
        viewModelScope.launch {
            session.state.distinctUntilChangedBy { (it as? AuthState.SignedIn)?.profile?.id }.collectLatest { s ->
                liveJobs.forEach { it.cancel() }
                if (s is AuthState.SignedIn) startLive(s.profile.id) else _badges.value = Badges()
            }
        }
    }

    private fun startLive(userId: String) {
        refreshBadges()
        liveJobs = listOf(
            viewModelScope.launch { notifications.inserts(userId).collect { _badges.update { it.copy(notifications = it.notifications + 1) } } },
            viewModelScope.launch {
                chats.anyNewMessage(userId).collect { m -> if (m.senderId != userId) refreshChats() }
            },
            // Alerts are not in the Realtime publication: poll the count every minute.
            viewModelScope.launch {
                while (true) {
                    delay(60_000)
                    alerts.countActive().onSuccess { n -> _badges.update { it.copy(alerts = n) } }
                }
            },
        )
    }

    fun refreshBadges() {
        viewModelScope.launch {
            notifications.countUnread().onSuccess { n -> _badges.update { it.copy(notifications = n) } }
            alerts.countActive().onSuccess { n -> _badges.update { it.copy(alerts = n) } }
        }
        refreshChats()
    }

    private fun refreshChats() {
        viewModelScope.launch {
            chats.conversations().onSuccess { list -> _badges.update { it.copy(chats = list.sumOf { c -> c.unreadCount }) } }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            if (session.state.value is AuthState.PasswordRecovery) session.recoveryFinished()
            auth.signOut()
        }
    }
}
