package com.neighborly.app.data.repository

import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.Comment
import com.neighborly.app.data.model.CommentTarget
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommentRepo @Inject constructor(
    private val supabase: SupabaseClient,
    private val reactions: ReactionRepo,
) {
    private val columns = Columns.raw("id, content, parent_id, created_at, user_id, status, $AUTHOR, reactions(user_id)")

    suspend fun list(target: CommentTarget, id: Long): Result<List<Comment>> = appResult {
        supabase.from("comments").select(columns) {
            filter { eq(target.column, id); eq("status", "visible") }
            order("created_at", Order.ASCENDING)
        }.decodeList<Comment>()
    }

    suspend fun add(target: CommentTarget, id: Long, content: String, parentId: Long?): Result<Comment> = appResult {
        supabase.from("comments").insert(buildJsonObject {
            put(target.column, id)
            put("content", content.trim())
            put("parent_id", parentId)
            put("user_id", supabase.uid())
        }) { select(columns) }.decodeSingle<Comment>()
    }

    suspend fun delete(id: Long): Result<Unit> = appResult {
        supabase.from("comments").delete { filter { eq("id", id) } }
    }

    /** Comments only support "like" in the web UI. */
    suspend fun setLiked(commentId: Long, liked: Boolean): Result<Unit> =
        reactions.set(ReactionRepo.Target.Comment(commentId), if (liked) "like" else null)
}
