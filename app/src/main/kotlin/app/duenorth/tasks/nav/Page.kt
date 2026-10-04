package app.duenorth.tasks.nav

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.duenorth.tasks.design.motion.turnstile

/** A destination that swings in and out with the turnstile. */
@Composable
internal fun Page(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().turnstile(scope)) { content() }
}
