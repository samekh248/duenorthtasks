package app.duenorth.tasks.design

import app.duenorth.tasks.design.components.dotPosition
import app.duenorth.tasks.design.components.moveTo
import app.duenorth.tasks.design.theme.Accent
import app.duenorth.tasks.design.theme.AccentShades
import app.duenorth.tasks.design.theme.Contrast
import app.duenorth.tasks.design.theme.MetroColors
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentTest {
    @Test
    fun thereAreTwentyTwoAccentsAndMagentaIsTheDefault() {
        assertEquals(22, Accent.entries.size)
        assertEquals(Accent.Magenta, Accent.Default)
    }

    @Test
    fun everyAccentCaptionMeetsFourAndAHalfToOneInBothThemes() {
        for (theme in listOf(MetroColors.Light, MetroColors.Dark)) {
            for (accent in Accent.entries) {
                val ratio = Contrast.ratio(accent.text(theme.isDark), theme.background)
                assertTrue("$accent on ${if (theme.isDark) "dark" else "light"}: $ratio", ratio >= Contrast.MIN_TEXT)
            }
        }
    }

    @Test
    fun everyShadeCaptionStaysReadable() {
        for (theme in listOf(MetroColors.Light, MetroColors.Dark)) {
            for (accent in Accent.entries) {
                for (step in AccentShades.steps) {
                    val text = AccentShades.colors(accent, step, theme).text
                    val ratio = Contrast.ratio(text, theme.background)
                    assertTrue("$accent step $step dark=${theme.isDark}: $ratio", ratio >= Contrast.MIN_TEXT - 0.05f)
                }
            }
        }
    }

    @Test
    fun stepZeroIsTheAccentItself() {
        val accent = Accent.Teal
        assertEquals(accent.fill(dark = false), AccentShades.mix(accent.fill(dark = false), 0))
    }

    @Test
    fun movingToAShorterMonthClampsTheDay() {
        assertEquals(LocalDate.of(2027, 2, 28), LocalDate.of(2027, 1, 31).moveTo(month = 2))
        assertEquals(LocalDate.of(2028, 2, 29), LocalDate.of(2027, 2, 28).moveTo(year = 2028).plusDays(1))
    }

    @Test
    fun progressDotsCrossTheWholeWidth() {
        assertEquals(0f, dotPosition(0f), 1e-6f)
        assertEquals(1f, dotPosition(1f), 1e-6f)
        assertEquals(0.5f, dotPosition(0.5f), 1e-6f)
    }
}
