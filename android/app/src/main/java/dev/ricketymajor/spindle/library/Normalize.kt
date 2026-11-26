/*
 * Adapted from KISS launcher (https://github.com/Neamar/KISS), GPL-3.0, commit 14a56e2,
 * app/src/main/java/fr/neamar/kiss/normalizer/StringNormalizer.java, via Bare (search/Normalize.kt).
 * Changes: Kotlin, no position mapping (Spindle doesn't highlight matches), always lowercase.
 */
package dev.ricketymajor.spindle.library

import java.text.Normalizer

/** Lowercase, decompose (NFKD) and drop combining marks and dashes: "Línea-Uno" → "lineauno". */
fun normalize(input: CharSequence): String {
    val decomposed = Normalizer.normalize(input, Normalizer.Form.NFKD)
    val out = StringBuilder(decomposed.length)
    var i = 0
    while (i < decomposed.length) {
        val cp = decomposed.codePointAt(i)
        when (Character.getType(cp)) {
            Character.NON_SPACING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt(),
            Character.DASH_PUNCTUATION.toInt() -> Unit
            else -> out.appendCodePoint(Character.toLowerCase(cp))
        }
        i += Character.charCount(cp)
    }
    return out.toString()
}
