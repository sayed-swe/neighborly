package com.neighborly.app.data.repository

import com.neighborly.app.core.util.AppError
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Embedded author, same string as AUTHOR in api.js. */
internal const val AUTHOR = "author:profiles(id, full_name, username, avatar_url, role)"
internal const val NEIGHBORHOOD_NAME = "neighborhood:neighborhoods(name)"

internal fun SupabaseClient.uid(): String =
    auth.currentUserOrNull()?.id ?: throw AppError.NotSignedIn()

internal fun ilikeAny(q: String) = "%$q%"

/** Used to unsubscribe Realtime channels after the collecting scope is gone. */
internal val CleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
