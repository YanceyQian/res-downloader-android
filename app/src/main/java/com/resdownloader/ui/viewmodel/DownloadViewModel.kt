package com.resdownloader.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resdownloader.data.model.DownloadTask
import com.resdownloader.data.model.ResourceInfo
import com.resdownloader.data.repository.DownloadRepository
import com.resdownloader.service.DownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    val activeTasks: StateFlow<List<DownloadTask>> = downloadRepository.activeTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTasks: StateFlow<List<DownloadTask>> = downloadRepository.completedTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTasks: StateFlow<List<DownloadTask>> = downloadRepository.downloadTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startDownload(resourceInfo: ResourceInfo) {
        viewModelScope.launch {
            val task = downloadRepository.createDownloadTask(resourceInfo)
            DownloadService.startDownload(context, task.id, resourceInfo.url, resourceInfo.filename, resourceInfo.decodeKey)
        }
    }

    fun pauseDownload(taskId: String) {
        downloadRepository.pauseTask(taskId)
        DownloadService.pauseDownload(taskId)
    }

    fun resumeDownload(taskId: String) {
        val task = allTasks.value.find { it.id == taskId }
        task?.let {
            downloadRepository.updateTaskStatus(taskId, com.resdownloader.data.model.DownloadStatus.PENDING)
            DownloadService.startDownload(context, taskId, it.resourceInfo.url, it.resourceInfo.filename, it.resourceInfo.decodeKey)
        }
    }

    fun cancelDownload(taskId: String) {
        downloadRepository.cancelTask(taskId)
        DownloadService.cancelDownload(taskId)
    }

    fun removeCompleted(taskId: String) {
        downloadRepository.removeCompletedTask(taskId)
    }

    fun updateProgress(taskId: String, progress: Int, downloadedBytes: Long) {
        downloadRepository.updateTaskProgress(taskId, progress, downloadedBytes)
    }

    fun updateStatus(taskId: String, status: com.resdownloader.data.model.DownloadStatus, filePath: String? = null) {
        downloadRepository.updateTaskStatus(taskId, status, filePath)
    }
}