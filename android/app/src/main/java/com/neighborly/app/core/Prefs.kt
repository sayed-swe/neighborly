package com.neighborly.app.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class FeedScope { NEIGHBORHOOD, ALL }

@Singleton
class Prefs @Inject constructor(private val store: DataStore<Preferences>) {
    private val scopeKey = stringPreferencesKey("feed_scope")
    private val onboardedKey = booleanPreferencesKey("onboarded")

    val feedScope: Flow<FeedScope> = store.data.map { p ->
        p[scopeKey]?.let { runCatching { FeedScope.valueOf(it) }.getOrNull() } ?: FeedScope.NEIGHBORHOOD
    }
    val onboarded: Flow<Boolean> = store.data.map { it[onboardedKey] ?: false }

    suspend fun setFeedScope(scope: FeedScope) = store.edit { it[scopeKey] = scope.name }
    suspend fun setOnboarded() = store.edit { it[onboardedKey] = true }
}
