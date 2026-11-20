package app.duenorth.tasks.ui.account

import app.duenorth.tasks.provider.api.ProviderError
import app.duenorth.tasks.provider.api.ProviderKind
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class SignInErrorsTest {
    @Test
    fun authRequiredShowsTheReasonGoogleGave() {
        val google = IllegalStateException("[28444] Developer console is not set up correctly.")
        val message = signInError(ProviderKind.GOOGLE, ProviderError.AuthRequired(RuntimeException("wrap", google)))
        assertEquals(
            "Couldn't sign in to Google Tasks. Try again.\n[28444] Developer console is not set up correctly.",
            message
        )
    }

    @Test
    fun aBareAuthRequiredHasNoReasonLine() {
        assertEquals(
            "Couldn't sign in to Google Tasks. Try again.",
            signInError(ProviderKind.GOOGLE, ProviderError.AuthRequired())
        )
    }

    @Test
    fun aCauseWithoutAMessageShowsItsType() {
        class NoCredentialException : Exception()
        assertEquals("NoCredentialException", signInReason(ProviderError.AuthRequired(NoCredentialException())))
    }

    @Test
    fun otherFailuresShowTheirOwnMessage() {
        assertEquals(
            "Couldn't reach Microsoft To Do. Check your connection and try again.\ntimeout",
            signInError(ProviderKind.MICROSOFT, IOException("timeout"))
        )
    }
}
