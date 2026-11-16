package com.neighborly.app.core.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ErrorsTest {
    @Test fun `network failures become Offline`() {
        assertTrue(IOException("timeout").toAppError() is AppError.Offline)
    }

    @Test fun `AppError passes through unchanged`() {
        val e = AppError.Validation("x")
        assertEquals(e, e.toAppError())
    }

    @Test fun `appResult wraps failures`() = runTest {
        val r = appResult<Int> { throw IOException("down") }
        assertTrue(r.exceptionOrNull() is AppError.Offline)
        assertEquals(3, appResult { 3 }.getOrNull())
    }

    @Test(expected = CancellationException::class)
    fun `appResult does not swallow cancellation`() = runTest {
        appResult<Unit> { throw CancellationException("cancelled") }
    }
}
