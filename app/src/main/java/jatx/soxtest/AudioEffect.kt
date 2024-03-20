package jatx.soxtest

import java.io.File

sealed class AudioEffect {
    abstract val description: String
}

data class LoadFile(
    private val file: File
): AudioEffect() {
    override val description = "load file: ${file.name}"
}

data class Tempo(
    private val tempo: Float
): AudioEffect() {
    override val description = "tempo: $tempo"
}

data object Reverse: AudioEffect() {
    override val description = "reverse"
}