package app.duenorth.tasks.ui.account

import androidx.compose.runtime.Composable
import app.duenorth.tasks.design.components.MetroRadio
import app.duenorth.tasks.provider.api.ProviderKind
import app.duenorth.tasks.ui.common.serviceName

/** The services this build can offer, in the order the radio group shows them. */
internal fun offeredServices(demoAvailable: Boolean): List<ProviderKind> =
    listOfNotNull(ProviderKind.GOOGLE, ProviderKind.MICROSOFT, ProviderKind.FAKE.takeIf { demoAvailable })

internal fun serviceLabel(kind: ProviderKind): String = if (kind ==
    ProviderKind.FAKE
) {
    "demo account"
} else {
    serviceName(kind)
}

/** One radio per service: the connected one selected, each with its own caption. */
@Composable
internal fun ServiceRadios(
    services: List<ProviderKind>,
    selected: ProviderKind?,
    caption: (ProviderKind) -> String,
    enabled: (ProviderKind) -> Boolean,
    onPick: (ProviderKind) -> Unit
) {
    services.forEach { kind ->
        MetroRadio(
            selected = selected == kind,
            onClick = { onPick(kind) },
            label = serviceLabel(kind),
            caption = caption(kind),
            enabled = enabled(kind)
        )
    }
}
