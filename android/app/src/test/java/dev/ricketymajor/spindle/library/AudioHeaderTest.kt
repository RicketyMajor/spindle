package dev.ricketymajor.spindle.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioHeaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun read(name: String, bytes: ByteArray) = readAudioHeader(tmp.newFile(name).apply { writeBytes(bytes) })

    @Test
    fun flacBitDepth() {
        assertEquals(AudioHeader("FLAC", 16), read("a.flac", flac(16)))
        assertEquals(AudioHeader("FLAC", 24), read("b.flac", flac(24)))
    }

    @Test
    fun flacAfterAnId3Tag() = assertEquals(AudioHeader("FLAC", 24), read("c.flac", flac(24, id3 = true)))

    @Test
    fun wavBitDepthSkippingOtherChunks() {
        assertEquals(AudioHeader("WAV", 16), read("a.wav", wav(16)))
        assertEquals(AudioHeader("WAV", 24), read("b.wav", wav(24, extraChunk = false)))
    }

    @Test
    fun lossyByExtensionWithoutBits() {
        assertEquals(AudioHeader("MP3", null), read("a.mp3", ByteArray(4)))
        assertEquals(AudioHeader("OPUS", null), read("a.opus", ByteArray(4)))
        assertEquals(AudioHeader("VORBIS", null), read("a.ogg", ByteArray(4)))
    }

    @Test
    fun truncatedOrForeignIsNull() {
        assertNull(read("t.flac", flac(16).copyOf(20)))
        assertNull(read("x.flac", ByteArray(64)))
        assertNull(read("t.wav", wav(16).copyOf(30)))
        assertNull(read("cover.jpg", ByteArray(64)))
    }

    @Test
    fun alacBitsFromTheCookie() {
        assertEquals(AudioHeader("ALAC", 16), read("a.m4a", m4a(alacEntry(16))))
        assertEquals(AudioHeader("ALAC", 24), read("b.m4a", m4a(alacEntry(24), moovFirst = true)))
    }

    @Test
    fun aacHasNoBits() = assertEquals(AudioHeader("AAC", null), read("c.m4a", m4a(mp4aEntry())))

    @Test
    fun moovAfterA64BitMdat() = assertEquals(AudioHeader("ALAC", 16), read("d.m4a", m4a(alacEntry(16), largeMdat = true)))

    @Test
    fun m4aWithoutMoovIsNull() = assertNull(read("e.m4a", box("ftyp", ascii("M4A ")) + box("mdat", ByteArray(10))))
}
