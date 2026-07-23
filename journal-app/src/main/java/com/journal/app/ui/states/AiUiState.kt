package com.journal.app.ui.states

import com.journal.app.data.model.SocialCopy
import com.journal.app.data.model.SocialPlatform
import com.journal.app.data.model.Summary

data class AiUiState(
    val isGenerating: Boolean = false,
    val summary: Summary? = null,
    val socialCopies: List<SocialCopy> = emptyList(),
    val selectedPlatform: SocialPlatform = SocialPlatform.WECHAT_MOMENTS,
)
