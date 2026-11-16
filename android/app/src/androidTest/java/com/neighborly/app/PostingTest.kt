package com.neighborly.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neighborly.app.core.designsystem.NeighborlyTheme
import com.neighborly.app.core.designsystem.PostCallbacks
import com.neighborly.app.core.designsystem.PostCard
import com.neighborly.app.data.model.FeedPost
import com.neighborly.app.data.model.Profile
import com.neighborly.app.feature.post.ComposerContent
import com.neighborly.app.feature.post.ComposerUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostingTest {
    @get:Rule val compose = createComposeRule()

    private val me = Profile(id = "me", fullName = "Rahim Uddin", username = "rahim")

    @Test fun composerEnablesPostOnceThereIsText() {
        var state by mutableStateOf(ComposerUiState())
        var submitted = false
        compose.setContent {
            NeighborlyTheme {
                ComposerContent(state, me, isAdmin = false, onBack = {}, onSubmit = { submitted = true },
                    onEdit = { t -> state = state.t() }, onUseMyLocation = {})
            }
        }
        compose.onNodeWithTag("composer_submit").assertIsNotEnabled()
        compose.onNodeWithTag("composer_body").performTextInput("Street light is fixed!")
        compose.onNodeWithTag("composer_submit").assertIsEnabled().performClick()
        assertEquals("Street light is fixed!", state.body)
        assertEquals(true, submitted)
    }

    @Test fun likeTapSendsLikeReaction() {
        val post = FeedPost(id = 7, userId = "other", createdAt = "2026-11-14T10:00:00Z", body = "Hello", authorName = "Karima")
        var reacted: String? = "unset"
        compose.setContent {
            NeighborlyTheme {
                PostCard(post, myId = "me", canModerate = false, callbacks = PostCallbacks(onReact = { _, t -> reacted = t }))
            }
        }
        compose.onNodeWithText("Like").performClick()
        assertEquals("like", reacted)
    }
}
