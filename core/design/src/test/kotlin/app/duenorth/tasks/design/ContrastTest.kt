package app.duenorth.tasks.design

import app.duenorth.tasks.design.theme.Accent
import app.duenorth.tasks.design.theme.AccentShades
import app.duenorth.tasks.design.theme.Contrast
import app.duenorth.tasks.design.theme.MetroColors
import org.junit.Assert.assertTrue
import org.junit.Test

/** T064: contrast for every accent and shade, and the grey, red and outline colors, in both themes. */
class ContrastTest {
    @Test
    fun everyAccentFillKeepsItsCountReadableInBothThemes() {
        for (theme in listOf(MetroColors.Light, MetroColors.Dark)) {
            for (accent in Accent.entries) {
                for (step in AccentShades.steps) {
                    val shade = AccentShades.colors(accent, step, theme)
                    val ratio = Contrast.ratio(shade.onFill, shade.fill)
                    assertTrue("$accent step $step dark=${theme.isDark}: $ratio", ratio >= Contrast.MIN_TEXT)
                }
            }
        }
    }

    @Test
    fun greyAndOverdueTextMeetFourAndAHalfToOne() {
        for (theme in listOf(MetroColors.Light, MetroColors.Dark)) {
            val name = if (theme.isDark) "dark" else "light"
            for ((role, color) in listOf(
                "foreground" to theme.foreground,
                "secondary" to theme.secondary,
                "overdue" to theme.overdue
            )) {
                val ratio = Contrast.ratio(color, theme.background)
                assertTrue("$role on $name: $ratio", ratio >= Contrast.MIN_TEXT)
            }
            // Outlines (check boxes, chips, disabled buttons) are graphics: 3:1 (WCAG 1.4.11).
            val outline = Contrast.ratio(theme.outline, theme.background)
            assertTrue("outline on $name: $outline", outline >= MIN_GRAPHIC)
        }
    }

    private companion object {
        const val MIN_GRAPHIC = 3f
    }
}
