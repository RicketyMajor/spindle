package dev.ricketymajor.spindle.library

import java.io.File

/**
 * Album folders under [root] as `artist/album` relative paths, sorted ignoring case.
 * Hidden entries (Syncthing's `.stfolder`, `.stversions`) are skipped. Null if [root] is missing.
 */
// ponytail: F0 only lists folders; the real indexer (TagLib, incremental scan) replaces this in F1.
fun listLibraryFolders(root: File): List<String>? {
    if (!root.isDirectory) return null
    return root.visibleDirs()
        .flatMap { artist -> artist.visibleDirs().map { "${artist.name}/${it.name}" } }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
}

private fun File.visibleDirs(): List<File> =
    listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") }
