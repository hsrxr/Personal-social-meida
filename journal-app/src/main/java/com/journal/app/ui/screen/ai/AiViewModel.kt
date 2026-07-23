package com.journal.app.ui.screen.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journal.app.data.model.SocialPlatform
import com.journal.app.data.repository.AiService
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiUiState())
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    init {
        loadSummary()
    }

    fun loadSummary() {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true) }
            aiService.generateSummary(today).fold(
                onSuccess = { summary ->
                    _uiState.update { it.copy(isGenerating = false, summary = summary) }
                },
                onFailure = {
                    _uiState.update { it.copy(isGenerating = false) }
                },
            )
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
