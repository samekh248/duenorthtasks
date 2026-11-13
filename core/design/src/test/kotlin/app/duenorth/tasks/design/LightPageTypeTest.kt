package app.duenorth.tasks.design

import app.duenorth.tasks.design.theme.Contrast
import app.duenorth.tasks.design.theme.MetroColors
import app.duenorth.tasks.design.theme.MetroTypography
import app.duenorth.tasks.design.theme.MetroWeights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner, 2026-11-12: light-mode text read washed out, so a light page sets its type one weight heavier. */
class LightPageTypeTest {
    @Test
    fun lightPageMovesEachThinRoleOneWeightHeavier() {
        val dark = MetroTypography.Default
        val light = dark.forLightPage()
        assertEquals(MetroWeights.Light, dark.header.fontWeight)
        assertEquals(MetroWeights.Semilight, light.header.fontWeight)
        assertEquals(MetroWeights.Semilight, light.panoramaTitle.fontWeight)
        assertEquals(MetroWeights.Semilight, light.listName.fontWeight)
        assertEquals(MetroWeights.Regular, light.subheader.fontWeight)
        assertEquals(MetroWeights.Regular, light.body.fontWeight)
        assertEquals(MetroWeights.Semibold, light.pageTitle.fontWeight)
    }

    @Test
    fun lightGreyReadsAsStronglyAsDarkGrey() {
        val light = Contrast.ratio(MetroColors.Light.secondary, MetroColors.Light.background)
        assertTrue("light grey $light", light >= 7f)
    }
}
