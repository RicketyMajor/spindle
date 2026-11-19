package dev.ricketymajor.spindle.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ricketymajor.spindle.ui.tui.Breadcrumb
import dev.ricketymajor.spindle.ui.tui.Tui
import dev.ricketymajor.spindle.ui.tui.TuiButton
import dev.ricketymajor.spindle.ui.tui.TuiItem

/** F0 screen: proves file access by listing the library's album folders. */
@Composable
fun FoldersScreen(hasAccess: Boolean, folders: List<String>?, onRequestAccess: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Tui.Background)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Breadcrumb(listOf("carpetas"))
        Spacer(Modifier.height(16.dp))
        when {
            !hasAccess -> {
                BasicText("spindle necesita acceso a todos los archivos para leer tu biblioteca.", style = Tui.Small)
                Spacer(Modifier.height(8.dp))
                TuiButton("conceder acceso", onRequestAccess)
            }
            folders == null -> BasicText("no hay biblioteca en Music/Spindle/Biblioteca", style = Tui.Small)
            folders.isEmpty() -> BasicText("la biblioteca está vacía", style = Tui.Small)
            else -> {
                BasicText("${folders.size} carpetas", style = Tui.Small)
                LazyColumn {
                    items(folders) { TuiItem(it, Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}
