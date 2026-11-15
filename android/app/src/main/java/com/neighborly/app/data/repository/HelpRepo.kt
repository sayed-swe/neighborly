package com.neighborly.app.data.repository

import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.HelpRequest
import com.neighborly.app.data.model.IdOnly
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HelpRepo @Inject constructor(private val supabase: SupabaseClient) {
    private val columns = Columns.raw(
        "*, $AUTHOR, $NEIGHBORHOOD_NAME, " +
            "help_responses(id, message, created_at, responder:profiles(id, full_name, username, avatar_url))",
    )

    /** status: "active" = open + in_progress, null = all, otherwise exact. */
    suspend fun list(status: String? = "active"): Result<List<HelpRequest>> = appResult {
        supabase.from("help_requests").select(columns) {
            filter {
                when (status) {
                    null -> Unit
                    "active" -> isIn("status", listOf("open", "in_progress"))
                    else -> eq("status", status)
                }
            }
            order("created_at", Order.DESCENDING)
            limit(50)
        }.decodeList<HelpRequest>()
    }

    suspend fun get(id: Long): Result<HelpRequest?> = appResult {
        supabase.from("help_requests").select(columns) { filter { eq("id", id) } }.decodeSingleOrNull<HelpRequest>()
    }

    suspend fun create(values: JsonObject): Result<Long> = appResult {
        val body = buildJsonObject {
            values.forEach { (k, v) -> put(k, v) }
            put("user_id", supabase.uid())
        }
        supabase.from("help_requests").insert(body) { select(Columns.raw("id")) }.decodeSingle<IdOnly>().id
    }

    suspend fun setStatus(id: Long, status: String): Result<Unit> = appResult {
        supabase.from("help_requests").update(buildJsonObject { put("status", status) }) { filter { eq("id", id) } }
    }

    suspend fun delete(id: Long): Result<Unit> = appResult {
        supabase.from("help_requests").delete { filter { eq("id", id) } }
    }

    /** "Offer help": the DB trigger notifies the requester. */
    suspend fun respond(helpRequestId: Long, message: String): Result<Unit> = appResult {
        supabase.from("help_responses").insert(buildJsonObject {
            put("help_request_id", helpRequestId)
            put("responder_id", supabase.uid())
            put("message", message.trim())
        })
    }
}
