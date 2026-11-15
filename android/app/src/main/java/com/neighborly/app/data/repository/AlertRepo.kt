package com.neighborly.app.data.repository

import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.EmergencyAlert
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
class AlertRepo @Inject constructor(private val supabase: SupabaseClient) {
    private val columns = Columns.raw("*, $AUTHOR, $NEIGHBORHOOD_NAME")

    suspend fun list(status: String? = "active"): Result<List<EmergencyAlert>> = appResult {
        supabase.from("emergency_alerts").select(columns) {
            filter { status?.let { eq("status", it) } }
            order("created_at", Order.DESCENDING)
            limit(50)
        }.decodeList<EmergencyAlert>()
    }

    suspend fun countActive(): Result<Int> = appResult {
        supabase.from("emergency_alerts").select(Columns.raw("id")) {
            filter { eq("status", "active") }
            limit(100)
        }.decodeList<IdOnly>().size
    }

    /** Raising an alert: the DB notifies the neighbourhood. */
    suspend fun create(values: JsonObject): Result<Long> = appResult {
        val body = buildJsonObject {
            values.forEach { (k, v) -> put(k, v) }
            put("created_by", supabase.uid())
        }
        supabase.from("emergency_alerts").insert(body) { select(Columns.raw("id")) }.decodeSingle<IdOnly>().id
    }

    suspend fun setStatus(id: Long, status: String): Result<Unit> = appResult {
        supabase.from("emergency_alerts").update(buildJsonObject { put("status", status) }) { filter { eq("id", id) } }
    }
}
