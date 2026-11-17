package dev.ricketymajor.spindle.ui.tui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ricketymajor.spindle.R

/** Visual language shared with Bare: black, text, monospace, one amber accent for what's playing. */
object Tui {
    val Background = Color(0xFF000000)
    val Text = Color(0xFFFFFFFF)
    val Secondary = Color(0xFF8A8A8A)
    val Accent = Color(0xFFFFB000)

    private val Mono = FontFamily(
        Font(R.font.jetbrains_mono_regular),
        Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    )
    val Title = TextStyle(fontFamily = Mono, fontSize = 24.sp, color = Text)
    val Item = TextStyle(fontFamily = Mono, fontSize = 20.sp, color = Text)
    val Small = TextStyle(fontFamily = Mono, fontSize = 14.sp, color = Secondary)

    val MinTouch = 48.dp
}

/** `spindle › artistas › radiohead`: the navigation path, always on top. */
@Composable
fun Breadcrumb(path: List<String>, modifier: Modifier = Modifier) {
    BasicText(
        (listOf("spindle") + path).joinToString(" › "),
        modifier,
        style = Tui.Title,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A list row: `› label`, full-width tap target of at least 48 dp. */
@Composable
fun TuiItem(label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Box(
        modifier
            .heightIn(min = Tui.MinTouch)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText("› $label", style = Tui.Item, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Controls are words, not icons: `[label]`. */
@Composable
fun TuiButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = Tui.MinTouch).clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText("[$label]", style = Tui.Item)
    }
}
