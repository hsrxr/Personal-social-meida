package com.journal.app.ui.screen.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journal.app.data.model.SocialPlatform
import com.journal.app.data.model.Visibility
import com.journal.app.data.repository.AiService
import com.journal.app.data.repository.TimelineRepository
import com.journal.app.ui.states.AiUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class AiViewModel @Inject constructor(
    private val aiService: AiService,
    private val timelineRepository: TimelineRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiUiState())
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    private val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    init {
        loadMaterials()
    }

    private fun loadMaterials() {
        viewModelScope.launch {
            val date = LocalDate.now()
            timelineRepository.getEntries(date).collect { entries ->
                _uiState.update {
                    it.copy(
                        materials = entries,
                        selectedMaterialIds = entries.map { e -> e.id }.toSet(), // select all by default
                    )
                }
            }
        }
    }

    fun toggleMaterial(id: String) {
        _uiState.update { state ->
            val ids = state.selectedMaterialIds.toMutableSet()
            if (id in ids) ids.remove(id) else ids.add(id)
            state.copy(selectedMaterialIds = ids)
        }
    }

    fun loadSummary() {
        val selectedMaterials = _uiState.value.materials.filter { it.id in _uiState.value.selectedMaterialIds }
        if (selectedMaterials.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true) }
            aiService.generateSummary(today, selectedMaterials).fold(
                onSuccess = { summary ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            summary = summary,
                            editedNarrative = summary.narrative,
                            editedKeywords = summary.keywords,
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isGenerating = false) }
                },
            )
        }
    }

    fun startEditing() {
        val summary = _uiState.value.summary ?: return
        _uiState.update {
            it.copy(
                isEditing = true,
                editedNarrative = summary.narrative,
                editedKeywords = summary.keywords,
            )
        }
    }

    fun onNarrativeChange(text: String) {
        _uiState.update { it.copy(editedNarrative = text) }
    }

    fun onKeywordAdd(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        _uiState.update {
            it.copy(editedKeywords = it.editedKeywords + trimmed)
        }
    }

    fun onKeywordRemove(keyword: String) {
        _uiState.update {
            it.copy(editedKeywords = it.editedKeywords - keyword)
        }
    }

    fun saveEdits() {
        val state = _uiState.value
        val updatedSummary = state.summary?.copy(
            narrative = state.editedNarrative,
            keywords = state.editedKeywords,
        ) ?: return
        _uiState.update {
            it.copy(
                isEditing = false,
                summary = updatedSummary,
            )
        }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(isEditing = false) }
    }

    fun setVisibility(visibility: Visibility) {
        _uiState.update { it.copy(visibility = visibility) }
    }

    fun publish() {
        val summary = _uiState.value.summary ?: return
        viewModelScope.launch {
            val result = aiService.publishSummary(today, summary, _uiState.value.visibility)
            result.onSuccess {
                _uiState.update { it.copy(published = true) }
            }
        }
    }

    fun generateSocialCopies() {
        val summary = _uiState.value.summary ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true) }
            aiService.generateSocialCopies(summary).fold(
                onSuccess = { copies ->
                    _uiState.update { it.copy(isGenerating = false, socialCopies = copies) }
                },
                onFailure = {
                    _uiState.update { it.copy(isGenerating = false) }
                },
            )
        }
    }

    fun regenerateCopy(platform: SocialPlatform) {
        val summary = _uiState.value.summary ?: return
        viewModelScope.launch {
            aiService.regenerateCopy(platform, summary).fold(
                onSuccess = { newCopy ->
                    val updated = _uiState.value.socialCopies.map {
                        if (it.platform == platform) newCopy else it
                    }
                    _uiState.update { it.copy(socialCopies = updated) }
                },
                onFailure = {},
            )
        }
    }
}
