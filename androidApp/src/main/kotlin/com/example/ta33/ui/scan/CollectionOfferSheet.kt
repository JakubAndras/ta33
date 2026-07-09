package com.example.ta33.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.ta33.domain.model.CollectionCandidate
import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.presentation.CollectionOutcome
import com.example.ta33.resources.Res
import com.example.ta33.resources.collect_action
import com.example.ta33.resources.collect_already
import com.example.ta33.resources.collect_offer
import com.example.ta33.resources.collect_out_of_range
import com.example.ta33.ui.components.Overline
import com.example.ta33.ui.components.PaperCard
import com.example.ta33.ui.components.PrimaryButton
import com.example.ta33.ui.format.formatMeters
import com.example.ta33.ui.theme.Ta33Theme
import org.jetbrains.compose.resources.stringResource

/**
 * Spodní nabídka sběru kontroly v dosahu (FR-08). Ukáže se, když
 * [com.example.ta33.presentation.ControlCollectionViewModel] vystaví `candidate`.
 * „Sebrat" volá `confirm()`; během zápisu ([isCollecting]) je místo tlačítka spinner.
 * [lastResult] slouží jen pro nekritické hlášky pokusu (Už sebráno / Mimo dosah);
 * úspěch ([CollectionOutcome.JustCollected]) řeší celoobrazovkové Splnění jinde.
 */
@Composable
fun CollectionOfferSheet(
    candidate: CollectionCandidate,
    isCollecting: Boolean,
    onCollect: () -> Unit,
    modifier: Modifier = Modifier,
    lastResult: CollectionOutcome? = null,
) {
    PaperCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x2)) {
            Overline(text = stringResource(Res.string.collect_offer))
            Text(
                text = "KP-${candidate.control.ordinal} · ${candidate.control.name}",
                style = MaterialTheme.typography.titleLarge,
                color = Ta33Theme.colors.fgStrong,
            )
            Text(
                text = formatMeters(candidate.distanceMeters),
                style = MaterialTheme.typography.bodyMedium,
                color = Ta33Theme.colors.fgMuted,
            )
            if (isCollecting) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Ta33Theme.spacing.x9)
                        .padding(top = Ta33Theme.spacing.x2),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                PrimaryButton(
                    text = stringResource(Res.string.collect_action),
                    onClick = onCollect,
                    modifier = Modifier.padding(top = Ta33Theme.spacing.x2),
                )
            }
            collectResultMessage(lastResult)?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ta33Theme.colors.fgMuted,
                )
            }
        }
    }
}

/** Nekritická hláška neúspěšného pokusu o sběr. Null = nic k zobrazení (nebo úspěch). */
@Composable
private fun collectResultMessage(result: CollectionOutcome?): String? = when (result) {
    CollectionOutcome.AlreadyCollected -> stringResource(Res.string.collect_already)
    CollectionOutcome.OutOfRange -> stringResource(Res.string.collect_out_of_range)
    else -> null
}

private val previewCandidate = CollectionCandidate(
    control = ControlPoint(
        id = "kp-02",
        routeId = "TA33",
        ordinal = 2,
        name = "Sloní pramen",
        location = GeoPoint(latitude = 50.6, longitude = 16.1),
    ),
    distanceMeters = 34.0,
    accuracyMeters = 8.0,
    atLocation = GeoPoint(latitude = 50.6, longitude = 16.1),
)

@Preview
@Composable
private fun CollectionOfferSheetPreview() {
    Ta33Theme {
        CollectionOfferSheet(
            candidate = previewCandidate,
            isCollecting = false,
            onCollect = {},
            modifier = Modifier.padding(Ta33Theme.spacing.x5),
        )
    }
}

@Preview
@Composable
private fun CollectionOfferSheetCollectingPreview() {
    Ta33Theme {
        CollectionOfferSheet(
            candidate = previewCandidate,
            isCollecting = true,
            onCollect = {},
            modifier = Modifier.padding(Ta33Theme.spacing.x5),
        )
    }
}

@Preview
@Composable
private fun CollectionOfferSheetOutOfRangePreview() {
    Ta33Theme {
        CollectionOfferSheet(
            candidate = previewCandidate,
            isCollecting = false,
            onCollect = {},
            modifier = Modifier.padding(Ta33Theme.spacing.x5),
            lastResult = CollectionOutcome.OutOfRange,
        )
    }
}
