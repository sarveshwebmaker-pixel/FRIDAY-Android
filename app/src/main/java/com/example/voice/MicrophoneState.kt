package com.example.voice

enum class MicrophoneState {
    MIC_IDLE,
    WAKE_WORD_LISTENING,
    COMMAND_LISTENING,
    PROCESSING,
    SPEAKING,
    RELEASING_MIC
}
