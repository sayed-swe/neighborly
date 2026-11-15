package com.neighborly.app.data.repository

import com.neighborly.app.core.util.Format
import com.neighborly.app.core.util.appResult
import com.neighborly.app.data.model.AdminStats
import com.neighborly.app.data.model.Report
import com.neighborly.app.data.model.ReviewTarget
import com.neighborly.app.data.model.UserRef
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Where each reported thing lives and how to hide it — same table as AdminReports.jsx. */
enum class ModTarget(
    val type: String,
    val table: String,
    val ownerColumn: String,
    val hide: JsonObject?,
    val labelColumn: String,
) {
    POST("post", "posts", "user_id", buildJsonObject { put("status", "hidden") }, "body"),
    COMMENT("comment", "comments", "user_id", buildJsonObject { put("status", "hidden") }, "content"),
    LISTING("listing", "marketplace_listings", "user_id", buildJsonObject { put("status", "closed") }, "title"),
    LOST_FOUND("lost_found", "lost_found_items", "user_id", buildJsonObject { put("status", "resolved") }, "item_name"),
    EVENT("event", "events", "created_by", buildJsonObject { put("status", "cancelled") }, "title"),
    HELP("help_request", "help_requests", "user_id", buildJsonObject { put("status", "closed") }, "title"),
    SERVICE("service", "services", "provider_id", buildJsonObject { put("is_active", false) }, "service_name"),
    USER("user", "profiles", "id", null, "full_name");

    companion object {
        fun of(type: String) = entries.firstOrNull { it.type == type } ?: POST
    }
}

enum class ModAction(val value: String) {
    NONE("none"), WARN("warning_sent"), REMOVE("content_removed"), SUSPEND("user_suspended"), BAN("user_banned")
}

@Singleton
class ModerationRepo @Inject constructor(private val supabase: SupabaseClient) {

    suspend fun reports(open: Boolean): Result<List<Report>> = appResult {
        supabase.from("reports").select(
            Columns.raw(
                "*, reporter:profiles!reports_reporter_id_fkey(id, full_name, username, avatar_url), " +
                    "reviewer:profiles!reports_reviewed_by_fkey(full_name)",
            ),
        ) {
            filter {
                isIn("status", if (open) listOf("pending", "under_review") else listOf("resolved", "dismissed"))
            }
            order("created_at", Order.DESCENDING)
            limit(200)
        }.decodeList<Report>()
    }

    suspend fun report(id: Long): Result<Report?> = appResult {
        supabase.from("reports").select(
            Columns.raw("*, reporter:profiles!reports_reporter_id_fkey(id, full_name, username, avatar_url), reviewer:profiles!reports_reviewed_by_fkey(full_name)"),
        ) { filter { eq("id", id) } }.decodeSingleOrNull<Report>()
    }

    suspend fun loadTarget(report: Report): Result<ReviewTarget> = appResult {
        val t = ModTarget.of(report.targetType)
        val row = supabase.from(t.table).select { filter { eq("id", report.targetId) } }.decodeSingleOrNull<JsonObject>()
        val ownerId = row?.get(t.ownerColumn)?.jsonPrimitive?.contentOrNull
        val owner = ownerId?.let {
            supabase.from("profiles").select(Columns.raw("id, full_name, username, avatar_url, status")) {
                filter { eq("id", it) }
            }.decodeSingleOrNull<UserRef>()
        }
        ReviewTarget(row, owner)
    }

    /**
     * Closes a report like the web review modal: optionally hides the content, suspends/bans the owner,
     * or sends a warning (a `system` notification), then records the decision on the report.
     */
    suspend fun close(report: Report, target: ReviewTarget, action: ModAction, note: String): Result<Unit> = appResult {
        val t = ModTarget.of(report.targetType)
        val me = supabase.uid()
        val owner = target.owner
        when (action) {
            ModAction.REMOVE -> if (t.hide != null && target.row != null) {
                supabase.from(t.table).update(t.hide) { filter { eq("id", report.targetId) } }
            }
            ModAction.SUSPEND, ModAction.BAN -> if (owner != null) {
                supabase.from("profiles").update(buildJsonObject {
                    put("status", if (action == ModAction.BAN) "banned" else "suspended")
                }) { filter { eq("id", owner.id) } }
            }
            ModAction.WARN -> if (owner != null) {
                supabase.from("notifications").insert(buildJsonObject {
                    put("user_id", owner.id)
                    put("actor_id", me)
                    put("type", "system")
                    put(
                        "message",
                        "⚠️ A moderator reviewed a report about your ${Format.labelize(report.targetType).lowercase()} " +
                            "(${Format.labelize(report.reason)}). Please follow the community guidelines.",
                    )
                })
            }
            ModAction.NONE -> Unit
        }
        supabase.from("reports").update(buildJsonObject {
            put("status", if (action == ModAction.NONE) "dismissed" else "resolved")
            put("action_taken", action.value)
            put("admin_note", JsonPrimitive(note.trim().ifBlank { null }))
            put("reviewed_by", me)
            put("reviewed_at", Instant.now().toString())
        }) { filter { eq("id", report.id) } }
    }

    suspend fun stats(): Result<AdminStats> = appResult {
        supabase.postgrest.rpc("admin_stats").decodeAs<AdminStats>()
    }
}

fun ReviewTarget.label(type: String): String {
    val t = ModTarget.of(type)
    val r = row ?: return ""
    fun s(k: String) = r[k]?.jsonPrimitive?.contentOrNull
    return when (t) {
        ModTarget.POST -> s("title")?.takeIf { it.isNotBlank() } ?: s("body").orEmpty()
        ModTarget.USER -> "${s("full_name").orEmpty()} (@${s("username").orEmpty()})"
        else -> s(t.labelColumn).orEmpty()
    }
}

fun ReviewTarget.field(key: String): String? = row?.get(key)?.let { (it as? JsonPrimitive)?.contentOrNull }
