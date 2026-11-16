package com.neighborly.app.feature.auth

import app.cash.turbine.test
import com.neighborly.app.MainDispatcherRule
import com.neighborly.app.R
import com.neighborly.app.core.util.AppError
import com.neighborly.app.data.model.Neighborhood
import com.neighborly.app.data.repository.AuthRepo
import com.neighborly.app.data.repository.LookupRepo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AuthViewModelsTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun `login shows busy then the friendly error`() = runTest {
        val auth = mockk<AuthRepo>()
        val gate = CompletableDeferred<Result<Unit>>()
        coEvery { auth.signIn(any(), any()) } coAnswers { gate.await() }
        val vm = LoginViewModel(auth)

        vm.state.test {
            assertEquals(LoginUiState(), awaitItem())
            vm.onEmail("rahim@neighborly.test")
            vm.onPassword("wrong")
            skipItems(2)
            vm.submit()
            assertTrue(awaitItem().busy)
            gate.complete(Result.failure(AppError.Server("Wrong email or password")))
            val done = awaitItem()
            assertFalse(done.busy)
            assertEquals("Wrong email or password", done.error)
        }
        coVerify { auth.signIn("rahim@neighborly.test", "wrong") }
    }

    @Test fun `login ignores empty fields`() = runTest {
        val auth = mockk<AuthRepo>(relaxed = true)
        val vm = LoginViewModel(auth)
        vm.submit()
        coVerify(exactly = 0) { auth.signIn(any(), any()) }
    }

    @Test fun `signup validates like the database`() = runTest {
        val lookups = mockk<LookupRepo>()
        coEvery { lookups.neighborhoods() } returns Result.success(listOf(Neighborhood(1, "Ashulia")))
        val vm = SignupViewModel(mockk(relaxed = true), lookups)
        val ok = vm.state.value.copy(
            fullName = "New Neighbor", username = "new.neighbor", neighborhood = Neighborhood(1, "Ashulia"),
            email = "new@neighborly.test", password = "longenough",
        )
        assertNull(vm.validate(ok))
        assertEquals(R.string.err_username, vm.validate(ok.copy(username = "ab")))
        assertEquals(R.string.err_username, vm.validate(ok.copy(username = "Bad Name!")))
        assertEquals(R.string.err_neighborhood, vm.validate(ok.copy(neighborhood = null)))
        assertEquals(R.string.err_email, vm.validate(ok.copy(email = "not-an-email")))
        assertEquals(R.string.err_password, vm.validate(ok.copy(password = "short")))
        assertEquals(1, vm.state.value.neighborhoods.size)
    }

    @Test fun `signup asks to check email when confirmation is required`() = runTest {
        val auth = mockk<AuthRepo>()
        coEvery { auth.signUp(any()) } returns Result.success(false)
        val lookups = mockk<LookupRepo>()
        coEvery { lookups.neighborhoods() } returns Result.success(emptyList())
        val vm = SignupViewModel(auth, lookups)
        vm.update {
            copy(fullName = "A B", username = "abc", neighborhood = Neighborhood(1, "Ashulia"), email = "a@b.co", password = "12345678")
        }
        vm.submit()
        assertTrue(vm.state.value.checkEmail)
        coVerify { auth.signUp(match { it.username == "abc" && it.neighborhoodId == 1L }) }
    }
}
