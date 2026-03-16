package org.nudgealarm.app.ai.model

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    object Success : DownloadState()
    data class Failed(val error: String) : DownloadState()
}

class ModelDownloader(private val context: Context) {

    private val workManager = WorkManager.getInstance(context)

    fun startDownload(model: ModelInfo, hfToken: String? = null) {
        val inputData = Data.Builder()
            .putString(ModelDownloadWorker.KEY_MODEL_ID, model.id)
            .putString(ModelDownloadWorker.KEY_URL, model.huggingFaceUrl)
            .putString(ModelDownloadWorker.KEY_FILENAME, model.filename)
            .apply { if (hfToken != null) putString(ModelDownloadWorker.KEY_HF_TOKEN, hfToken) }
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag(downloadTag(model.id))
            .build()

        workManager.enqueueUniqueWork(downloadTag(model.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelDownload(modelId: String) {
        workManager.cancelUniqueWork(downloadTag(modelId))
    }

    fun getDownloadState(modelId: String): Flow<DownloadState> {
        return workManager.getWorkInfosForUniqueWorkFlow(downloadTag(modelId)).map { infos ->
            val info = infos.firstOrNull() ?: return@map DownloadState.Idle
            when (info.state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                    val progress = info.progress.getInt(ModelDownloadWorker.KEY_PROGRESS, 0)
                    DownloadState.Downloading(progress)
                }
                WorkInfo.State.SUCCEEDED -> DownloadState.Success
                WorkInfo.State.FAILED -> {
                    val error = info.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "Download failed"
                    DownloadState.Failed(error)
                }
                WorkInfo.State.CANCELLED -> DownloadState.Idle
                WorkInfo.State.BLOCKED -> DownloadState.Downloading(0)
            }
        }
    }

    private fun downloadTag(modelId: String) = "download_$modelId"
}
