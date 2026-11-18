package dev.ricketymajor.spindle.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LibraryFoldersTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun listsArtistAlbumFoldersSortedIgnoringCase() {
        val root = tmp.newFolder("Biblioteca")
        File(root, "beta/2001 - B").mkdirs()
        File(root, "Alfa/2019 - Uno").mkdirs()
        File(root, "Alfa/Sencillos").mkdirs()
        File(root, "Alfa/2019 - Uno/01 x.flac").createNewFile() // files are not folders
        File(root, "loose.mp3").createNewFile()

        assertEquals(
            listOf("Alfa/2019 - Uno", "Alfa/Sencillos", "beta/2001 - B"),
            listLibraryFolders(root),
        )
    }

    @Test
    fun skipsHiddenSyncthingFolders() {
        val root = tmp.newFolder("Biblioteca")
        File(root, ".stfolder").mkdirs()
        File(root, ".stversions/Old/Album").mkdirs()
        File(root, "Alfa/.hidden").mkdirs()
        File(root, "Alfa/Album").mkdirs()

        assertEquals(listOf("Alfa/Album"), listLibraryFolders(root))
    }

    @Test
    fun missingRootIsNull() {
        assertNull(listLibraryFolders(File(tmp.root, "nope")))
    }
}
