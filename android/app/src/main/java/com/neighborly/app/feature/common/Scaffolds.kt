package com.neighborly.app.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.CollectMessages
import com.neighborly.app.core.designsystem.SectionTitle
import com.neighborly.app.data.model.CommentTarget
import com.neighborly.app.feature.post.CommentInputBar
import com.neighborly.app.feature.post.CommentsViewModel
import com.neighborly.app.feature.post.commentItems

data class MenuAction(val label: String, val icon: ImageVector? = null, val onClick: () -> Unit)

@Composable
fun OverflowMenu(actions: List<MenuAction>) {
    if (actions.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.cd_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { a ->
                DropdownMenuItem(
                    text = { Text(a.label) },
                    leadingIcon = a.icon?.let { { Icon(it, null) } },
                    onClick = { open = false; a.onClick() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: List<MenuAction> = emptyList()) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
        actions = { OverflowMenu(actions) },
    )
}

/**
 * Detail screen with an optional comment / Q&A thread at the bottom (comments for events and help;
 * questions for listings and lost & found).
 */
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    actions: List<MenuAction> = emptyList(),
    commentTarget: Pair<CommentTarget, Long>? = null,
    commentsTitle: Int = R.string.comments_title,
    commentPlaceholder: Int = R.string.comment_placeholder,
    onOpenProfile: (String) -> Unit = {},
    overlay: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val comments: CommentsViewModel? = commentTarget?.let { hiltViewModel(key = "comments-${it.first}-${it.second}") }
    if (comments != null && commentTarget != null) remember(commentTarget) { comments.bind(commentTarget.first, commentTarget.second) }
    val cs = comments?.state?.collectAsStateWithLifecycle()?.value
    comments?.let { CollectMessages(it.messages) }

    Scaffold(
        topBar = { BackTopBar(title, onBack, actions) },
        bottomBar = {
            if (comments != null && cs != null) {
                CommentInputBar(
                    cs, comments::onText, comments::send, comments::cancelReply, stringResource(commentPlaceholder),
                    onConfirmDelete = comments::confirmDelete, onDismissDelete = comments::dismissDelete,
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
                if (comments != null && cs != null) {
                    item { SectionTitle(stringResource(commentsTitle)) }
                    commentItems(
                        cs, comments.myId, comments.canModerate,
                        onLike = comments::toggleLike, onReply = comments::startReply, onDelete = comments::requestDelete,
                        onOpenProfile = onOpenProfile, onRetry = comments::load,
                    )
                }
            }
            overlay()
        }
    }
}

/** Create / edit form with a submit button at the end. */
@Composable
fun FormScaffold(
    title: String,
    onBack: () -> Unit,
    submitLabel: String,
    canSubmit: Boolean,
    busy: Boolean,
    error: String?,
    onSubmit: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(topBar = { BackTopBar(title, onBack) }) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onSubmit, enabled = canSubmit && !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(if (busy) stringResource(R.string.saving) else submitLabel)
            }
        }
    }
}
