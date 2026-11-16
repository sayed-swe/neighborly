package com.neighborly.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.neighborly.app.core.designsystem.NeighborlyTheme
import com.neighborly.app.core.supabase.AUTH_HOST
import com.neighborly.app.core.supabase.AUTH_SCHEME
import com.neighborly.app.core.supabase.SessionManager
import com.neighborly.app.navigation.AppRoot
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var supabase: SupabaseClient
    @Inject lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAuthIntent(intent)
        setContent {
            NeighborlyTheme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    /** neighborly://auth-callback — email confirmation and password reset (PKCE code exchange). */
    private fun handleAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != AUTH_SCHEME || data.host != AUTH_HOST) return
        if (data.getQueryParameter("flow") == "recovery") session.markRecovery()
        supabase.handleDeeplinks(intent)
    }
}
