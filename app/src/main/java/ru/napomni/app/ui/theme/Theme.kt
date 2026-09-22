package ru.napomni.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ru.napomni.app.data.model.NoteColor
import ru.napomni.app.data.model.ThemeMode

// Спокойная синяя палитра («врачебные» напоминания без агрессии).
val LightColors = lightColorScheme(
    primary = Color(0xFF4056A1),
    onPrimary = Color.White,
    secondary = Color(0xFF62929E),
    tertiary = Color(0xFFD77A61),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF9FB1E0),
    onPrimary = Color(0xFF1B2559),
    secondary = Color(0xFF8FB8C2),
    tertiary = Color(0xFFE8A08C),
)

/** Цвет метки заметки/напоминания. */
fun NoteColor.composeColor(): Color = when (this) {
    NoteColor.YELLOW -> Color(0xFFF6C445)
    NoteColor.GREEN -> Color(0xFF7FB685)
    NoteColor.BLUE -> Color(0xFF7AA2E3)
    NoteColor.PURPLE -> Color(0xFFB08BC8)
    NoteColor.PINK -> Color(0xFFE88BA8)
    NoteColor.ORANGE -> Color(0xFFF0A36B)
    NoteColor.TEAL -> Color(0xFF63B7AF)
    NoteColor.GREY -> Color(0xFFB0B0B0)
}

@Composable
fun NapomniTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
