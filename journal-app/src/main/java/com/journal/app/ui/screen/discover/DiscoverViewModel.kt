package com.journal.app.ui.screen.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journal.app.data.model.FeedPost
import com.journal.app.data.repository.FeedRepository
import com.journal.app.ui.states.DiscoverUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            feedRepository.getEchoes().collect { posts ->
                _uiState.update { it.copy(isLoading = false, posts = posts) }
            }
        }
    }

    fun openPost(post: FeedPost) {
        _uiState.update { it.copy(selectedPost = post) }
    }

    fun dismissPost() {
        _uiState.update { it.copy(selectedPost = null) }
    }

    fun skipPost(post: FeedPost) {
        // Swipe left — just remove from stack visually handled by SwipeableCardStack
    }

    fun sayHiAndShare(post: FeedPost) {
        viewModelScope.launch {
            val myPostText = post.matchedMyPostText ?: ""
            val ok = feedRepository.sayHiAndShare(post.id, myPostText)
            val message = if (ok) {
                "Sent Hi and shared your post with ${post.authorName} 👋"
            } else {
                "Couldn't reach ${post.authorName}"
            }
            // Update matchedMyPostText to simulate "shared" state
            _uiState.update {
                it.copy(
                    sayHiConfirmation = message,
                    selectedPost = null,
                    navigateToConversationId = if (ok) "conv-${post.authorId}" else null,
                )
            }
        }
    }

    fun sayHi(post: FeedPost) {
        viewModelScope.launch {
            val myPostText = post.matchedMyPostText ?: ""
            val ok = feedRepository.sayHiAndShare(post.id, myPostText)
            val message = if (ok) "Said hi to ${post.authorName} 👋" else "Couldn't reach ${post.authorName}"
            _uiState.update {
                it.copy(
                    sayHiConfirmation = message,
                    selectedPost = null,
                    navigateToConversationId = if (ok) "conv-${post.authorId}" else null,
                )
            }
        }
    }

    fun consumeConfirmation() {
        _uiState.update { it.copy(sayHiConfirmation = null) }
    }

    fun consumeNavigation() {
        _uiState.update { it.copy(navigateToConversationId = null) }
    }
}
