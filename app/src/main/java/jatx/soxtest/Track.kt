package jatx.soxtest

import android.util.Log
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.flac.FlacTag
import java.io.File

data class Track(
    val artist: String = "",
    val album: String = "",
    val title: String = "",
    val year: String = "1900",
    val length: String = "",
    val number: String = "0"
) {
    fun tryToFill(file: File): Track {
        return try {
            val af = AudioFileIO.read(file)
            val len: Int = af.audioHeader.trackLength
            val sec = len % 60
            val min = (len - sec) / 60
            val _length = String.format("%02d:%02d", min, sec)
            val _track = when (file.extension) {
                "mp3" -> {
                    fillFromMP3File(af)
                }
                "flac" -> {
                    fillFromFLACFile(af)
                }
                else -> {
                    throw IllegalStateException("wrong file extension")
                }
            }
            val _title = _track.title.takeIf { it.trim().isNotEmpty() } ?: file.name
            _track.copy(title = _title, length = _length)
        } catch (e: Throwable) {
            Log.e("error", "track", e)
            this
        }
    }

    private fun fillFromMP3File(af: AudioFile): Track {
        val tag = af.tag
        val _artist = tag.getFirst(FieldKey.ARTIST).trim()
        val _album = tag.getFirst(FieldKey.ALBUM).trim()
        val _title = tag.getFirst(FieldKey.TITLE).trim()
        val _year = tag.getFirst(FieldKey.YEAR)
        var _number = tag.getFirst(FieldKey.TRACK)
        try {
            val num = _number.toInt()
            if (num < 10) {
                _number = "00$num"
            } else if (num < 100) {
                _number = "0$num"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return copy(artist = _artist, album = _album, title = _title, year = _year, number = _number)
    }

    private fun fillFromFLACFile(af: AudioFile): Track {
        val tag = af.tag as FlacTag
        val _artist = tag.getFirst(FieldKey.ARTIST).trim()
        val _album = tag.getFirst(FieldKey.ALBUM).trim()
        val _title = tag.getFirst(FieldKey.TITLE).trim()
        val _year = tag.getFirst(FieldKey.YEAR)
        var _number = tag.getFirst(FieldKey.TRACK)
        try {
            val num = _number.toInt()
            if (num < 10) {
                _number = "00$num"
            } else if (num < 100) {
                _number = "0$num"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return copy(artist = _artist, album = _album, title = _title, year = _year, number = _number)
    }
}