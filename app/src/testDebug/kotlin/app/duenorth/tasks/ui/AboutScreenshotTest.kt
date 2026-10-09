package app.duenorth.tasks.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.design.theme.Accent
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.settings.ThemeMode
import app.duenorth.tasks.settings.ThemeSettings
import app.duenorth.tasks.ui.settings.AboutPage
import app.duenorth.tasks.ui.settings.BisonCanvas
import app.duenorth.tasks.ui.settings.BisonTimeline
import app.duenorth.tasks.ui.settings.SettingsContent
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The about page and the bison easter egg, light and dark, for review (CI uploads them). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class AboutScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aboutLight() = about(dark = false)

    @Test
    fun aboutDark() = about(dark = true)

    @Test
    fun bisonLookingLight() = aboutWithBison(BisonTimeline.LOOK_HOLD_START + 200f, "looking", dark = false)

    @Test
    fun bisonLookingDark() = aboutWithBison(BisonTimeline.LOOK_HOLD_START + 200f, "looking", dark = true)

    @Test
    fun bisonWalkingLight() = aboutWithBison(BisonTimeline.WALK_IN_MS * 0.55f, "walking", dark = false)

    @Test
    fun bisonWalkingDark() = aboutWithBison(BisonTimeline.WALK_IN_MS * 0.55f, "walking", dark = true)

    /**
     * Every frame of the walk at 25 fps, for an animated preview. Off unless BISON_FRAMES is set,
     * since CI doesn't need 200 images.
     */
    @Test
    fun bisonFrames() {
        assumeTrue(System.getenv("BISON_FRAMES") != null)
        val at = mutableFloatStateOf(0f)
        compose.setContent {
            MetroTheme(darkTheme = false, accent = Accent.Magenta) {
                BisonCanvas(
                    { at.floatValue },
                    Modifier.fillMaxWidth().height(112.dp).background(MetroTheme.colors.background)
                )
            }
        }
        var frame = 0
        while (at.floatValue <= BisonTimeline.TOTAL_MS) {
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/bison-frames/frame_%04d.png".format(frame))
            frame++
            at.floatValue += 40f
        }
    }

    private fun about(dark: Boolean) {
        show(dark, Accent.Magenta) {
            val theme = ThemeSettings(ThemeMode.SYSTEM, Accent.Magenta)
            SettingsContent(theme, onMode = {}, onAccent = {}, initialPage = 2)
        }
        snap("about", dark)
    }

    private fun aboutWithBison(ms: Float, name: String, dark: Boolean) {
        show(dark, Accent.Magenta) { AboutPage(bisonPreviewMs = ms) }
        snap("bison_$name", dark)
    }

    private fun show(dark: Boolean, accent: Accent, content: @Composable () -> Unit) {
        compose.setContent { MetroTheme(darkTheme = dark, accent = accent) { content() } }
    }

    private fun snap(name: String, dark: Boolean) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(
            "build/outputs/roborazzi/about/${name}_${if (dark) "dark" else "light"}.png"
        )
    }
}
