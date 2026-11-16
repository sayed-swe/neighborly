package com.neighborly.app.navigation

import kotlinx.serialization.Serializable

/* ---------- signed-out graph ---------- */
@Serializable data object LoginRoute
@Serializable data object SignupRoute
@Serializable data object ForgotPasswordRoute

/* ---------- bottom tabs ---------- */
@Serializable data object HomeRoute
@Serializable data object MapRoute
@Serializable data object MarketRoute
@Serializable data object EventsRoute
@Serializable data object AlertsRoute

/* ---------- everything else ---------- */
@Serializable data object MenuRoute
@Serializable data object SearchRoute
@Serializable data object NotificationsRoute
@Serializable data object ChatsRoute
@Serializable data class ChatRoute(val conversationId: Long, val title: String = "")
@Serializable data class ProfileRoute(val userId: String? = null, val username: String? = null)
@Serializable data object EditProfileRoute
@Serializable data class PostRoute(val postId: Long)
@Serializable data class ComposerRoute(
    val editPostId: Long? = null,
    val withPhoto: Boolean = false,
    val withLocation: Boolean = false,
    val ask: Boolean = false,
)
@Serializable data class ListingRoute(val id: Long)
@Serializable data class ListingEditRoute(val id: Long? = null)
@Serializable data class LostFoundRoute(val itemId: Long? = null)
@Serializable data class LostFoundEditRoute(val id: Long? = null)
@Serializable data class EventRoute(val id: Long)
@Serializable data class EventEditRoute(val id: Long? = null)
@Serializable data class ServicesRoute(val helpTab: Boolean = false)
@Serializable data class ServiceRoute(val id: Long)
@Serializable data class ServiceEditRoute(val id: Long? = null)
@Serializable data class HelpRoute(val id: Long)
@Serializable data object HelpEditRoute
@Serializable data object AlertCreateRoute
@Serializable data object ModerationRoute
@Serializable data class ReportReviewRoute(val reportId: Long)
