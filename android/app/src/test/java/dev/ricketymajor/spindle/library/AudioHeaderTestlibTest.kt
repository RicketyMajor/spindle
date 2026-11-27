package dev.ricketymajor.spindle.library

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class AudioHeaderTestlibTest {
    private val lib = File("../../testlib/Biblioteca") // unit tests run from android/app

    @Test
    fun readsTheFfmpegLibrary() {
        assumeTrue("run python scripts/make_testlib.py first", lib.isDirectory)
        val got = lib.walkTopDown().filter { it.isFile }.associate { it.name to readAudioHeader(it) }
        assertEquals(AudioHeader("FLAC", 16), got["01 Primera Señal.flac"])
        assertEquals(AudioHeader("WAV", 24), got["01 Silencio Largo.wav"])
        assertEquals(AudioHeader("ALAC", 16), got["02 Interludio.m4a"])
        assertEquals(AudioHeader("AAC", null), got["01 Tema de Apertura.m4a"])
        assertEquals(AudioHeader("VORBIS", null), got["03 Créditos.ogg"])
    }
}
