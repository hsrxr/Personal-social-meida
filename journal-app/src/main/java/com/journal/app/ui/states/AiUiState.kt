package com.journal.app.ui.states

import com.journal.app.data.model.SocialCopy
import com.journal.app.data.model.SocialPlatform
import com.journal.app.data.model.Summary
import com.journal.app.data.model.TimelineEntry
import com.journal.app.data.model.Visibility

data class AiUiState(
    val isGenerating: Boolean = false,
    val summary: Summary? = null,
    val socialCopies: List<SocialCopy> = emptyList(),
    val selectedPlatform: SocialPlatform = SocialPlatform.WECHAT_MOMENTS,
    // Material selection
    val materials: List<TimelineEntry> = emptyList(),
    val selectedMaterialIds: Set<String> = emptySet(),
    // Editing mode
    val isEditing: Boolean = false,
    val editedNarrative: String = "",
    val editedKeywords: List<String> = emptyList(),
    // Visibility
    val visibility: Visibility = Visibility.PRIVATE,
    // Feedback
    val published: Boolean = false,
)
