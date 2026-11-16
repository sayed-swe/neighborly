package com.neighborly.app.data.repository

import com.neighborly.app.core.util.Format
import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.IdOnly
import com.neighborly.app.data.model.Service
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
class ServiceRepo @Inject constructor(private val supabase: SupabaseClient) {
    private val columns = Columns.raw(
        "*, provider:profiles(id, full_name, username, avatar_url), category:service_categories(name, icon), $NEIGHBORHOOD_NAME",
    )

    suspend fun list(categoryId: Long? = null, search: String? = null): Result<List<Service>> = appResult {
        val q = Format.cleanSearch(search)
        supabase.from("services").select(columns) {
            filter {
                eq("is_active", true)
                categoryId?.let { eq("service_category_id", it) }
                if (q.isNotEmpty()) or {
                    ilike("service_name", ilikeAny(q))
                    ilike("description", ilikeAny(q))
                }
            }
            order("is_verified", Order.DESCENDING)
            order("created_at", Order.DESCENDING)
            limit(60)
        }.decodeList<Service>()
    }

    suspend fun get(id: Long): Result<Service?> = appResult {
        supabase.from("services").select(columns) { filter { eq("id", id) } }.decodeSingleOrNull<Service>()
    }

    /** is_verified is never sent: only admins may set it (enforced by a trigger). */
    suspend fun create(values: JsonObject): Result<Long> = appResult {
        val body = buildJsonObject {
            values.forEach { (k, v) -> if (k != "is_verified") put(k, v) }
            put("provider_id", supabase.uid())
        }
        supabase.from("services").insert(body) { select(Columns.raw("id")) }.decodeSingle<IdOnly>().id
    }

    suspend fun update(id: Long, values: JsonObject): Result<Unit> = appResult {
        supabase.from("services").update(JsonObject(values - "is_verified")) { filter { eq("id", id) } }
    }

    suspend fun delete(id: Long): Result<Unit> = appResult {
        supabase.from("services").delete { filter { eq("id", id) } }
    }
}
