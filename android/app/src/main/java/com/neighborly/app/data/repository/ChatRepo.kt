package com.neighborly.app.data.repository

import com.neighborly.app.core.supabase.AppJson
import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.ConversationMember
import com.neighborly.app.data.model.ConversationSummary
import com.neighborly.app.data.model.Message
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepo @Inject constructor(private val supabase: SupabaseClient) {

    suspend fun conversations(): Result<List<ConversationSummary>> = appResult {
        supabase.postgrest.rpc("my_conversations").decodeList<ConversationSummary>()
    }

    /** Finds or creates the 1:1 conversation. */
    suspend fun start(otherUserId: String): Result<Long> = appResult {
        supabase.postgrest.rpc("start_conversation", buildJsonObject { put("other_user", otherUserId) }).decodeAs<Long>()
    }

    suspend fun messages(conversationId: Long): Result<List<Message>> = appResult {
        supabase.from("messages").select {
            filter { eq("conversation_id", conversationId) }
            order("created_at", Order.ASCENDING)
            limit(500)
        }.decodeList<Message>()
    }

    suspend fun send(conversationId: Long, body: String): Result<Message> = appResult {
        supabase.from("messages").insert(buildJsonObject {
            put("conversation_id", conversationId)
            put("sender_id", supabase.uid())
            put("body", body.trim())
        }) { select() }.decodeSingle<Message>()
    }

    suspend fun markRead(conversationId: Long): Result<Unit> = appResult {
        val me = supabase.uid()
        supabase.from("conversation_members").update(buildJsonObject { put("last_read_at", Instant.now().toString()) }) {
            filter { eq("conversation_id", conversationId); eq("user_id", me) }
        }
    }

    suspend fun members(conversationId: Long): Result<List<ConversationMember>> = appResult {
        supabase.from("conversation_members")
            .select(Columns.raw("user_id, user:profiles(id, full_name, username, avatar_url)")) {
                filter { eq("conversation_id", conversationId) }
            }.decodeList<ConversationMember>()
    }

    /** New messages in one chat (Realtime). */
    fun newMessages(conversationId: Long, onSubscribed: suspend () -> Unit = {}): Flow<Message> =
        insertsOf("conv-$conversationId", onSubscribed) {
            filter("conversation_id", FilterOperator.EQ, conversationId)
        }

    /** Any new message visible to me (RLS limits rows to my conversations) — used for the unread badge. */
    fun anyNewMessage(userId: String, onSubscribed: suspend () -> Unit = {}): Flow<Message> =
        insertsOf("inbox-$userId", onSubscribed) {}

    /**
     * [onSubscribed] runs every time the channel (re)joins. Realtime does not replay events missed
     * while the socket was down, so callers refetch there to fill the gap.
     */
    private fun insertsOf(
        name: String,
        onSubscribed: suspend () -> Unit,
        config: io.github.jan.supabase.realtime.PostgresChangeFilter.() -> Unit,
    ): Flow<Message> = callbackFlow {
        // Unique topic per subscriber: the badge and an open screen may listen at the same time.
        val channel = supabase.channel("$name-${java.util.UUID.randomUUID()}")
        // The change listener must be registered BEFORE subscribe(), or the join omits postgres_changes.
        val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
            config()
        }
        val job = launch {
            changes.collect { action ->
                runCatching { AppJson.decodeFromJsonElement(Message.serializer(), action.record) }.onSuccess { trySend(it) }
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
