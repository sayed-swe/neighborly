package com.neighborly.app.feature.post

import app.cash.turbine.test
import com.neighborly.app.core.util.AppError
import com.neighborly.app.data.model.FeedPost
import com.neighborly.app.data.repository.FeedSource
import com.neighborly.app.data.repository.FeedQuery
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostLogicTest {

    private fun post(id: Long, my: String? = null, count: Int = 0, top: List<String> = emptyList()) =
        FeedPost(id = id, userId = "u", createdAt = "2026-11-14T10:00:00Z", myReaction = my, reactionCount = count, topReactions = top)

    @Test fun `first reaction increments the count`() {
        val p = post(1).withReaction("like")
        assertEquals("like", p.myReaction)
        assertEquals(1, p.reactionCount)
        assertEquals(listOf("like"), p.topReactions)
    }

    @Test fun `changing reaction keeps the count`() {
        val p = post(1, my = "like", count = 4, top = listOf("like")).withReaction("love")
        assertEquals(4, p.reactionCount)
        assertTrue("love" in p.topReactions)
    }

    @Test fun `removing the only reaction clears the stack`() {
        val p = post(1, my = "like", count = 1, top = listOf("like")).withReaction(null)
        assertNull(p.myReaction)
        assertEquals(0, p.reactionCount)
        assertTrue(p.topReactions.isEmpty())
    }

    @Test fun `paged list loads, pages and stops at the end`() = runTest(UnconfinedTestDispatcher()) {
        val source = mockk<FeedSource>()
        val pages = mapOf(0 to (1L..10L).map { post(it) }, 1 to (11L..13L).map { post(it) })
        coEvery { source.feed(any(), any()) } answers { Result.success(pages[firstArg<Int>()].orEmpty()) }
        val paged = PagedPosts(backgroundScope, source) { source.feed(it, FeedQuery()) }

        paged.state.test {
            assertTrue(awaitItem().loading)
            paged.reload()
            var s = expectMostRecentItem()
            assertEquals(10, s.posts.size)
            assertFalse(s.endReached)
            paged.loadMore()
            s = expectMostRecentItem()
            assertEquals(13, s.posts.size)
            assertTrue(s.endReached)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `reaction is optimistic and rolls back on failure`() = runTest(UnconfinedTestDispatcher()) {
        val source = mockk<FeedSource>()
        coEvery { source.feed(any(), any()) } returns Result.success(listOf(post(1)))
        coEvery { source.setReaction(1, "like") } returns Result.failure(AppError.NotAllowed())
        val paged = PagedPosts(backgroundScope, source) { source.feed(it, FeedQuery()) }
        paged.reload()

        paged.messages.test {
            paged.react(1, "like")
            awaitItem() // the friendly error message
            cancelAndIgnoreRemainingEvents()
        }
        val after = paged.state.value.posts.single()
        assertNull("rolled back", after.myReaction)
        assertEquals(0, after.reactionCount)
        coVerify { source.setReaction(1, "like") }
    }

    @Test fun `remove and share bump update the list`() = runTest(UnconfinedTestDispatcher()) {
        val source = mockk<FeedSource>()
        coEvery { source.feed(any(), any()) } returns Result.success(listOf(post(1), post(2)))
        val paged = PagedPosts(backgroundScope, source) { source.feed(it, FeedQuery()) }
        paged.reload()
        paged.bumpShares(2)
        paged.remove(1)
        val s = paged.state.value
        assertEquals(listOf(2L), s.posts.map { it.id })
        assertEquals(1, s.posts.single().shareCount)
    }
}
