package com.neighborly.app.data.repository

import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.IdOnly
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** Port of setReaction() in api.js. One reaction per user per target. */
@Singleton
class ReactionRepo @Inject constructor(private val supabase: SupabaseClient) {

    sealed class Target(val column: String, val id: Long) {
        class Post(id: Long) : Target("post_id", id)
        class Comment(id: Long) : Target("comment_id", id)
        class Event(id: Long) : Target("event_id", id)
    }

    /** `type == null` removes the user's reaction; otherwise updates the existing row or inserts one. */
    suspend fun set(target: Target, type: String?): Result<Unit> = appResult {
        val me = supabase.uid()
        val table = supabase.from("reactions")
        if (type == null) {
            table.delete { filter { eq("user_id", me); eq(target.column, target.id) } }
            return@appResult
        }
        val existing = table.select(Columns.raw("id")) {
            filter { eq("user_id", me); eq(target.column, target.id) }
        }.decodeSingleOrNull<IdOnly>()
        if (existing != null) {
            table.update(buildJsonObject { put("reaction_type", type) }) { filter { eq("id", existing.id) } }
        } else {
            table.insert(buildJsonObject {
                put("user_id", me)
                put(target.column, target.id)
                put("reaction_type", type)
            })
        }
    }
}
