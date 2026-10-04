package app.duenorth.tasks.design.motion

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * False when the user turned animations off ("remove animations", or animator duration scale 0).
 * Compose's own animations already finish instantly then; this lets gesture-driven effects such as
 * tilt and the progress dots skip themselves too (research R4).
 */
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}
