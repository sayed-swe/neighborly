package com.neighborly.app.data.repository

import com.neighborly.app.core.util.Format
import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.IdOnly
import com.neighborly.app.data.model.LostFoundItem
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
class LostFoundRepo @Inject constructor(private val supabase: SupabaseClient) {
    private val columns = Columns.raw("*, $AUTHOR, $NEIGHBORHOOD_NAME")

    suspend fun list(type: String? = null, status: String? = "open", search: String? = null): Result<List<LostFoundItem>> = appResult {
        val q = Format.cleanSearch(search)
        supabase.from("lost_found_items").select(columns) {
            filter {
                status?.let { eq("status", it) }
                type?.let { eq("report_type", it) }
                if (q.isNotEmpty()) or {
                    ilike("item_name", ilikeAny(q))
                    ilike("description", ilikeAny(q))
                    ilike("location", ilikeAny(q))
                }
            }
            order("created_at", Order.DESCENDING)
            limit(60)
        }.decodeList<LostFoundItem>()
    }

    suspend fun get(id: Long): Result<LostFoundItem?> = appResult {
        supabase.from("lost_found_items").select(columns) { filter { eq("id", id) } }.decodeSingleOrNull<LostFoundItem>()
    }

    suspend fun create(values: JsonObject): Result<Long> = appResult {
        val body = buildJsonObject {
            values.forEach { (k, v) -> put(k, v) }
            put("user_id", supabase.uid())
        }
        supabase.from("lost_found_items").insert(body) { select(Columns.raw("id")) }.decodeSingle<IdOnly>().id
    }

    suspend fun update(id: Long, values: JsonObject): Result<Unit> = appResult {
        supabase.from("lost_found_items").update(values) { filter { eq("id", id) } }
    }

    /** "Reunite": mark resolved. */
    suspend fun resolve(id: Long) = update(id, buildJsonObject { put("status", "resolved") })

    suspend fun delete(id: Long): Result<Unit> = appResult {
        supabase.from("lost_found_items").delete { filter { eq("id", id) } }
    }
}
