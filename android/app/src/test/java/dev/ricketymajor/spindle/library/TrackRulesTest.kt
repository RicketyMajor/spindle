package dev.ricketymajor.spindle.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackRulesTest {
    @Test
    fun guessesFromTheLibraryLayout() {
        assertEquals(PathGuess("Ensayo Uno", "Señales", 2019, 1, "Primera Señal"), guessFromPath("Ensayo Uno/2019 - Señales/01 Primera Señal.flac"))
        assertEquals(PathGuess("Ensayo Uno", "Sencillos", null, null, "Tono Suelto"), guessFromPath("Ensayo Uno/Sencillos/Tono Suelto.opus"))
        assertEquals(PathGuess("Artista 000", "Luz luz 000", 1970, 10, "Señal hielo"), guessFromPath("Grande/Artista 000/1970 - Luz luz 000/10 Señal hielo.flac"))
        assertEquals(PathGuess("", "", null, null, "suelto"), guessFromPath("suelto.mp3"))
        assertEquals("2001 A Space", guessFromPath("a/b/2001 A Space.mp3").title) // 4 digits are not a track number
    }

    @Test
    fun years() {
        assertEquals(1973, yearOf("1973-03-01"))
        assertEquals(2019, yearOf("2019"))
        assertNull(yearOf("s/f"))
        assertNull(yearOf(null))
    }

    @Test
    fun numbers() {
        assertEquals(3, numberOf("3/10"))
        assertEquals(3, numberOf(" 3 "))
        assertNull(numberOf("A"))
        assertNull(numberOf(null))
    }
}
