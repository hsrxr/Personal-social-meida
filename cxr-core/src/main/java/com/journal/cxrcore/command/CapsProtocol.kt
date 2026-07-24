package com.journal.cxrcore.command

/**
 * Caps protocol constants shared between phone and glasses.
 *
 * Channel names and field order must match exactly with the glasses-side app.
 */
object CapsProtocol {
    /** Glasses → Phone: key event reports. */
    const val CHANNEL_JOURNAL_EVENT = "journal_event"

    /** Glasses → Phone: audio stream data frames. */
    const val CHANNEL_AUDIO_STREAM = "audio_stream"

    /** Phone → Glasses: commands (display text, vibrate, etc.). SDK standard channel. */
    const val CHANNEL_PHONE_CMD = "rk_custom_client"

    /** Return channel key (first Caps field convention per SDK). */
    const val CHANNEL_RETURN_KEY = "rk_custom_key"

    // --- Journal event fields (glasses → phone) ---
    const val FIELD_EVENT_TYPE = "eventType"
    const val FIELD_TIMESTAMP = "timestamp"
    const val FIELD_METADATA = "metadata"

    // --- Audio stream fields (glasses → phone) ---
    const val FIELD_SEQ = "seq"
    const val FIELD_PCM_CHUNK = "pcmChunk"
    const val FIELD_IS_FINAL = "isFinal"

    // --- Phone command fields (phone → glasses) ---
    const val FIELD_CMD = "cmd"
    const val FIELD_TEXT = "text"

    /** Known event types from glasses. */
    object EventType {
        const val MOMENT_MARK = "moment_mark"
        const val QUICK_NOTE_START = "quick_note_start"
        const val AGENT_TALK_START = "agent_talk_start"
        const val AGENT_TALK_STOP = "agent_talk_stop"
        const val PLAY_SUMMARY = "play_summary"
        const val SKIP = "skip"
    }

    /** Known phone commands sent to glasses. */
    object PhoneCmd {
        const val SHOW_STATUS = "show_status"
        const val VIBRATE = "vibrate"
        const val UPDATE_DISPLAY = "update_display"
        const val START_AUDIO = "start_audio"
        const val STOP_AUDIO = "stop_audio"
    }
}

/**
 * Parsed journal event from glasses.
 */
data class JournalEvent(
    val eventType: String,
    val timestamp: Long,
    val metadata: String = "",
)
