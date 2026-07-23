package com.journal.glasses.protocol

/**
 * Caps protocol constants — must match [com.journal.cxrcore.command.CapsProtocol] EXACTLY.
 * Duplicated here because glasses-journal is an independent APK with no dependency on cxr-core.
 */
object CapsProtocol {
    const val CHANNEL_JOURNAL_EVENT = "journal_event"
    const val CHANNEL_AUDIO_STREAM = "audio_stream"
    const val CHANNEL_PHONE_CMD = "phone_cmd"

    const val FIELD_EVENT_TYPE = "eventType"
    const val FIELD_TIMESTAMP = "timestamp"
    const val FIELD_METADATA = "metadata"

    const val FIELD_SEQ = "seq"
    const val FIELD_PCM_CHUNK = "pcmChunk"
    const val FIELD_IS_FINAL = "isFinal"

    const val FIELD_CMD = "cmd"
    const val FIELD_TEXT = "text"

    object EventType {
        const val MOMENT_MARK = "moment_mark"
        const val QUICK_NOTE_START = "quick_note_start"
        const val AGENT_TALK_START = "agent_talk_start"
        const val AGENT_TALK_STOP = "agent_talk_stop"
        const val PLAY_SUMMARY = "play_summary"
        const val SKIP = "skip"
    }

    object PhoneCmd {
        const val SHOW_STATUS = "show_status"
        const val VIBRATE = "vibrate"
        const val UPDATE_DISPLAY = "update_display"
    }
}
