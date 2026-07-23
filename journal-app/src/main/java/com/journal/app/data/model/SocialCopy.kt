package com.journal.app.data.model

data class SocialCopy(
    val id: String,
    val platform: SocialPlatform,
    val text: String,
    val suggestedPhotoIds: List<String> = emptyList(),
)

enum class SocialPlatform {
    WECHAT_MOMENTS,
    XIAOHONGSHU,
    INSTAGRAM,
}
