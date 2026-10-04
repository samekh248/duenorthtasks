package app.duenorth.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.duenorth.tasks.design.theme.MetroTheme

/** Release builds show an empty themed screen until the home panorama lands (M2). */
@Composable
fun StartScreen() {
    Box(Modifier.fillMaxSize().background(MetroTheme.colors.background))
}
