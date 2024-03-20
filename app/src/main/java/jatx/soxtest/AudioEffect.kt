package jatx.soxtest

import java.io.File

sealed class AudioEffect {
    abstract val description: String
    abstract val fileNameModifier: String
}

data class LoadFile(
    private val file: File
): AudioEffect() {
    override val description = "load file: ${file.name}"
    override val fileNameModifier = ""
}

data class Tempo(
    private val tempo: Float
): AudioEffect() {
    override val description = "tempo: $tempo"
    override val fileNameModifier = "tempo_$tempo"
}

data class Pitch(
    private val pitch: Int
): AudioEffect() {
    override val description = "pitch: $pitch"
    override val fileNameModifier = "pitch_$pitch"
}

data object Reverse: AudioEffect() {
    override val description = "reverse"
    override val fileNameModifier = "reverse"
}