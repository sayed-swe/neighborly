package com.neighborly.app.data.repository

import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.MapItem
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapRepo @Inject constructor(private val supabase: SupabaseClient) {
    suspend fun items(): Result<List<MapItem>> = appResult {
        supabase.from("map_items").select {
            order("created_at", Order.DESCENDING)
            limit(500)
        }.decodeList<MapItem>()
    }
}
