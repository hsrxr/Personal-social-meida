package com.journal.app.ui.screen.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journal.app.data.repository.FeedRepository
import com.journal.app.ui.states.UserProfileUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val feedRepository: FeedRepository,
) : ViewModel() {

    private val userId: String = savedStateHandle["userId"] ?: ""

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    init {
        if (userId.isNotEmpty()) {
            viewModelScope.launch {
                val profile = feedRepository.getUserPublicProfile(userId)
                _uiState.update { it.copy(isLoading = false, profile = profile) }
            }
        } else {
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
