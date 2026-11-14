package app.duenorth.tasks.settings

import app.duenorth.tasks.ui.settings.OssLibrary
import app.duenorth.tasks.ui.settings.OssLicense
import app.duenorth.tasks.ui.settings.parseLicenses
import org.junit.Assert.assertEquals
import org.junit.Test

/** The open-source licenses page reads the AboutLibraries export. */
class LicensesParseTest {
    @Test
    fun librariesNameTheirLicensesByKey() {
        val json = """
            {
              "metadata": {"generated": "2026-11-14"},
              "libraries": [
                {"uniqueId": "com.squareup.okhttp3:okhttp", "artifactVersion": "5.1.0", "name": "OkHttp",
                 "licenses": ["Apache-2.0"]},
                {"uniqueId": "org.example:nameless", "artifactVersion": "1.0", "licenses": ["MIT", "unknown"]},
                {"uniqueId": "com.squareup.okhttp3:okhttp", "name": "OkHttp", "licenses": []}
              ],
              "licenses": {
                "Apache-2.0": {"name": "Apache License 2.0", "url": "https://www.apache.org/licenses/LICENSE-2.0",
                               "content": "Apache text"},
                "MIT": {"name": "MIT License", "url": "https://opensource.org/licenses/MIT"}
              }
            }
        """.trimIndent()

        assertEquals(
            listOf(
                OssLibrary(
                    "OkHttp",
                    "5.1.0",
                    listOf(
                        OssLicense("Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0", "Apache text")
                    )
                ),
                OssLibrary(
                    "org.example:nameless",
                    "1.0",
                    listOf(
                        OssLicense("MIT License", "https://opensource.org/licenses/MIT", null),
                        OssLicense("unknown", null, null)
                    )
                )
            ),
            parseLicenses(json)
        )
    }
}
