package com.neighborly.app.data.repository

import com.neighborly.app.core.util.appResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportRepo @Inject constructor(private val supabase: SupabaseClient) {
    suspend fun create(targetType: String, targetId: String, reason: String, details: String?): Result<Unit> = appResult {
        supabase.from("reports").insert(buildJsonObject {
            put("reporter_id", supabase.uid())
            put("target_type", targetType)
            put("target_id", targetId)
            put("reason", reason)
            put("details", details?.trim()?.ifBlank { null })
        })
    }
}
