package com.neighborly.app.feature.post

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.neighborly.app.R
import com.neighborly.app.core.designsystem.ConfirmDialog
import com.neighborly.app.core.designsystem.LocalSnackbar
import com.neighborly.app.core.designsystem.PostCallbacks
import com.neighborly.app.data.model.FeedPost
import com.neighborly.app.data.model.ReportTarget
import com.neighborly.app.feature.common.ReportSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What a post list's ViewModel must offer for the shared card actions. */
interface PostListActions {
    fun react(post: FeedPost, type: String?)
    fun delete(postId: Long)
    fun onShared(postId: Long, toFeed: Boolean)
}

/** Navigation the card needs. */
data class PostNav(
    val openPost: (Long) -> Unit,
    val openProfile: (String) -> Unit,
    val editPost: (Long) -> Unit,
)

/**
 * Remembers sheet/dialog state for a post list and returns ready-made [PostCallbacks].
 * Call [Overlays] once in the screen to render share, reactors, report and delete UI.
 */
class PostOverlayController internal constructor(
    val callbacks: PostCallbacks,
    val render: @Composable () -> Unit,
)

@Composable
fun rememberPostOverlays(actions: PostListActions, nav: PostNav, scope: CoroutineScope): PostOverlayController {
    var sharing by remember { mutableStateOf<FeedPost?>(null) }
    var reactorsFor by remember { mutableStateOf<Long?>(null) }
    var reportFor by remember { mutableStateOf<Long?>(null) }
    var deleteFor by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val copied = stringResource(R.string.link_copied)
    val reported = stringResource(R.string.report_sent)
    val shared = stringResource(R.string.share_done)

    val callbacks = remember(actions, nav) {
        PostCallbacks(
            onOpen = nav.openPost,
            onOpenProfile = nav.openProfile,
            onReact = actions::react,
            onShowReactors = { reactorsFor = it },
            onComment = nav.openPost,
            onShare = { sharing = it },
            onCopyLink = { copyPostLink(context, it); scope.launch { snackbar.showSnackbar(copied) } },
            onEdit = nav.editPost,
            onDelete = { deleteFor = it },
            onReport = { reportFor = it },
        )
    }
    return PostOverlayController(callbacks) {
        sharing?.let { p ->
            ShareSheet(
                post = p,
                onDismiss = { sharing = null },
                onShared = { toFeed -> actions.onShared(p.sharedPostId ?: p.id, toFeed); scope.launch { snackbar.showSnackbar(shared) } },
                onCopied = { scope.launch { snackbar.showSnackbar(copied) } },
            )
        }
        reactorsFor?.let { id -> ReactorsSheet(id, onDismiss = { reactorsFor = null }, onOpenProfile = { reactorsFor = null; nav.openProfile(it) }) }
        reportFor?.let { id ->
            ReportSheet(ReportTarget.POST, id.toString(), onDismiss = { reportFor = null }, onSent = { scope.launch { snackbar.showSnackbar(reported) } })
        }
        deleteFor?.let { id ->
            ConfirmDialog(
                title = stringResource(R.string.post_delete_title),
                text = stringResource(R.string.confirm_delete_text),
                confirmLabel = stringResource(R.string.action_delete),
                onConfirm = { actions.delete(id) },
                onDismiss = { deleteFor = null },
            )
        }
    }
}
