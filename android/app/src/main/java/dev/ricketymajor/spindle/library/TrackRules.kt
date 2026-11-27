package dev.ricketymajor.spindle.library

/** What the path says when tags are missing: `Artista/AAAA - Álbum/NN Título.ext` (SPEC 4.2, decision 11). */
data class PathGuess(val artist: String, val album: String, val year: Int?, val trackNo: Int?, val title: String)

private val NUMBERED = Regex("""^(\d{1,3})[ .\-_]+(.+)$""")
private val DATED = Regex("""^(\d{4}) - (.+)$""")
private val YEAR = Regex("""\d{4}""")

fun guessFromPath(relativePath: String): PathGuess {
    val parts = relativePath.split('/')
    val name = parts.last().substringBeforeLast('.')
    val folder = parts.getOrNull(parts.size - 2).orEmpty()
    val numbered = NUMBERED.find(name)
    val dated = DATED.find(folder)
    return PathGuess(
        artist = parts.getOrNull(parts.size - 3).orEmpty(),
        album = dated?.groupValues?.get(2) ?: folder,
        year = dated?.groupValues?.get(1)?.toInt(),
        trackNo = numbered?.groupValues?.get(1)?.toInt(),
        title = numbered?.groupValues?.get(2) ?: name,
    )
}

/** First 4-digit run: `2019`, `2019-05-01`, `2019/05`. */
fun yearOf(date: String?): Int? = date?.let { YEAR.find(it)?.value?.toInt() }

/** `3/10` → 3. */
fun numberOf(text: String?): Int? = text?.substringBefore('/')?.trim()?.toIntOrNull()
