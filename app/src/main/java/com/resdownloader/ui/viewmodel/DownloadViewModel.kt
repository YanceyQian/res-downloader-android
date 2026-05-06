package com.resdownloader.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resdownloader.data.model.DownloadStatus
import com.resdownloader.data.model.DownloadTask
import com.resdownloader.data.model.ResourceInfo
import com.resdownloader.data.repository.DownloadRepository
import com.resdownloader.service.DownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val activeTasks = downloadRepository.activeTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTasks = downloadRepository.completedTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _downloadStates = MutableStateFlow<Map<String, DownloadService.DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadService.DownloadState>> = _downloadStates.asStateFlow()

    fun startDownload(resourceInfo: ResourceInfo) {
        viewModelScope.launch {
            val task = downloadRepository.createDownloadTask(resourceInfo)
            DownloadService.startDownload(context, task.id, resourceInfo)
        }
    }

    fun pauseDownload(taskId: String) {
        viewModelScope.launch {
            downloadRepository.pauseTask(taskId)
            DownloadService.pauseDownload(taskId)
        }
    }

    fun resumeDownload(task: DownloadTask) {
        viewModelScope.launch {
            downloadRepository.resumeTask(task.id)
            DownloadService.startDownload(context, task.id, task.resourceInfo)
        }
    }

    fun cancelDownload(taskId: String) {
        viewModelScope.launch {
            downloadRepository.cancelTask(taskId)
            DownloadService.cancelDownload(taskId)
        }
    }

    fun removeCompletedTask(taskId: String) {
        viewModelScope.launch {
            downloadRepository.removeCompletedTask(taskId)
        }
    }

    fun updateDownloadState(taskId: String, state: DownloadService.DownloadState) {
        viewModelScope.launch {
            _downloadStates.value = _downloadStates.value + (taskId to state)

            when (state) {
                is DownloadService.DownloadState.Completed -> {
                    downloadRepository.updateTaskStatus(
                        taskId,
                        DownloadStatus.COMPLETED,
                        filePath = state.filePath
                    )
                }
                is DownloadService.DownloadState.Failed -> {
                    downloadRepository.updateTaskStatus(
                        taskId,
                        DownloadStatus.FAILED,
                        error = state.error
                    )
                }
                is DownloadService.DownloadState.Paused -> {
                    downloadRepository.updateTaskStatus(taskId, DownloadStatus.PAUSED)
                }
                is DownloadService.DownloadState.Downloading -> {
                    downloadRepository.updateTaskProgress(taskId, state.progress, 0)
                }
            }
        }
    }
}
