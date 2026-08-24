package com.elmtrackr.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.data.repository.CompensationProfilesRepository
import com.elmtrackr.app.domain.model.CompensationProfile
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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

sealed interface TaskManagementUiState {
    data object Loading : TaskManagementUiState
    data class Ready(
        val tasks: List<Task>,
        /**
         * The work profiles a task can belong to, and the one this screen is
         * showing. Empty or single means there is nothing to choose and the
         * selector stays off screen.
         */
        val profiles: List<CompensationProfile> = emptyList(),
        val selectedProfileId: String? = null,
        val defaultRules: List<TaskDefaultRule> = emptyList(),
        val message: UiText? = null,
        val errorMessage: UiText? = null,
    ) : TaskManagementUiState
    data class Error(val message: String) : TaskManagementUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskManagementViewModel @Inject constructor(
    private val tasksRepository: TasksRepository,
    private val shiftsRepository: ShiftsRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val compensationProfilesRepository: CompensationProfilesRepository,
) : ViewModel() {

    private val _message = MutableStateFlow<UiText?>(null)
    private val _errorMessage = MutableStateFlow<UiText?>(null)
    private val _reload = MutableStateFlow(0)

    /**
     * The work profile this screen is showing, and the one a new task is created
     * under. Set once at the top of the screen rather than as a field in the task
     * editor: the context is the same for every task the user adds in a sitting,
     * and a per-task picker would ask the same question over and over.
     */
    private val _selectedProfileId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TaskManagementUiState> = _reload
        .flatMapLatest {
            currentUserProvider.userId.flatMapLatest { userId ->
                if (userId == null) return@flatMapLatest flowOf(TaskManagementUiState.Loading)
                combine(
                    tasksRepository.observeAllTasks(userId),
                    shiftsRepository.observeRecentCompletedShifts(userId, 120),
                    compensationProfilesRepository.observeProfiles(userId),
                    _selectedProfileId,
                ) { tasks, shifts, profiles, requested ->
                    val profileId = resolveProfileId(profiles, requested)
                    val scoped = tasksForProfile(tasks, profileId, profiles)
                    val active = scoped.filter { !it.isArchived }
                    val rules = TaskDefaultRulesBuilder.buildRules(active, shifts)
                    TaskManagementUiState.Ready(
                        tasks = scoped,
                        profiles = profiles,
                        selectedProfileId = profileId,
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

    fun selectProfile(profileId: String) {
        _selectedProfileId.value = profileId
    }

    /** The chosen profile while it exists, then the default, then the first. */
    private fun resolveProfileId(
        profiles: List<CompensationProfile>,
        requested: String?,
    ): String? = requested?.takeIf { id -> profiles.any { it.id == id } }
        ?: profiles.firstOrNull { it.isDefault }?.id
        ?: profiles.firstOrNull()?.id

    /**
     * A task with no profile is shown under the default one rather than hidden:
     * every task created before tasks were scoped to a job has none, and taking an
     * upgrading user's list away would be worse than showing it where they have
     * always seen it.
     */
    private fun tasksForProfile(
        tasks: List<Task>,
        profileId: String?,
        profiles: List<CompensationProfile>,
    ): List<Task> {
        if (profileId == null) return tasks
        val isDefaultProfile = profiles.firstOrNull { it.id == profileId }?.isDefault == true ||
            profiles.size <= 1
        return tasks.filter { task ->
            task.compensationProfileId == profileId ||
                (task.compensationProfileId == null && isDefaultProfile)
        }
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
                _errorMessage.value = UiText.Res(R.string.tasks_error_name_exists)
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
                    // An edit keeps the task where it is; a new task joins the
                    // profile the screen is showing.
                    compensationProfileId = existing?.compensationProfileId
                        ?: resolveProfileId(
                            compensationProfilesRepository.getProfiles(userId),
                            _selectedProfileId.value,
                        ),
                    isArchived = existing?.isArchived ?: false,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    lastUsedAt = existing?.lastUsedAt,
                    remoteId = existing?.remoteId,
                ),
            )
            _message.value = UiText.Res(R.string.tasks_msg_saved)
            _errorMessage.value = null
        }
    }

    fun archiveTask(taskId: String) {
        viewModelScope.launch {
            val userId = currentUserProvider.currentUserId() ?: return@launch
            tasksRepository.archiveTask(userId, taskId)
            _message.value = UiText.Res(R.string.tasks_msg_archived)
        }
    }

    fun unarchiveTask(taskId: String) {
        viewModelScope.launch {
            val userId = currentUserProvider.currentUserId() ?: return@launch
            tasksRepository.unarchiveTask(userId, taskId)
            _message.value = UiText.Res(R.string.tasks_msg_restored)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            val userId = currentUserProvider.currentUserId() ?: return@launch
            tasksRepository.deleteTask(userId, taskId)
            _message.value = UiText.Res(R.string.tasks_msg_deleted)
        }
    }
}
