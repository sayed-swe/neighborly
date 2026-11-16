package com.neighborly.app.data.repository

import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.Category
import com.neighborly.app.data.model.Neighborhood
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

/** Lookup tables, cached for the process lifetime (they rarely change). */
@Singleton
class LookupRepo @Inject constructor(private val supabase: SupabaseClient) {
    private var neighborhoods: List<Neighborhood>? = null
    private var postCategories: List<Category>? = null
    private var serviceCategories: List<Category>? = null

    suspend fun neighborhoods(): Result<List<Neighborhood>> = appResult {
        neighborhoods ?: supabase.from("neighborhoods").select { order("name", Order.ASCENDING) }
            .decodeList<Neighborhood>().also { neighborhoods = it }
    }

    suspend fun postCategories(): Result<List<Category>> = appResult {
        postCategories ?: supabase.from("post_categories").select { order("id", Order.ASCENDING) }
            .decodeList<Category>().also { postCategories = it }
    }

    suspend fun serviceCategories(): Result<List<Category>> = appResult {
        serviceCategories ?: supabase.from("service_categories").select { order("name", Order.ASCENDING) }
            .decodeList<Category>().also { serviceCategories = it }
    }
}
