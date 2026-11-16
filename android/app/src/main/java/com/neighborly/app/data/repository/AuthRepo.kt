package com.neighborly.app.data.repository

import com.neighborly.app.core.supabase.AUTH_RECOVERY_REDIRECT
import com.neighborly.app.core.supabase.AUTH_REDIRECT
import com.neighborly.app.core.util.appResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

data class SignUpForm(
    val fullName: String,
    val username: String,
    val phone: String,
    val neighborhoodId: Long?,
    val email: String,
    val password: String,
)

/** Result of sign-up: true when a session was created immediately (email confirmation disabled). */
@Singleton
class AuthRepo @Inject constructor(private val supabase: SupabaseClient) {

    suspend fun signIn(email: String, password: String): Result<Unit> = appResult {
        supabase.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signUp(form: SignUpForm): Result<Boolean> = appResult {
        supabase.auth.signUpWith(Email, redirectUrl = AUTH_REDIRECT) {
            email = form.email.trim()
            password = form.password
            // Keys read by the handle_new_user trigger.
            data = buildJsonObject {
                put("full_name", form.fullName.trim())
                put("username", form.username.trim().lowercase())
                put("phone", form.phone.trim())
                put("neighborhood_id", form.neighborhoodId?.toString() ?: "")
            }
        }
        supabase.auth.currentSessionOrNull() != null
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = appResult {
        supabase.auth.resetPasswordForEmail(email.trim(), redirectUrl = AUTH_RECOVERY_REDIRECT)
    }

    suspend fun updatePassword(newPassword: String): Result<Unit> = appResult {
        supabase.auth.updateUser { password = newPassword }
    }

    suspend fun signOut(): Result<Unit> = appResult { supabase.auth.signOut() }

    companion object {
        val USERNAME_REGEX = Regex("^[a-z0-9_.]{3,30}$")
        val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        const val MIN_PASSWORD = 8
    }
}
