package app.duenorth.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildConfigTest {
    @Test
    fun applicationIdIsStable() {
        assertEquals("app.duenorth.tasks", BuildConfig.APPLICATION_ID)
    }
}
