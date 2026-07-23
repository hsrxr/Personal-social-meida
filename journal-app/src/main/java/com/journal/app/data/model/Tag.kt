package com.journal.app.data.model

data class Tag(
    val id: Long = 0,
    val name: String,
    val type: TagType = TagType.CUSTOM,
)

enum class TagType { MOOD, ACTIVITY, LOCATION, CUSTOM, AI_SUGGESTED }
