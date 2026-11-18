package app.duenorth.tasks.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.duenorth.tasks.R
import app.duenorth.tasks.design.components.MetroLinkifiedText
import app.duenorth.tasks.design.components.MetroText
import app.duenorth.tasks.design.motion.metroTilt
import app.duenorth.tasks.design.theme.MetroDimens
import app.duenorth.tasks.design.theme.MetroTheme
import app.duenorth.tasks.ui.common.PageHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One license a library ships under. [text] is the full license when the build could bundle it. */
data class OssLicense(val name: String, val url: String?, val text: String?)

/** A library the app is built on, as the AboutLibraries plugin lists it at build time. */
data class OssLibrary(val name: String, val version: String?, val licenses: List<OssLicense>)

/**
 * The open-source licenses page, opened from about, credits. It lists every library in the app with its version
 * and license, read from `res/raw/aboutlibraries.json` (generated from the dependency graph at build time, so it
 * never goes stale), plus the Selawik font. Tapping a library shows its license text. Read-only, so no app bar.
 */
@Composable
fun LicensesScreen() {
    val context = LocalContext.current
    val libraries by produceState<List<OssLibrary>?>(null) {
        value = withContext(Dispatchers.IO) { loadLicenses(context) }
    }
    LicensesContent(libraries.orEmpty())
}

@Composable
fun LicensesContent(libraries: List<OssLibrary>, initiallyOpen: String? = null) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(MetroTheme.colors.background)
            .statusBarsPadding()
            .testTag("licenses"),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item(key = "header", contentType = "header") { PageHeader("licenses") }
        item(key = "intro", contentType = "intro") {
            MetroText(
                "Due North Tasks is built on these open-source libraries. Tap one to read its license.",
                MetroTheme.typography.body,
                Modifier.padding(horizontal = MetroDimens.Gutter, vertical = 16.dp),
                color = MetroTheme.colors.secondary
            )
        }
        items(libraries, key = { it.name }, contentType = { "library" }) { library ->
            LibraryRow(library, expanded = open == library.name) {
                open = if (open == library.name) null else library.name
            }
        }
        item(key = "end", contentType = "end") { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun LibraryRow(library: OssLibrary, expanded: Boolean, onToggle: () -> Unit) {
    val type = MetroTheme.typography
    val colors = MetroTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .metroTilt()
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = if (expanded) "license shown" else "license hidden" }
            .heightIn(min = MetroDimens.TouchTarget)
            .padding(horizontal = MetroDimens.Gutter, vertical = 8.dp)
    ) {
        MetroText(library.name, type.body)
        val caption = listOfNotNull(library.version, library.licenses.joinToString { it.name }.ifEmpty { null })
        MetroText(caption.joinToString(" · "), type.caption, color = colors.secondary)
        if (expanded) {
            library.licenses.forEach { license ->
                MetroLinkifiedText(
                    license.text ?: license.url ?: license.name,
                    type.caption,
                    Modifier.padding(top = 8.dp),
                    color = colors.secondary
                )
            }
        }
    }
}

private fun loadLicenses(context: Context): List<OssLibrary> {
    val json = context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
    val font = context.assets.open(SELAWIK_LICENSE).bufferedReader().use { it.readText() }
    val selawik = OssLibrary("Selawik", null, listOf(OssLicense("SIL Open Font License 1.1", null, font)))
    return (parseLicenses(json) + selawik).sortedBy { it.name.lowercase() }
}

private const val SELAWIK_LICENSE = "licenses/selawik_OFL.txt"

private val LicenseJson = Json { ignoreUnknownKeys = true }

/** Reads the AboutLibraries export: `libraries[]` naming license keys that `licenses{}` spells out. */
internal fun parseLicenses(json: String): List<OssLibrary> {
    val root = LicenseJson.parseToJsonElement(json).jsonObject
    val licenses = (root["licenses"] as? JsonObject).orEmpty()
    val libraries = (root["libraries"] as? JsonArray).orEmpty()
    return libraries
        .map { it.jsonObject }
        .map { library ->
            val keys = (library["licenses"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
            OssLibrary(
                name = library.text("name") ?: library.text("uniqueId").orEmpty(),
                version = library.text("artifactVersion"),
                licenses = keys.map { key ->
                    val license = licenses[key] as? JsonObject
                    OssLicense(
                        name = license?.text("name") ?: key,
                        url = license?.text("url"),
                        text = license?.text("content")
                    )
                }
            )
        }
        .filter { it.name.isNotBlank() }
        .distinctBy { it.name }
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)
    ?.contentOrNull
    ?.takeIf { it.isNotBlank() }
