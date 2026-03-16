package org.nudgealarm.app.ai.model

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.net.HttpURLConnection
import java.net.URL

/** Minimal HuggingFace API client for browsing MediaPipe-compatible models. */
object HuggingFaceApi {

    private const val TAG = "HuggingFaceApi"
    private val gson = Gson()

    /**
     * Fetches .task/.bin model files from the `litert-community` org on HuggingFace.
     * Falls back to an empty list on any error.
     */
    suspend fun fetchLiteRtModels(hfToken: String? = null): List<ModelInfo> {
        // Fetch all models in litert-community with full file list
        val apiUrl = "https://huggingface.co/api/models?organization=litert-community&limit=50&full=true"
        return try {
            val json = fetchJson(apiUrl, hfToken) ?: return emptyList()
            val type = object : TypeToken<List<HfModel>>() {}.type
            val models: List<HfModel> = gson.fromJson(json, type) ?: return emptyList()
            models.flatMap { it.toModelInfoList() }.sortedBy { it.name }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch litert-community models", e)
            emptyList()
        }
    }

    /**
     * Searches HuggingFace for models matching [query] that have .task files.
     */
    suspend fun searchModels(query: String, hfToken: String? = null): List<ModelInfo> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val apiUrl = "https://huggingface.co/api/models?search=$encoded&limit=30&full=true"
        return try {
            val json = fetchJson(apiUrl, hfToken) ?: return emptyList()
            val type = object : TypeToken<List<HfModel>>() {}.type
            val models: List<HfModel> = gson.fromJson(json, type) ?: return emptyList()
            models.flatMap { it.toModelInfoList() }.sortedBy { it.name }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for '$query'", e)
            emptyList()
        }
    }

    private fun fetchJson(urlStr: String, hfToken: String?): String? {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Accept", "application/json")
        if (hfToken != null) conn.setRequestProperty("Authorization", "Bearer $hfToken")
        return try {
            if (conn.responseCode != 200) {
                Log.w(TAG, "HTTP ${conn.responseCode} for $urlStr")
                return null
            }
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    // ---- JSON response types ----

    private data class HfModel(
        @SerializedName("id") val id: String? = null,
        @SerializedName("modelId") val modelId: String? = null,
        @SerializedName("siblings") val siblings: List<HfSibling>? = null
    ) {
        val repoId: String get() = id ?: modelId ?: ""

        fun toModelInfoList(): List<ModelInfo> {
            val repo = repoId.ifBlank { return emptyList() }
            val repoName = repo.substringAfterLast("/")
            return (siblings ?: emptyList())
                .filter { s -> s.rfilename?.endsWith(".task") == true || s.rfilename?.endsWith(".bin") == true }
                .filter { s ->
                    // Exclude web-only and tflite-only files
                    s.rfilename?.contains("-web") != true
                }
                .mapNotNull { s ->
                    val fname = s.rfilename ?: return@mapNotNull null
                    val sizeBytes = s.size ?: estimateSizeFromName(fname)
                    ModelInfo(
                        id = "${repo.replace("/", "_")}_${fname.substringBeforeLast(".")}",
                        name = "$repoName / ${fname.substringBeforeLast(".")}",
                        description = "From $repo",
                        sizeBytes = sizeBytes,
                        huggingFaceUrl = "https://huggingface.co/$repo/resolve/main/$fname",
                        filename = fname,
                        requiresHfToken = true
                    )
                }
        }
    }

    private data class HfSibling(
        @SerializedName("rfilename") val rfilename: String? = null,
        @SerializedName("size") val size: Long? = null
    )

    /** Rough size estimate when the API doesn't return sizes (common for gated models). */
    private fun estimateSizeFromName(filename: String): Long {
        return when {
            filename.contains("1b", ignoreCase = true) || filename.contains("1B") -> 650_000_000L
            filename.contains("2b", ignoreCase = true) || filename.contains("2B") -> 1_500_000_000L
            filename.contains("4b", ignoreCase = true) || filename.contains("4B") -> 3_000_000_000L
            filename.contains("7b", ignoreCase = true) || filename.contains("7B") -> 5_000_000_000L
            else -> 1_000_000_000L
        }
    }
}
