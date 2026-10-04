package app.duenorth.tasks

import androidx.compose.runtime.Composable
import app.duenorth.tasks.gallery.GalleryScreen

/** Debug builds keep the Metro component gallery (T015) one menu item away. */
object DebugRoutes {
    const val GALLERY_ENABLED = true

    @Composable
    fun Gallery() {
        GalleryScreen()
    }
}
