package com.neighborly.app.core.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.neighborly.app.R
import com.neighborly.app.core.util.Format
import com.neighborly.app.data.model.FeedPost
import com.neighborly.app.data.model.Reaction
import com.neighborly.app.data.model.SharedPost

data class PostCallbacks(
    val onOpen: (Long) -> Unit = {},
    val onOpenProfile: (String) -> Unit = {},
    val onReact: (FeedPost, String?) -> Unit = { _, _ -> },
    val onShowReactors: (Long) -> Unit = {},
    val onComment: (Long) -> Unit = {},
    val onShare: (FeedPost) -> Unit = {},
    val onCopyLink: (Long) -> Unit = {},
    val onEdit: (Long) -> Unit = {},
    val onDelete: (Long) -> Unit = {},
    val onReport: (Long) -> Unit = {},
)

@Composable
fun PostCard(
    post: FeedPost,
    myId: String?,
    canModerate: Boolean,
    callbacks: PostCallbacks,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    NbCard(modifier) {
        PostHeader(post, myId, canModerate, callbacks)
        val official = post.postType == "announcement"
        if (official && !post.title.isNullOrBlank()) {
            Text(post.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp))
        }
        if (post.body.isNotBlank()) {
            Text(
                post.body,
                style = NbTextStyles.post,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 8,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clickable { callbacks.onOpen(post.id) }.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        post.locationName?.takeIf { it.isNotBlank() }?.let { LocationPill(it, Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
        if (!post.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = post.imageUrl,
                contentDescription = stringResource(R.string.cd_post_photo),
                contentScale = ContentScale.Crop,
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth().heightIn(max = 480.dp).aspectRatio(4f / 3f)
                    .clickable { callbacks.onOpen(post.id) },
            )
        }
        if (post.sharedPostId != null) {
            SharedPostBox(post.sharedPost, callbacks)
        }
        StatsRow(post, callbacks)
        HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        ActionRow(post, callbacks)
    }
}

@Composable
private fun PostHeader(post: FeedPost, myId: String?, canModerate: Boolean, callbacks: PostCallbacks) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(post.authorAvatar, post.authorName, 40.dp, Modifier.clickable { callbacks.onOpenProfile(post.userId) })
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    post.authorName, style = NbTextStyles.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).clickable { callbacks.onOpenProfile(post.userId) },
                )
                when (post.authorRole) {
                    "admin", "moderator" -> Pill(stringResource(R.string.badge_admin), PillTone.Red)
                    "service_provider" -> Pill(stringResource(R.string.badge_pro), PillTone.Green)
                }
            }
            val meta = listOfNotNull(Format.timeAgo(post.createdAt), post.neighborhoodName, post.categoryName).joinToString(" · ")
            MetaText(meta, maxLines = 1)
            if (post.postType == "announcement") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(Icons.Outlined.Campaign, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.badge_official), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, stringResource(R.string.cd_more)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_copy_link)) },
                    leadingIcon = { Icon(Icons.Outlined.Link, null) },
                    onClick = { menu = false; callbacks.onCopyLink(post.id) },
                )
                val mine = post.userId == myId
                if (mine) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = { menu = false; callbacks.onEdit(post.id) },
                    )
                }
                if (mine || canModerate) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { menu = false; callbacks.onDelete(post.id) },
                    )
                }
                if (!mine) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_report)) },
                        leadingIcon = { Icon(Icons.Outlined.Flag, null) },
                        onClick = { menu = false; callbacks.onReport(post.id) },
                    )
                }
            }
        }
    }
}

@Composable
fun LocationPill(text: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(50), modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Place, null, Modifier.size(14.dp), tint = LocalExtraColors.current.accent)
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SharedPostBox(shared: SharedPost?, callbacks: PostCallbacks) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.padding(12.dp).fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(enabled = shared != null) { shared?.let { callbacks.onOpen(it.id) } },
    ) {
        if (shared == null || shared.status != null && shared.status != "published") {
            Text(stringResource(R.string.post_shared_unavailable), style = MaterialTheme.typography.bodySmall,
                color = LocalExtraColors.current.secondaryText, modifier = Modifier.padding(12.dp))
            return@Column
        }
        if (!shared.imageUrl.isNullOrBlank()) {
            AsyncImage(shared.imageUrl, stringResource(R.string.cd_post_photo), contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        }
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(shared.authorAvatar, shared.authorName, 28.dp)
            Column(Modifier.padding(start = 8.dp)) {
                Text(shared.authorName.orEmpty(), style = NbTextStyles.name)
                MetaText(Format.timeAgo(shared.createdAt))
            }
        }
        val text = listOfNotNull(shared.title, shared.body).filter { it.isNotBlank() }.joinToString("\n")
        if (text.isNotBlank()) Text(text, style = NbTextStyles.post, maxLines = 5, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp))
    }
}

@Composable
private fun StatsRow(post: FeedPost, callbacks: PostCallbacks) {
    if (post.reactionCount == 0 && post.commentCount == 0 && post.shareCount == 0) {
        Spacer(Modifier.padding(4.dp)); return
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (post.reactionCount > 0) {
            Row(
                Modifier.clickable { callbacks.onShowReactors(post.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReactionStack(post.topReactions.ifEmpty { listOf("like") })
                MetaText(Format.compact(post.reactionCount), Modifier.padding(start = 6.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        if (post.commentCount > 0) {
            MetaText(pluralStringResource(R.plurals.post_comments, post.commentCount, Format.compact(post.commentCount)),
                Modifier.clickable { callbacks.onComment(post.id) })
        }
        if (post.shareCount > 0) {
            MetaText(pluralStringResource(R.plurals.post_shares, post.shareCount, Format.compact(post.shareCount)), Modifier.padding(start = 12.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionRow(post: FeedPost, callbacks: PostCallbacks) {
    var picker by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
        val mine = post.myReaction?.let { Reaction.of(it) }
        Box(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.post_like),
                        onLongClickLabel = stringResource(R.string.post_react),
                        onClick = { callbacks.onReact(post, if (post.myReaction == null) "like" else null) },
                        onLongClick = { picker = true },
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (mine == null) {
                    Icon(Icons.Outlined.ThumbUp, null, Modifier.size(20.dp), tint = LocalExtraColors.current.secondaryText)
                    ActionLabel(stringResource(R.string.post_like), LocalExtraColors.current.secondaryText)
                } else {
                    Text(mine.emoji, fontSize = 18.sp)
                    ActionLabel(mine.label, reactionColor(mine))
                }
            }
            if (picker) {
                ReactionPicker(onPick = { r -> picker = false; callbacks.onReact(post, r.type) }, onDismiss = { picker = false })
            }
        }
        ActionButton(Icons.Outlined.ChatBubbleOutline, stringResource(R.string.post_comment)) { callbacks.onComment(post.id) }
        ActionButton(Icons.AutoMirrored.Outlined.Reply, stringResource(R.string.post_share)) { callbacks.onShare(post) }
    }
}

@Composable
private fun reactionColor(r: Reaction): Color = when (r) {
    Reaction.LIKE -> MaterialTheme.colorScheme.primary
    Reaction.LOVE -> Color(0xFFE0245E)
    Reaction.ANGRY -> Color(0xFFE9710F)
    else -> Color(0xFFF7B125)
}

@Composable
private fun ActionLabel(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.padding(start = 6.dp))
}

@Composable
private fun RowScope.ActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = LocalExtraColors.current.secondaryText)
        ActionLabel(label, LocalExtraColors.current.secondaryText)
    }
}
