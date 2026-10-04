package app.duenorth.tasks

import androidx.compose.runtime.Composable
import app.duenorth.tasks.gallery.GalleryScreen

/** Debug builds open the Metro component gallery (T015) until the home panorama lands (M2). */
@Composable
fun StartScreen() {
    GalleryScreen()
}
