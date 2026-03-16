package org.nudgealarm.app.ai.model

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_URL = "url"
        const val KEY_FILENAME = "filename"
        const val KEY_HF_TOKEN = "hf_token"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"

        /** Models smaller than this are almost certainly a corrupt download (e.g. an auth error HTML page). */
        const val MIN_VALID_SIZE_BYTES = 50 * 1024 * 1024L // 50 MB
    }

    override suspend fun doWork(): Result {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()
        val urlStr = inputData.getString(KEY_URL) ?: return Result.failure()
        val filename = inputData.getString(KEY_FILENAME) ?: return Result.failure()
        val hfToken = inputData.getString(KEY_HF_TOKEN) // optional

        val modelsDir = File(applicationContext.filesDir, "ai_models").also { it.mkdirs() }
        val tempFile = File(modelsDir, "$filename.download")
        val finalFile = File(modelsDir, filename)

        // Clean up any leftover temp or corrupt files
        tempFile.delete()

        return try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            if (hfToken != null) {
                connection.setRequestProperty("Authorization", "Bearer $hfToken")
            }
            connection.instanceFollowRedirects = true
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val hasToken = hfToken != null
                val msg = when (responseCode) {
                    401 -> if (hasToken) "Invalid token (HTTP 401). Check your HuggingFace token is correct."
                           else "Login required (HTTP 401). Enter your HuggingFace token in the download screen."
                    403 -> if (hasToken) "License not accepted (HTTP 403). Go to the model page on HuggingFace and click \"Agree and access repository\", then retry."
                           else "Access denied (HTTP 403). Enter your HuggingFace token and accept the model license on HuggingFace."
                    404 -> "Model file not found (HTTP 404). URL: $urlStr"
                    else -> "Server returned HTTP $responseCode for URL: $urlStr"
                }
                Log.e("ModelDownloadWorker", msg)
                return Result.failure(workDataOf(KEY_ERROR to msg))
            }

            val contentLength = connection.contentLengthLong

            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    var bytes = input.read(buffer)
                    while (bytes >= 0) {
                        if (isStopped) {
                            tempFile.delete()
                            return Result.failure(workDataOf(KEY_ERROR to "Cancelled"))
                        }
                        output.write(buffer, 0, bytes)
                        downloaded += bytes
                        bytes = input.read(buffer)

                        if (contentLength > 0) {
                            val progress = (downloaded * 100 / contentLength).toInt()
                            setProgress(workDataOf(KEY_PROGRESS to progress))
                        }
                    }
                }
            }

            // Sanity check: reject suspiciously small "model" files (likely an auth error page)
            if (tempFile.length() < MIN_VALID_SIZE_BYTES) {
                val kb = tempFile.length() / 1024
                val msg = "Download produced only ${kb} KB — likely an auth error or wrong URL. Enter your HuggingFace token and retry. URL: $urlStr"
                Log.e("ModelDownloadWorker", msg)
                tempFile.delete()
                return Result.failure(workDataOf(KEY_ERROR to msg))
            }

            // Atomic rename to final filename
            if (finalFile.exists()) finalFile.delete()
            tempFile.renameTo(finalFile)

            Result.success(workDataOf(KEY_MODEL_ID to modelId))
        } catch (e: Exception) {
            Log.e("ModelDownloadWorker", "Exception during download of $urlStr", e)
            tempFile.delete()
            Result.failure(workDataOf(KEY_ERROR to "${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}"))
        }
    }
}
