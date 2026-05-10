package com.resdownloader.data.repository

import com.resdownloader.data.model.DownloadStatus
import com.resdownloader.data.model.DownloadTask
import com.resdownloader.data.model.ResourceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor() {

    private val _downloadTasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloadTasks: StateFlow<List<DownloadTask>> = _downloadTasks.asStateFlow()

    private val _activeTasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val activeTasks: StateFlow<List<DownloadTask>> = _activeTasks.asStateFlow()

    private val _completedTasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val completedTasks: StateFlow<List<DownloadTask>> = _completedTasks.asStateFlow()

    fun createDownloadTask(resourceInfo: ResourceInfo): DownloadTask {
        val task = DownloadTask(
            id = UUID.randomUUID().toString(),
            resourceInfo = resourceInfo,
            status = DownloadStatus.PENDING,
            progress = 0,
            downloadedBytes = 0,
            totalBytes = resourceInfo.size
        )

        val currentTasks = _downloadTasks.value.toMutableList()
        currentTasks.add(task)
        _downloadTasks.value = currentTasks

        updateTaskLists()

        return task
    }

    fun updateTaskProgress(taskId: String, progress: Int, downloadedBytes: Long) {
        val currentTasks = _downloadTasks.value.toMutableList()
        val index = currentTasks.indexOfFirst { it.id == taskId }

        if (index != -1) {
            currentTasks[index] = currentTasks[index].copy(
                status = DownloadStatus.DOWNLOADING,
                progress = progress,
                downloadedBytes = downloadedBytes
            )
            _downloadTasks.value = currentTasks
            updateTaskLists()
        }
    }

    fun updateTaskStatus(taskId: String, status: DownloadStatus, filePath: String? = null, error: String? = null) {
        val currentTasks = _downloadTasks.value.toMutableList()
        val index = currentTasks.indexOfFirst { it.id == taskId }

        if (index != -1) {
            currentTasks[index] = currentTasks[index].copy(
                status = status,
                filePath = filePath,
                errorMessage = error,
                progress = if (status == DownloadStatus.COMPLETED) 100 else currentTasks[index].progress
            )
            _downloadTasks.value = currentTasks
            updateTaskLists()
        }
    }

    fun pauseTask(taskId: String) {
        updateTaskStatus(taskId, DownloadStatus.PAUSED)
    }

    fun resumeTask(taskId: String) {
        updateTaskStatus(taskId, DownloadStatus.PENDING)
    }

    fun cancelTask(taskId: String) {
        val currentTasks = _downloadTasks.value.toMutableList()
        currentTasks.removeAll { it.id == taskId }
        _downloadTasks.value = currentTasks
        updateTaskLists()
    }

    fun removeCompletedTask(taskId: String) {
        val currentTasks = _downloadTasks.value.toMutableList()
        currentTasks.removeAll { it.id == taskId }
        _downloadTasks.value = currentTasks
        updateTaskLists()
    }

    private fun updateTaskLists() {
        _activeTasks.value = _downloadTasks.value.filter {
            it.status != DownloadStatus.COMPLETED
        }
        _completedTasks.value = _downloadTasks.value.filter {
            it.status == DownloadStatus.COMPLETED
        }
    }
}
