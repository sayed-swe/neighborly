package com.neighborly.app.core.supabase

import com.neighborly.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.Json
import javax.inject.Singleton

const val AUTH_SCHEME = "neighborly"
const val AUTH_HOST = "auth-callback"
const val AUTH_REDIRECT = "$AUTH_SCHEME://$AUTH_HOST"
const val AUTH_RECOVERY_REDIRECT = "$AUTH_REDIRECT?flow=recovery"

val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {
    @Provides
    @Singleton
    fun supabase(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        defaultSerializer = KotlinXSerializer(AppJson)
        install(Auth) {
            scheme = AUTH_SCHEME
            host = AUTH_HOST
            flowType = FlowType.PKCE
        }
        install(Postgrest)
        install(Storage)
        install(Realtime)
    }
}
