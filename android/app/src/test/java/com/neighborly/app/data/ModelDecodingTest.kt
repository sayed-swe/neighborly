package com.neighborly.app.data

import com.neighborly.app.core.supabase.AppJson
import com.neighborly.app.data.model.AdminStats
import com.neighborly.app.data.model.Comment
import com.neighborly.app.data.model.ConversationSummary
import com.neighborly.app.data.model.FeedPost
import com.neighborly.app.data.model.MapItem
import com.neighborly.app.data.repository.NewPost
import com.neighborly.app.data.repository.toJson
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** The DTOs must decode exactly what PostgREST returns for the shared views / RPCs. */
class ModelDecodingTest {

    @Test fun `feed_posts row with a shared post`() {
        val json = """
            {"id":12,"user_id":"22222222-2222-2222-2222-222222222222","neighborhood_id":1,"category_id":2,
             "post_type":"post","title":null,"body":"Look at this","image_url":null,"location_name":null,
             "latitude":null,"longitude":null,"shared_post_id":3,"status":"published",
             "created_at":"2026-11-14T10:00:00.123456+00:00","updated_at":"2026-11-14T10:00:00+00:00",
             "author_name":"Rahim Uddin","author_username":"rahim","author_avatar":null,"author_role":"resident",
             "neighborhood_name":"Ashulia","category_name":"Safety","reaction_count":3,"comment_count":1,
             "share_count":0,"my_reaction":"love","top_reactions":["like","sad"],
             "shared_post":{"id":3,"title":null,"body":"Original","image_url":null,"created_at":"2026-11-13T10:00:00+00:00",
               "user_id":"x","status":"published","author_name":"Karima","author_avatar":null,"author_username":"karima",
               "location_name":null},
             "some_new_column":"ignored"}
        """.trimIndent()
        val p = AppJson.decodeFromString(FeedPost.serializer(), json)
        assertEquals(12L, p.id)
        assertEquals("love", p.myReaction)
        assertEquals(listOf("like", "sad"), p.topReactions)
        assertEquals("Original", p.sharedPost?.body)
    }

    @Test fun `comment with embedded author and likes`() {
        val json = """{"id":5,"content":"Hi","parent_id":null,"created_at":"2026-11-14T10:00:00Z","user_id":"u1",
            "status":"visible","author":{"id":"u1","full_name":"Site Admin","username":"admin","avatar_url":null,"role":"admin"},
            "reactions":[{"user_id":"u2"}]}"""
        val c = AppJson.decodeFromString(Comment.serializer(), json)
        assertEquals("Site Admin", c.author?.fullName)
        assertEquals(1, c.reactions.size)
    }

    @Test fun `my_conversations unread_count bigint`() {
        val json = """{"conversation_id":1,"other_user_id":"u","other_name":"Nusrat","other_username":"nusrat",
            "other_avatar":null,"last_message":"Hi","last_sender_id":"u","last_message_at":"2026-11-14T10:00:00Z","unread_count":2}"""
        assertEquals(2, AppJson.decodeFromString(ConversationSummary.serializer(), json).unreadCount)
    }

    @Test fun `map_items and admin_stats`() {
        val m = AppJson.decodeFromString(MapItem.serializer(),
            """{"kind":"listing","item_id":"7","title":"Bike","subtitle":"sell","latitude":23.87,"longitude":90.32,"created_at":null}""")
        assertEquals("7", m.itemId)
        val s = AppJson.decodeFromString(AdminStats.serializer(),
            """{"users":6,"posts":7,"posts_by_day":[{"day":"2026-11-14","posts":2}],"users_by_neighborhood":[{"name":"Ashulia","users":4}]}""")
        assertEquals(6, s.users)
        assertEquals(2, s.postsByDay.first().posts)
    }

    @Test fun `new post insert body only sends set fields`() {
        val body = NewPost(body = "Hello", neighborhoodId = 1).toJson("me")
        assertEquals("me", body["user_id"]!!.jsonPrimitive.content)
        assertEquals("post", body["post_type"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("image_url"))
        assertNull(body["shared_post_id"])
    }
}
