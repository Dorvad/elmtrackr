package com.elmtrackr.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.domain.repository.ShiftsRepository
import com.elmtrackr.app.domain.repository.TasksRepository
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.tasks.TaskDefaultRule
import com.elmtrackr.app.domain.tasks.TaskDefaultRulesBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Instant
import java.util.UUID

sealed interface TaskManagementUiState {
    data object Loading : TaskManagementUiState
    data class Ready(
        val tasks: List<Task>,
        val defaultRules: List<TaskDefaultRule> = emptyList(),
        val message: String? = null,
        val errorMessage: String? = null,
    ) : TaskManagementUiState
    data class Error(val message: String) : TaskManagementUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class TaskManagementViewModel(
    private val tasksRepository: TasksRepository,
    private val shiftsRepository: ShiftsRepository,
    private val currentUserProvider: CurrentUserProvider,
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _reload = MutableStateFlow(0)

    val uiState: StateFlow<TaskManagementUiState> = _reload
        .flatMapLatest {
            currentUserProvider.userId.flatMapLatest { userId ->
                if (userId == null) return@flatMapLatest flowOf(TaskManagementUiState.Loading)
                combine(
                    tasksRepository.observeAllTasks(userId),
                    shiftsRepository.observeRecentCompletedShifts(userId, 120),
                ) { tasks, shifts ->
                    val active = tasks.filter { !it.isArchived }
                    val rules = TaskDefaultRulesBuilder.buildRules(active, shifts)
                    TaskManagementUiState.Ready(
                        tasks = tasks,
                        defaultRules = rules,
                        message = _message.value,
                        errorMessage = _errorMessage.value,
                    ) as TaskManagementUiState
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskManagementUiState.Loading)

    fun load() {
        _reload.value++
    }

    fun clearMessage() {
        _message.value = null
        _errorMessage.value = null
    }

    fun saveTask(taskId: String?, name: String, icon: String, color: String?, hourlyRate: Double) {
        viewModelScope.launch {
            val userId = currentUserProvider.currentUserId() ?: return@launch
            val trimmed = name.trim()
            val duplicate = tasksRepository.getActiveTasks(userId).any {
                it.id != taskId && it.name.equals(trimmed, ignoreCase = true)
            }
            if (duplicate) {
                _errorMessage.value = "A task named \"$trimmed\" already exists"
                return@launch
            }
            val now = Instant.now()
            val existing = taskId?.let { tasksRepository.getTaskById(userId, it) }
            tasksRepository.upsertTask(
                Task(
                    id = taskId ?: UUID.randomUUID().toString(),
                    userId = userId,
                    name = trimmed,
                    icon = icon,
                    color = color,
                    hourlyRate = hourlyRate,
                    isArchived = existing?.isArchived ?: false,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    lastUsedAt = existing?.lastUsedAt,
                    remoteId = existing?.remoteId,
                ),
            )
            _message.value = "Task saved"
            _errorMessage.value = null
        }
    }

    fun archiveTask(taskId: String) {
        viewModelScope.launch {
            val userId = currentUserProvider.currentUserId() ?: return@launch
            tasksRepository.archiveTask(userId, taskId)
            _message.value = "Task archived"
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                @Suppress("UNCHECKED_CAST")
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ElmTrackrApp
                TaskManagementViewModel(app.tasksRepository, app.shiftsRepository, app.currentUserProvider)
            }
        }
    }
}
