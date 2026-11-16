package com.neighborly.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neighborly.app.core.designsystem.NeighborlyTheme
import com.neighborly.app.feature.auth.LoginContent
import com.neighborly.app.feature.auth.LoginUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loginButtonEnablesAndSubmits() {
        var state by mutableStateOf(LoginUiState())
        var submitted = 0
        compose.setContent {
            NeighborlyTheme {
                LoginContent(
                    state = state,
                    onEmail = { state = state.copy(email = it) },
                    onPassword = { state = state.copy(password = it) },
                    onSubmit = { submitted++ },
                    onSignup = {},
                    onForgot = {},
                )
            }
        }
        compose.onNodeWithTag("login_submit").assertIsNotEnabled()
        compose.onNodeWithTag("login_email").performTextInput("rahim@neighborly.test")
        compose.onNodeWithTag("login_password").performTextInput("Neighbor@123")
        compose.onNodeWithTag("login_submit").assertIsEnabled().performClick()
        assertEquals(1, submitted)
    }

    @Test fun showsServerError() {
        compose.setContent {
            NeighborlyTheme {
                LoginContent(LoginUiState(email = "a@b.co", password = "x", error = "Wrong email or password"), {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("login_error").assertIsDisplayed()
    }
}
