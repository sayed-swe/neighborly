package com.neighborly.app.data.model

/** Enum values exactly as stored in Postgres (sent as strings). */
object Enums {
    val LISTING_TYPES = listOf("sell", "buy", "exchange", "free")
    val LISTING_STATUSES = listOf("available", "reserved", "sold", "closed")
    val CONDITIONS = listOf("new", "like_new", "used", "for_parts")
    val LISTING_CATEGORIES = listOf(
        "Electronics", "Furniture", "Vehicles", "Books", "Clothing", "Home & Kitchen", "Kids", "Sports", "Other",
    )
    val LF_TYPES = listOf("lost", "found")
    val HELP_TYPES = listOf("medical", "grocery", "repair", "transport", "childcare", "elderly_care", "other")
    val SEVERITIES = listOf("low", "medium", "high", "critical")
    val REPORT_REASONS = listOf("spam", "abuse", "inappropriate", "scam", "false_info", "harassment", "other")
}

enum class Reaction(val type: String, val emoji: String, val label: String) {
    LIKE("like", "👍", "Like"),
    LOVE("love", "❤️", "Love"),
    CARE("care", "🥰", "Care"),
    HAHA("haha", "😆", "Haha"),
    WOW("wow", "😮", "Wow"),
    SAD("sad", "😢", "Sad"),
    ANGRY("angry", "😡", "Angry");

    companion object {
        fun of(type: String?): Reaction = entries.firstOrNull { it.type == type } ?: LIKE
    }
}

/** Kinds in the `map_items` view, with the web's marker colours. */
enum class MapKind(val key: String, val color: Long) {
    POST("post", 0xFF2F5D45),
    ALERT("alert", 0xFFC2412D),
    HELP("help", 0xFFE0663A),
    LOST_FOUND("lost_found", 0xFF6B4FA0),
    LISTING("listing", 0xFF2F6DB5),
    EVENT("event", 0xFFB7791F),
    SERVICE("service", 0xFF3D7457);

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: POST
    }
}

/** Comment / reaction targets — the column name used in the row. */
enum class CommentTarget(val column: String) {
    POST("post_id"), EVENT("event_id"), HELP("help_request_id"), LISTING("listing_id"), LOST_FOUND("lost_found_id")
}

enum class ReportTarget(val value: String) {
    POST("post"), COMMENT("comment"), EVENT("event"), HELP_REQUEST("help_request"),
    SERVICE("service"), LISTING("listing"), LOST_FOUND("lost_found"), USER("user")
}
