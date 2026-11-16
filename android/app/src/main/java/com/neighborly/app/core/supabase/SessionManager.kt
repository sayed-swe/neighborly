package com.neighborly.app.core.supabase

import com.neighborly.app.data.model.Profile
import com.neighborly.app.data.repository.ProfileRepo
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    /** Opened from a password-reset email: ask for a new password. */
    data object PasswordRecovery : AuthState
    data class SignedIn(val profile: Profile) : AuthState
    data class Blocked(val profile: Profile) : AuthState
    data class ProfileError(val message: String) : AuthState
}

/**
 * Single source of truth for "who is signed in". Follows supabase.auth.sessionStatus and loads the
 * profile row after `Authenticated` (the row itself is created by the handle_new_user trigger).
 */
@Singleton
class SessionManager @Inject constructor(
    private val supabase: SupabaseClient,
    private val profiles: ProfileRepo,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private var recoveryRequested = false
    private var loadedFor: String? = null

    init {
        scope.launch {
            supabase.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val uid = status.session.user?.id ?: return@collect
                        if (recoveryRequested) {
                            _state.value = AuthState.PasswordRecovery
                        } else if (loadedFor != uid || _state.value !is AuthState.SignedIn) {
                            loadedFor = uid
                            loadProfile(uid)
                        }
                    }
                    is SessionStatus.NotAuthenticated -> {
                        loadedFor = null
                        _state.value = AuthState.SignedOut
                    }
                    is SessionStatus.RefreshFailure -> Unit // keep the cached session; supabase-kt retries
                    SessionStatus.Initializing -> _state.value = AuthState.Loading
                }
            }
        }
    }

    val currentProfile: Profile?
        get() = when (val s = _state.value) {
            is AuthState.SignedIn -> s.profile
            is AuthState.Blocked -> s.profile
            else -> null
        }

    val currentUserId: String? get() = supabase.auth.currentUserOrNull()?.id

    /** Called by MainActivity when a password-recovery deep link arrives. */
    fun markRecovery() {
        recoveryRequested = true
        if (supabase.auth.currentSessionOrNull() != null) _state.value = AuthState.PasswordRecovery
    }

    fun recoveryFinished() {
        recoveryRequested = false
        currentUserId?.let { scope.launch { loadProfile(it) } }
    }

    fun refreshProfile() {
        currentUserId?.let { scope.launch { loadProfile(it) } }
    }

    fun updateLocalProfile(profile: Profile) {
        _state.update { if (it is AuthState.SignedIn) AuthState.SignedIn(profile) else it }
    }

    private suspend fun loadProfile(uid: String) {
        profiles.getProfile(uid)
            .onSuccess { p ->
                _state.value = when {
                    p == null -> AuthState.ProfileError("Your profile could not be found.")
                    !p.isActive -> AuthState.Blocked(p)
                    else -> AuthState.SignedIn(p)
                }
            }
            .onFailure { _state.value = AuthState.ProfileError(it.message ?: "Could not load your profile") }
    }
}
