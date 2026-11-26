package dev.ricketymajor.spindle.library

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizeTest {
    @Test
    fun stripsAccentsLikeKiss() {
        mapOf(
            "è" to "e", "é" to "e", "ê" to "e", "ë" to "e", "û" to "u", "ù" to "u", "ï" to "i", "î" to "i",
            "à" to "a", "â" to "a", "ô" to "o", "ä" to "a", "ö" to "o", "ü" to "u", "Å" to "a", "Æ" to "æ", "ﬁ" to "fi",
        ).forEach { (input, expected) -> assertEquals(input, expected, normalize(input)) }
    }

    @Test
    fun spanishNamesDashesAndSlashes() {
        assertEquals("senales", normalize("Señales"))
        assertEquals("nandu", normalize("Ñandú"))
        assertEquals("hiphop", normalize("Hip-Hop"))
        assertEquals("ac/dc", normalize("AC/DC")) // other punctuation stays; Search splits words on it
    }
}
