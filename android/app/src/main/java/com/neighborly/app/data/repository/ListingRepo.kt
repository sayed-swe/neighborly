package com.neighborly.app.data.repository

import com.neighborly.app.core.util.Format
import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.Listing
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

data class ListingFilter(
    val type: String? = null,
    val category: String? = null,
    val search: String? = null,
    val status: String? = "available",
    val userId: String? = null,
)

@Singleton
class ListingRepo @Inject constructor(private val supabase: SupabaseClient) {
    private val columns = Columns.raw("*, $AUTHOR, $NEIGHBORHOOD_NAME")

    suspend fun list(f: ListingFilter = ListingFilter()): Result<List<Listing>> = appResult {
        val search = Format.cleanSearch(f.search)
        supabase.from("marketplace_listings").select(columns) {
            filter {
                f.status?.let { eq("status", it) }
                f.type?.let { eq("listing_type", it) }
                f.category?.let { eq("category", it) }
                f.userId?.let { eq("user_id", it) }
                if (search.isNotEmpty()) or {
                    ilike("title", ilikeAny(search))
                    ilike("description", ilikeAny(search))
                }
            }
            order("created_at", Order.DESCENDING)
            limit(60)
        }.decodeList<Listing>()
    }

    suspend fun get(id: Long): Result<Listing?> = appResult {
        supabase.from("marketplace_listings").select(columns) { filter { eq("id", id) } }.decodeSingleOrNull<Listing>()
    }

    suspend fun create(values: JsonObject): Result<Long> = appResult {
        val body = buildJsonObject {
            values.forEach { (k, v) -> put(k, v) }
            put("user_id", supabase.uid())
        }
        supabase.from("marketplace_listings").insert(body) { select(Columns.raw("id")) }
            .decodeSingle<com.neighborly.app.data.model.IdOnly>().id
    }

    suspend fun update(id: Long, values: JsonObject): Result<Unit> = appResult {
        supabase.from("marketplace_listings").update(values) { filter { eq("id", id) } }
    }

    suspend fun setStatus(id: Long, status: String) = update(id, buildJsonObject { put("status", status) })

    suspend fun delete(id: Long): Result<Unit> = appResult {
        supabase.from("marketplace_listings").delete { filter { eq("id", id) } }
    }
}
