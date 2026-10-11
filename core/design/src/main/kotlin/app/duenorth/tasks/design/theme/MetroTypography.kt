package app.duenorth.tasks.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.duenorth.tasks.design.R

/** Segoe's weights; Selawik ships the same four (plus bold, unused by the ramp). */
object MetroWeights {
    val Light = FontWeight.Light
    val Semilight = FontWeight(350)
    val Regular = FontWeight.Normal
    val Semibold = FontWeight.SemiBold
}

/**
 * The type ramp from research R2. Typography is the interface in Metro, so screens use these
 * tokens rather than ad hoc sizes. Colors are applied by the caller from [MetroColors].
 */
@Immutable
data class MetroTypography(
    /** "due north" across the home panorama. */
    val panoramaTitle: TextStyle,
    /** Panorama sections: "today", "lists", "stats". */
    val sectionHeader: TextStyle,
    /** Small uppercase app name above a page title ("DUE NORTH"). */
    val pageTitle: TextStyle,
    /** Page titles and pivot headers. */
    val header: TextStyle,
    /** Task title on the detail page. */
    val detailTitle: TextStyle,
    /** List names next to their tiles. */
    val listName: TextStyle,
    /** Task titles in lists, section titles. */
    val subheader: TextStyle,
    /** Details, settings rows, buttons. */
    val body: TextStyle,
    /** The two-line details preview under a task title. */
    val preview: TextStyle,
    /** Due dates and secondary text. */
    val caption: TextStyle
) {
    companion object {
        fun create(family: FontFamily = MetroFonts.family): MetroTypography = MetroTypography(
            panoramaTitle = TextStyle(
                fontFamily = family,
                fontWeight = MetroWeights.Light,
                fontSize = 118.sp,
                letterSpacing = (-0.04).em,
                lineHeight = 118.sp
            ),
            sectionHeader = TextStyle(fontFamily = family, fontWeight = MetroWeights.Light, fontSize = 40.sp),
            pageTitle = TextStyle(
                fontFamily = family,
                fontWeight = MetroWeights.Semibold,
                fontSize = 13.sp,
                letterSpacing = 0.06.em
            ),
            header = TextStyle(fontFamily = family, fontWeight = MetroWeights.Light, fontSize = 52.sp),
            detailTitle = TextStyle(fontFamily = family, fontWeight = MetroWeights.Light, fontSize = 38.sp),
            listName = TextStyle(fontFamily = family, fontWeight = MetroWeights.Light, fontSize = 24.sp),
            subheader = TextStyle(fontFamily = family, fontWeight = MetroWeights.Semilight, fontSize = 20.sp),
            body = TextStyle(fontFamily = family, fontWeight = MetroWeights.Regular, fontSize = 15.sp),
            preview = TextStyle(fontFamily = family, fontWeight = MetroWeights.Regular, fontSize = 14.sp),
            caption = TextStyle(fontFamily = family, fontWeight = MetroWeights.Regular, fontSize = 13.sp)
        )

        val Default: MetroTypography = create()
    }
}

/**
 * Font family for the whole app: Selawik, Microsoft's open Segoe UI stand-in (research R2),
 * bundled in res/font under the SIL Open Font License (assets/licenses/selawik_OFL.txt).
 */
object MetroFonts {
    val family: FontFamily = FontFamily(
        Font(R.font.selawik_light, MetroWeights.Light),
        Font(R.font.selawik_semilight, MetroWeights.Semilight),
        Font(R.font.selawik_regular, MetroWeights.Regular),
        Font(R.font.selawik_semibold, MetroWeights.Semibold),
        Font(R.font.selawik_bold, FontWeight.Bold)
    )
}
