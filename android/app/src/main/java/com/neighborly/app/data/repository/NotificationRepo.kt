package com.neighborly.app.data.repository

import com.neighborly.app.core.supabase.AppJson
import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.AppNotification
import com.neighborly.app.data.model.IdOnly
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepo @Inject constructor(private val supabase: SupabaseClient) {

    suspend fun list(): Result<List<AppNotification>> = appResult {
        supabase.from("notifications")
            .select(Columns.raw("*, actor:profiles!notifications_actor_id_fkey(id, full_name, avatar_url)")) {
                order("created_at", Order.DESCENDING)
                limit(60)
            }.decodeList<AppNotification>()
    }

    suspend fun countUnread(): Result<Int> = appResult {
        supabase.from("notifications").select(Columns.raw("id")) {
            filter { eq("is_read", false) }
            limit(100)
        }.decodeList<IdOnly>().size
    }

    suspend fun markRead(id: Long): Result<Unit> = appResult {
        supabase.from("notifications").update(buildJsonObject { put("is_read", true) }) { filter { eq("id", id) } }
    }

    suspend fun markAllRead(): Result<Unit> = appResult {
        val me = supabase.uid()
        supabase.from("notifications").update(buildJsonObject { put("is_read", true) }) {
            filter { eq("user_id", me); eq("is_read", false) }
        }
    }

    /**
     * Emits every new notification row for the signed-in user (Realtime). [onSubscribed] runs on
     * every (re)join so callers can refetch anything missed while the socket was down.
     */
    fun inserts(userId: String, onSubscribed: suspend () -> Unit = {}): Flow<AppNotification> = callbackFlow {
        // Unique topic per subscriber: the badge and the open screen may listen at the same time.
        val channel = supabase.channel("notifications-$userId-${java.util.UUID.randomUUID()}")
        // The change listener must be registered BEFORE subscribe(), or the join omits postgres_changes.
        val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "notifications"
            filter("user_id", FilterOperator.EQ, userId)
        }
        val job = launch {
            changes.collect { action ->
                runCatching { AppJson.decodeFromJsonElement(AppNotification.serializer(), action.record) }
                    .onSuccess { trySend(it) }
            }
        }
        val statusJob = launch {
            channel.status.collect { if (it == RealtimeChannel.Status.SUBSCRIBED) onSubscribed() }
        }
        channel.subscribe()
        awaitClose {
            job.cancel()
            statusJob.cancel()
            CleanupScope.launch { supabase.realtime.removeChannel(channel) }
        }
    }
}
