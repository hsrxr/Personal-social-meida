package com.journal.app.data.model

data class Summary(
    val keywords: List<String>,
    val narrative: String,
    val mood: String?,
    val highlight: String?,
)
