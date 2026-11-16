package com.neighborly.app.data.repository

import com.neighborly.app.core.util.Format
import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.Profile
import com.neighborly.app.data.model.UserRef
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepo @Inject constructor(private val supabase: SupabaseClient) {

    private val withNeighborhood = Columns.raw("*, neighborhood:neighborhoods(*)")

    suspend fun getProfile(id: String): Result<Profile?> = appResult {
        supabase.from("profiles").select(withNeighborhood) { filter { eq("id", id) } }.decodeSingleOrNull<Profile>()
    }

    suspend fun getByUsername(username: String): Result<Profile?> = appResult {
        supabase.from("profiles").select(withNeighborhood) { filter { eq("username", username) } }
            .decodeSingleOrNull<Profile>()
    }

    /** RLS + the protect_profile_fields trigger stop users changing their own role/status. */
    suspend fun update(id: String, values: JsonObject): Result<Unit> = appResult {
        supabase.from("profiles").update(values) { filter { eq("id", id) } }
    }

    suspend fun searchPeople(q: String, limit: Long = 8): Result<List<UserRef>> = appResult {
        val term = Format.cleanSearch(q)
        supabase.from("profiles").select(Columns.raw("id, full_name, username, avatar_url, neighborhood:neighborhoods(name)")) {
            filter {
                eq("status", "active")
                if (term.isNotEmpty()) or {
                    ilike("full_name", ilikeAny(term))
                    ilike("username", ilikeAny(term))
                }
            }
            order("created_at", Order.DESCENDING)
            limit(limit)
        }.decodeList<UserRef>()
    }
}
