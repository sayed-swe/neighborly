package com.neighborly.app.data.repository

import com.neighborly.app.core.util.Format
import com.neighborly.app.core.util.Geo
import com.neighborly.app.core.util.LatLng
import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.FeedPost
import com.neighborly.app.data.model.IdOnly
import com.neighborly.app.data.model.Reactor
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

data class FeedQuery(
    val neighborhoodId: Long? = null,
    val categoryId: Long? = null,
    val userId: String? = null,
    val search: String? = null,
    val near: LatLng? = null,
    val radiusKm: Int = 3,
)

data class NewPost(
    val body: String,
    val title: String? = null,
    val imageUrl: String? = null,
    val categoryId: Long? = null,
    val neighborhoodId: Long? = null,
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val postType: String = "post",
    val sharedPostId: Long? = null,
)

interface FeedSource {
    suspend fun feed(page: Int, query: FeedQuery): Result<List<FeedPost>>
    suspend fun setReaction(postId: Long, type: String?): Result<Unit>
}

@Singleton
class FeedRepo @Inject constructor(
    private val supabase: SupabaseClient,
    private val reactions: ReactionRepo,
) : FeedSource {

    /** Mirrors getFeed() in api.js: 10 per page, newest first. */
    override suspend fun feed(page: Int, query: FeedQuery): Result<List<FeedPost>> = appResult {
        val search = Format.cleanSearch(query.search)
        val box = query.near?.let { Geo.boundingBox(it, query.radiusKm.toDouble()) }
        supabase.from("feed_posts").select {
            filter {
                eq("status", "published")
                query.neighborhoodId?.let { eq("neighborhood_id", it) }
                query.categoryId?.let { eq("category_id", it) }
                query.userId?.let { eq("user_id", it) }
                if (search.isNotEmpty()) or {
                    ilike("body", ilikeAny(search))
                    ilike("title", ilikeAny(search))
                }
                if (box != null) {
                    gte("latitude", box.minLat); lte("latitude", box.maxLat)
                    gte("longitude", box.minLng); lte("longitude", box.maxLng)
                }
            }
            order("created_at", Order.DESCENDING)
            range(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE - 1)
        }.decodeList<FeedPost>()
    }

    suspend fun post(id: Long): Result<FeedPost?> = appResult {
        supabase.from("feed_posts").select { filter { eq("id", id) } }.decodeSingleOrNull<FeedPost>()
    }

    suspend fun create(p: NewPost): Result<Long> = appResult {
        supabase.from("posts").insert(p.toJson(supabase.uid())) { select(Columns.raw("id")) }.decodeSingle<IdOnly>().id
    }

    suspend fun update(id: Long, p: NewPost): Result<Unit> = appResult {
        val values = buildJsonObject {
            put("body", p.body)
            put("title", p.title)
            put("image_url", p.imageUrl)
            put("category_id", p.categoryId)
            put("location_name", p.locationName)
            put("latitude", p.latitude)
            put("longitude", p.longitude)
        }
        supabase.from("posts").update(values) { filter { eq("id", id) } }
    }

    suspend fun delete(id: Long): Result<Unit> = appResult {
        supabase.from("posts").delete { filter { eq("id", id) } }
    }

    /** A share is a post that points at the original. */
    suspend fun share(postId: Long, caption: String, neighborhoodId: Long?): Result<Long> =
        create(NewPost(body = caption, sharedPostId = postId, neighborhoodId = neighborhoodId))

    override suspend fun setReaction(postId: Long, type: String?): Result<Unit> =
        reactions.set(ReactionRepo.Target.Post(postId), type)

    suspend fun reactors(postId: Long): Result<List<Reactor>> = appResult {
        supabase.from("reactions")
            .select(Columns.raw("reaction_type, user:profiles(id, full_name, username, avatar_url)")) {
                filter { eq("post_id", postId) }
                order("created_at", Order.DESCENDING)
            }.decodeList<Reactor>()
    }

    companion object {
        const val PAGE_SIZE = 10L
    }
}

internal fun NewPost.toJson(uid: String): JsonObject = buildJsonObject {
    put("user_id", uid)
    put("body", body)
    title?.let { put("title", it) }
    imageUrl?.let { put("image_url", it) }
    categoryId?.let { put("category_id", it) }
    neighborhoodId?.let { put("neighborhood_id", it) }
    locationName?.let { put("location_name", it) }
    latitude?.let { put("latitude", it) }
    longitude?.let { put("longitude", it) }
    sharedPostId?.let { put("shared_post_id", it) }
    put("post_type", postType)
}
