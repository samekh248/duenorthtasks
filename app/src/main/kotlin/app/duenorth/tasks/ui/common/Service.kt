package app.duenorth.tasks.ui.common

import app.duenorth.tasks.provider.api.ProviderKind

/** The connected service as the UI names it ("Details sync as the task's notes in ..."). */
fun serviceName(kind: ProviderKind?): String = when (kind) {
    ProviderKind.GOOGLE -> "Google Tasks"
    ProviderKind.MICROSOFT -> "Microsoft To Do"
    ProviderKind.FAKE -> "the demo account"
    null -> ""
}
