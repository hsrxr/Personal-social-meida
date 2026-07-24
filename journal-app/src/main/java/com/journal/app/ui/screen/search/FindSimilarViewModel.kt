package com.journal.app.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journal.app.data.model.MyPost
import com.journal.app.data.repository.SearchRepository
import com.journal.app.ui.states.FindSimilarUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FindSimilarViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FindSimilarUiState())
    val uiState: StateFlow<FindSimilarUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val posts = searchRepository.getMyPosts()
            _uiState.update { it.copy(myPosts = posts) }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        if (query.isNotBlank()) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                val results = searchRepository.search(query)
                _uiState.update { it.copy(isLoading = false, results = results) }
            }
        }
    }

    fun showPostPicker() = _uiState.update { it.copy(showPostPicker = true) }
    fun dismissPostPicker() = _uiState.update { it.copy(showPostPicker = false) }

    fun selectPost(post: MyPost) {
        _uiState.update { it.copy(selectedPost = post, showPostPicker = false, isLoading = true) }
        viewModelScope.launch {
            val results = searchRepository.findSimilar(post.id)
            _uiState.update { it.copy(isLoading = false, results = results) }
        }
    }
}
