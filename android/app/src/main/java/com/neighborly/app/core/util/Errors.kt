package com.neighborly.app.core.util

import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import java.io.IOException

/** Typed errors surfaced by repositories. */
sealed class AppError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Offline(cause: Throwable? = null) : AppError("You're offline. Check your connection and try again.", cause)
    class NotAllowed(cause: Throwable? = null) : AppError("You don't have permission to do that.", cause)
    class NotSignedIn : AppError("Please sign in again")
    class Validation(message: String) : AppError(message)
    class Server(message: String, cause: Throwable? = null) : AppError(message, cause)
}

/** Maps any exception to a friendly, user-facing [AppError]. Never includes tokens or personal data. */
fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    is HttpRequestException, is IOException -> AppError.Offline(this)
    is AuthRestException -> AppError.Server(friendlyAuth(this), this)
    is RestException -> {
        val msg = (error + " " + (description ?: "") + " " + (message ?: "")).lowercase()
        when {
            "row-level security" in msg || "permission denied" in msg || "42501" in msg -> AppError.NotAllowed(this)
            "duplicate key" in msg && "username" in msg -> AppError.Validation("That username is taken")
            "duplicate key" in msg -> AppError.Validation("That already exists")
            "jwt" in msg -> AppError.NotSignedIn()
            "admins only" in msg -> AppError.NotAllowed(this)
            "check constraint" in msg -> AppError.Validation("Some fields are invalid or too long")
            else -> AppError.Server(description ?: error, this)
        }
    }
    else -> AppError.Server(message ?: "Something went wrong", this)
}

private fun friendlyAuth(e: AuthRestException): String {
    val m = (e.errorDescription + " " + e.error).lowercase()
    return when {
        "invalid login" in m || "invalid_credentials" in m -> "Wrong email or password"
        "email not confirmed" in m -> "Please confirm your email first. Check your inbox."
        "already registered" in m || "user_already_exists" in m -> "An account with this email already exists"
        "password" in m && "short" in m -> "Password must be at least 8 characters"
        "rate limit" in m -> "Too many attempts. Please wait a minute and try again."
        else -> e.errorDescription.ifBlank { "Sign-in failed" }
    }
}

/** runCatching that never swallows coroutine cancellation and always yields an [AppError]. */
suspend inline fun <T> appResult(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (c: CancellationException) {
    throw c
} catch (t: Throwable) {
    Result.failure(t.toAppError())
}
