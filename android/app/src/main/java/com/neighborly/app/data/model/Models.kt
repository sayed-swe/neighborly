package com.neighborly.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/* ---------- lookups ---------- */

@Serializable
data class Neighborhood(
    val id: Long,
    val name: String,
    val area: String? = null,
    val city: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class Category(val id: Long, val name: String, val icon: String? = null)

@Serializable
data class NameRef(val name: String? = null, val icon: String? = null)

/** Embedded `author:profiles(id, full_name, username, avatar_url, role)` and friends. */
@Serializable
data class UserRef(
    val id: String,
    @SerialName("full_name") val fullName: String = "",
    val username: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val role: String? = null,
    val status: String? = null,
    val neighborhood: NameRef? = null,
)

/* ---------- profiles ---------- */

@Serializable
data class Profile(
    val id: String,
    @SerialName("full_name") val fullName: String,
    val username: String,
    val phone: String? = null,
    val role: String = "resident",
    val status: String = "active",
    @SerialName("neighborhood_id") val neighborhoodId: Long? = null,
    val address: String? = null,
    val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val neighborhood: Neighborhood? = null,
) {
    val isModerator: Boolean get() = role == "moderator" || role == "admin"
    val isAdmin: Boolean get() = role == "admin"
    val isActive: Boolean get() = status == "active"
}

/* ---------- feed ---------- */

@Serializable
data class SharedPost(
    val id: Long,
    val title: String? = null,
    val body: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val status: String? = null,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("author_avatar") val authorAvatar: String? = null,
    @SerialName("author_username") val authorUsername: String? = null,
    @SerialName("location_name") val locationName: String? = null,
)

@Serializable
data class FeedPost(
    val id: Long,
    @SerialName("user_id") val userId: String,
    @SerialName("neighborhood_id") val neighborhoodId: Long? = null,
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("post_type") val postType: String = "post",
    val title: String? = null,
    val body: String = "",
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("location_name") val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("shared_post_id") val sharedPostId: Long? = null,
    val status: String = "published",
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("author_name") val authorName: String = "",
    @SerialName("author_username") val authorUsername: String = "",
    @SerialName("author_avatar") val authorAvatar: String? = null,
    @SerialName("author_role") val authorRole: String = "resident",
    @SerialName("neighborhood_name") val neighborhoodName: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("reaction_count") val reactionCount: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("share_count") val shareCount: Int = 0,
    @SerialName("my_reaction") val myReaction: String? = null,
    @SerialName("top_reactions") val topReactions: List<String> = emptyList(),
    @SerialName("shared_post") val sharedPost: SharedPost? = null,
)

@Serializable
data class IdOnly(val id: Long)

@Serializable
data class UserIdOnly(@SerialName("user_id") val userId: String)

@Serializable
data class Reactor(
    @SerialName("reaction_type") val reactionType: String,
    val user: UserRef? = null,
)

@Serializable
data class Comment(
    val id: Long,
    val content: String,
    @SerialName("parent_id") val parentId: Long? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("user_id") val userId: String,
    val status: String = "visible",
    val author: UserRef? = null,
    val reactions: List<UserIdOnly> = emptyList(),
)

/* ---------- marketplace ---------- */

@Serializable
data class Listing(
    val id: Long,
    @SerialName("user_id") val userId: String,
    @SerialName("neighborhood_id") val neighborhoodId: Long? = null,
    @SerialName("listing_type") val listingType: String = "sell",
    val title: String,
    val description: String = "",
    val category: String? = null,
    @SerialName("item_condition") val itemCondition: String? = null,
    val price: Double? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("contact_info") val contactInfo: String? = null,
    @SerialName("location_name") val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val status: String = "available",
    @SerialName("created_at") val createdAt: String,
    val author: UserRef? = null,
    val neighborhood: NameRef? = null,
)

/* ---------- lost & found ---------- */

@Serializable
data class LostFoundItem(
    val id: Long,
    @SerialName("user_id") val userId: String,
    @SerialName("neighborhood_id") val neighborhoodId: Long? = null,
    @SerialName("report_type") val reportType: String,
    @SerialName("item_name") val itemName: String,
    val description: String = "",
    val location: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("date_occurred") val dateOccurred: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("contact_info") val contactInfo: String? = null,
    val status: String = "open",
    @SerialName("created_at") val createdAt: String,
    val author: UserRef? = null,
    val neighborhood: NameRef? = null,
)

/* ---------- events ---------- */

@Serializable
data class Participant(
    @SerialName("user_id") val userId: String,
    val rsvp: String,
    val user: UserRef? = null,
)

@Serializable
data class Event(
    val id: Long,
    @SerialName("created_by") val createdBy: String,
    @SerialName("neighborhood_id") val neighborhoodId: Long? = null,
    val title: String,
    val description: String = "",
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String? = null,
    val location: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("max_participants") val maxParticipants: Int? = null,
    val status: String = "upcoming",
    @SerialName("created_at") val createdAt: String? = null,
    val creator: UserRef? = null,
    @SerialName("event_participants") val participants: List<Participant> = emptyList(),
    val neighborhood: NameRef? = null,
) {
    val goingCount get() = participants.count { it.rsvp == "going" }
    val interestedCount get() = participants.count { it.rsvp == "interested" }
}

/* ---------- services & help ---------- */

@Serializable
data class Service(
    val id: Long,
    @SerialName("provider_id") val providerId: String,
    @SerialName("neighborhood_id") val neighborhoodId: Long? = null,
    @SerialName("service_category_id") val serviceCategoryId: Long,
    @SerialName("service_name") val serviceName: String,
    val description: String = "",
    @SerialName("contact_phone") val contactPhone: String = "",
    @SerialName("contact_email") val contactEmail: String? = null,
    @SerialName("price_info") val priceInfo: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    val provider: UserRef? = null,
    val category: NameRef? = null,
    val neighborhood: NameRef? = null,
)

@Serializable
data class HelpResponse(
    val id: Long,
    val message: String,
    @SerialName("created_at") val createdAt: String,
    val responder: UserRef? = null,
)

@Serializable
data class HelpRequest(
    val id: Long,
    @SerialName("user_id") val userId: String,
    @SerialName("neighborhood_id") val neighborhoodId: Long? = null,
    @SerialName("help_type") val helpType: String,
    val title: String,
    val details: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val status: String = "open",
    @SerialName("created_at") val createdAt: String,
    val author: UserRef? = null,
    val neighborhood: NameRef? = null,
    @SerialName("help_responses") val responses: List<HelpResponse> = emptyList(),
)

/* ---------- alerts & notifications ---------- */

@Serializable
data class EmergencyAlert(
    val id: Long,
    @SerialName("created_by") val createdBy: String,
    @SerialName("neighborhood_id") val neighborhoodId: Long? = null,
    val title: String,
    val message: String = "",
    val severity: String = "medium",
    @SerialName("location_name") val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val status: String = "active",
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    val author: UserRef? = null,
    val neighborhood: NameRef? = null,
)

@Serializable
data class AppNotification(
    val id: Long,
    @SerialName("user_id") val userId: String,
    @SerialName("actor_id") val actorId: String? = null,
    val type: String,
    val message: String,
    val link: String? = null,
    @SerialName("reference_id") val referenceId: Long? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    val actor: UserRef? = null,
)

/* ---------- messages ---------- */

@Serializable
data class ConversationSummary(
    @SerialName("conversation_id") val conversationId: Long,
    @SerialName("other_user_id") val otherUserId: String,
    @SerialName("other_name") val otherName: String = "",
    @SerialName("other_username") val otherUsername: String = "",
    @SerialName("other_avatar") val otherAvatar: String? = null,
    @SerialName("last_message") val lastMessage: String? = null,
    @SerialName("last_sender_id") val lastSenderId: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("unread_count") val unreadCount: Int = 0,
)

@Serializable
data class Message(
    val id: Long,
    @SerialName("conversation_id") val conversationId: Long,
    @SerialName("sender_id") val senderId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ConversationMember(
    @SerialName("user_id") val userId: String,
    val user: UserRef? = null,
)

/* ---------- map ---------- */

@Serializable
data class MapItem(
    val kind: String,
    @SerialName("item_id") val itemId: String,
    val title: String? = null,
    val subtitle: String? = null,
    val latitude: Double,
    val longitude: Double,
    @SerialName("created_at") val createdAt: String? = null,
)

/* ---------- moderation ---------- */

@Serializable
data class Report(
    val id: Long,
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("target_type") val targetType: String,
    @SerialName("target_id") val targetId: String,
    val reason: String,
    val details: String? = null,
    val status: String = "pending",
    @SerialName("reviewed_by") val reviewedBy: String? = null,
    @SerialName("action_taken") val actionTaken: String = "none",
    @SerialName("admin_note") val adminNote: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    val reporter: UserRef? = null,
    val reviewer: NameOnly? = null,
)

@Serializable
data class NameOnly(@SerialName("full_name") val fullName: String? = null)

@Serializable
data class DayCount(val day: String, val posts: Int? = null, val users: Int? = null)

@Serializable
data class NeighborhoodCount(val name: String, val users: Int)

@Serializable
data class AdminStats(
    val users: Int = 0,
    @SerialName("active_users") val activeUsers: Int = 0,
    @SerialName("banned_users") val bannedUsers: Int = 0,
    val posts: Int = 0,
    @SerialName("hidden_posts") val hiddenPosts: Int = 0,
    val comments: Int = 0,
    val reactions: Int = 0,
    val shares: Int = 0,
    val listings: Int = 0,
    @SerialName("lost_found") val lostFound: Int = 0,
    val events: Int = 0,
    val services: Int = 0,
    @SerialName("help_requests") val helpRequests: Int = 0,
    @SerialName("alerts_active") val alertsActive: Int = 0,
    val messages: Int = 0,
    @SerialName("reports_pending") val reportsPending: Int = 0,
    val neighborhoods: Int = 0,
    @SerialName("posts_by_day") val postsByDay: List<DayCount> = emptyList(),
    @SerialName("signups_by_day") val signupsByDay: List<DayCount> = emptyList(),
    @SerialName("users_by_neighborhood") val usersByNeighborhood: List<NeighborhoodCount> = emptyList(),
)

/** A moderated item as loaded for review: raw row + owner. */
data class ReviewTarget(val row: JsonObject?, val owner: UserRef?)
