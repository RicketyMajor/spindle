package dev.ricketymajor.spindle.library

import java.io.File
import java.io.RandomAccessFile

/** Codec and bit depth from the file header (ADR-006): TagLib gives neither. [bits] only for lossless formats. */
data class AudioHeader(val codec: String, val bits: Int?)

/** Null when the header can't be read; callers fall back to [codecFromExtension]. */
fun readAudioHeader(file: File): AudioHeader? = runCatching {
    RandomAccessFile(file, "r").use { f ->
        when (file.extension.lowercase()) {
            "flac" -> f.flac()
            "wav" -> f.wav()
            "m4a" -> f.mp4()
            else -> codecFromExtension(file.extension)?.let { AudioHeader(it, null) }
        }
    }
}.getOrNull()

/** By extension only. `m4a` is null: it can be AAC or ALAC, only the header knows. */
fun codecFromExtension(ext: String): String? = when (ext.lowercase()) {
    "flac" -> "FLAC"
    "wav" -> "WAV"
    "mp3" -> "MP3"
    "opus" -> "OPUS"
    "ogg" -> "VORBIS" // ponytail: Opus inside .ogg would be mislabelled; Station writes Opus as .opus
    else -> null
}

/** STREAMINFO is the first metadata block; bits-per-sample − 1 spans byte 12 bit 0 and byte 13's high nibble. */
private fun RandomAccessFile.flac(): AudioHeader? {
    val head = ByteArray(10)
    readFully(head)
    val start = if (ascii(head, 0, 3) == "ID3") 10L + synchsafe(head, 6) else 0L
    seek(start)
    val b = ByteArray(8 + 34) // "fLaC" + block header + STREAMINFO
    readFully(b)
    if (ascii(b, 0, 4) != "fLaC" || (b[4].toInt() and 0x7F) != 0) return null
    val bitsMinus1 = ((b[8 + 12].toInt() and 0x01) shl 4) or ((b[8 + 13].toInt() and 0xFF) ushr 4)
    return AudioHeader("FLAC", bitsMinus1 + 1)
}

/** Walks RIFF chunks to `fmt `; bitsPerSample is its 16-bit field at offset 14. */
private fun RandomAccessFile.wav(): AudioHeader? {
    val riff = ByteArray(12)
    readFully(riff)
    if (ascii(riff, 0, 4) != "RIFF" || ascii(riff, 8, 4) != "WAVE") return null
    val chunk = ByteArray(8)
    repeat(32) { // ponytail: real files have a handful of chunks before fmt
        readFully(chunk)
        val size = le32(chunk, 4)
        if (ascii(chunk, 0, 4) == "fmt ") {
            val fmt = ByteArray(16)
            readFully(fmt)
            return AudioHeader("WAV", le16(fmt, 14))
        }
        seek(filePointer + size + (size and 1)) // chunks are padded to even sizes
    }
    return null
}

private fun ascii(b: ByteArray, from: Int, len: Int) = String(b, from, len, Charsets.US_ASCII)
private fun le16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
private fun le32(b: ByteArray, o: Int) = (0..3).fold(0L) { acc, i -> acc or ((b[o + i].toLong() and 0xFF) shl (8 * i)) }
private fun synchsafe(b: ByteArray, o: Int) = (0..3).fold(0L) { acc, i -> (acc shl 7) or (b[o + i].toLong() and 0x7F) }

/** `stsd`'s first sample entry: `alac` → ALAC with the cookie's bit depth, `mp4a` → AAC. `moov` may sit after `mdat`. */
private fun RandomAccessFile.mp4(): AudioHeader? {
    val stsd = findBox(0, length(), listOf("moov", "trak", "mdia", "minf", "stbl", "stsd")) ?: return null
    seek(stsd + 16) // box header + version/flags + entry count
    val entry = ByteArray(ALAC_BITS + 1)
    readFully(entry, 0, 8)
    return when (ascii(entry, 4, 4)) {
        "mp4a" -> AudioHeader("AAC", null)
        "alac" -> {
            readFully(entry, 8, ALAC_BITS + 1 - 8)
            AudioHeader("ALAC", entry[ALAC_BITS].toInt() and 0xFF)
        }
        else -> null
    }
}

/** Sample entry header 8 + AudioSampleEntry 28 + `alac` box header 8 + version/flags 4; the cookie's byte 5 is the bit depth. */
private const val ALAC_BITS = 8 + 28 + 8 + 4 + 5

/** Offset of the box at [path] inside [from, to), descending only through the named containers. */
private fun RandomAccessFile.findBox(from: Long, to: Long, path: List<String>): Long? {
    val h = ByteArray(16)
    var pos = from
    while (pos + 8 <= to) {
        seek(pos)
        readFully(h, 0, 8)
        var size = be32(h, 0)
        var header = 8L
        if (size == 1L) { // 64-bit size follows the type
            readFully(h, 8, 8)
            size = be64(h, 8)
            header = 16
        }
        if (size == 0L) size = to - pos // box runs to the end
        if (size < header) return null
        if (ascii(h, 4, 4) == path[0]) return if (path.size == 1) pos else findBox(pos + header, pos + size, path.drop(1))
        pos += size
    }
    return null
}

private fun be32(b: ByteArray, o: Int) = (0..3).fold(0L) { acc, i -> (acc shl 8) or (b[o + i].toLong() and 0xFF) }
private fun be64(b: ByteArray, o: Int) = (0..7).fold(0L) { acc, i -> (acc shl 8) or (b[o + i].toLong() and 0xFF) }
