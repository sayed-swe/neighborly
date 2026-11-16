package com.neighborly.app.data.repository

import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.Event
import com.neighborly.app.data.model.IdOnly
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepo @Inject constructor(private val supabase: SupabaseClient) {
    // events → profiles is ambiguous (created_by FK, and many-to-many through event_participants),
    // so the constraint is named explicitly.
    private val creator = "creator:profiles!events_created_by_fkey(id, full_name, username, avatar_url)"
    private val listColumns = Columns.raw("*, $creator, event_participants(user_id, rsvp), $NEIGHBORHOOD_NAME")
    private val detailColumns = Columns.raw(
        "*, $creator, " +
            "event_participants(user_id, rsvp, user:profiles(id, full_name, avatar_url)), $NEIGHBORHOOD_NAME",
    )

    /** Same 6-hour grace window as the web: events that started < 6 h ago still count as upcoming. */
    suspend fun list(upcoming: Boolean = true): Result<List<Event>> = appResult {
        val cutoff = Instant.now().minus(6, ChronoUnit.HOURS).toString()
        supabase.from("events").select(listColumns) {
            filter {
                if (upcoming) {
                    gte("starts_at", cutoff); neq("status", "cancelled")
                } else {
                    lt("starts_at", cutoff)
                }
            }
            order("starts_at", if (upcoming) Order.ASCENDING else Order.DESCENDING)
            limit(50)
        }.decodeList<Event>()
    }

    suspend fun get(id: Long): Result<Event?> = appResult {
        supabase.from("events").select(detailColumns) { filter { eq("id", id) } }.decodeSingleOrNull<Event>()
    }

    suspend fun create(values: JsonObject): Result<Long> = appResult {
        val body = buildJsonObject {
            values.forEach { (k, v) -> put(k, v) }
            put("created_by", supabase.uid())
        }
        supabase.from("events").insert(body) { select(Columns.raw("id")) }.decodeSingle<IdOnly>().id
    }

    suspend fun update(id: Long, values: JsonObject): Result<Unit> = appResult {
        supabase.from("events").update(values) { filter { eq("id", id) } }
    }

    suspend fun cancel(id: Long) = update(id, buildJsonObject { put("status", "cancelled") })

    suspend fun delete(id: Long): Result<Unit> = appResult {
        supabase.from("events").delete { filter { eq("id", id) } }
    }

    /** `rsvp == null` removes the RSVP; otherwise upserts going / interested. */
    suspend fun setRsvp(eventId: Long, rsvp: String?): Result<Unit> = appResult {
        val me = supabase.uid()
        val table = supabase.from("event_participants")
        if (rsvp == null) {
            table.delete { filter { eq("event_id", eventId); eq("user_id", me) } }
        } else {
            table.upsert(buildJsonObject {
                put("event_id", eventId); put("user_id", me); put("rsvp", rsvp)
            }) { onConflict = "event_id,user_id" }
        }
    }
}
