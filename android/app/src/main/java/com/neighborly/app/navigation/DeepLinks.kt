package com.neighborly.app.navigation

import android.net.Uri

/**
 * Maps a web path stored in notifications.link (e.g. "/post/12", "/events?item=5") or a full
 * https://…/neighborly/post/12 URL to an app route. Returns null when there's nothing to open.
 */
fun webLinkToRoute(link: String?): Any? {
    if (link.isNullOrBlank()) return null
    val uri = Uri.parse(if (link.startsWith("/")) "https://x$link" else link)
    val segments = uri.pathSegments.dropWhile { it == "neighborly" }
    val item = uri.getQueryParameter("item")?.toLongOrNull()
    return when (segments.firstOrNull()) {
        "post" -> segments.getOrNull(1)?.toLongOrNull()?.let { PostRoute(it) }
        "events" -> item?.let { EventRoute(it) } ?: EventsRoute
        "marketplace" -> item?.let { ListingRoute(it) } ?: MarketRoute
        "lost-found" -> LostFoundRoute(item)
        "services" -> when {
            uri.getQueryParameter("tab") == "help" -> item?.let { HelpRoute(it) } ?: ServicesRoute(helpTab = true)
            item != null -> ServiceRoute(item)
            else -> ServicesRoute()
        }
        "alerts" -> AlertsRoute
        "messages" -> ChatsRoute
        "profile" -> segments.getOrNull(1)?.let { ProfileRoute(username = it) }
        "map" -> MapRoute
        else -> null
    }
}

private val postLink = Regex("""/post/(\d+)""")

/** Post id from a shared link inside a chat message, if any. */
fun postIdIn(text: String): Long? = postLink.find(text)?.groupValues?.get(1)?.toLongOrNull()
