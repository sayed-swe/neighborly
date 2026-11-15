package com.neighborly.app.data.repository

import android.net.Uri
import com.neighborly.app.core.util.ImageCompressor
import com.neighborly.app.core.util.appResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageRepo @Inject constructor(
    private val supabase: SupabaseClient,
    private val compressor: ImageCompressor,
) {
    /**
     * Compresses and uploads a photo to the public "media" bucket. The path MUST start with the
     * user's id (storage RLS). Returns the public URL.
     */
    suspend fun uploadImage(uri: Uri, folder: String): Result<String> = appResult {
        val bytes = compressor.compress(uri)
        val uid = supabase.uid()
        val path = "$uid/$folder-${System.currentTimeMillis()}-${(1000..9999).random()}.jpg"
        val bucket = supabase.storage.from("media")
        bucket.upload(path, bytes) {
            upsert = false
            contentType = ContentType.Image.JPEG
        }
        bucket.publicUrl(path)
    }
}
