package dev.ricketymajor.spindle

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.ricketymajor.spindle.library.listLibraryFolders
import dev.ricketymajor.spindle.ui.screens.FoldersScreen
import java.io.File

class MainActivity : ComponentActivity() {
    // Re-checked on every resume: the user grants access in system settings and comes back.
    private var hasAccess by mutableStateOf(false)

    private val libraryRoot = File(Environment.getExternalStorageDirectory(), "Music/Spindle/Biblioteca")

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // ponytail: synchronous listing on the main thread is fine for F0's few folders; F1's indexer runs in background.
            val folders = remember(hasAccess) { if (hasAccess) listLibraryFolders(libraryRoot) else null }
            FoldersScreen(hasAccess, folders, ::requestAccess)
        }
    }

    override fun onResume() {
        super.onResume()
        hasAccess = Environment.isExternalStorageManager()
    }

    private fun requestAccess() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")),
        )
    }
}
