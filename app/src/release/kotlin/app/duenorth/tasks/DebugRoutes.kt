package app.duenorth.tasks

import androidx.compose.runtime.Composable

/** Release builds ship without the component gallery. */
object DebugRoutes {
    const val GALLERY_ENABLED = false

    @Composable
    fun Gallery() = Unit
}
