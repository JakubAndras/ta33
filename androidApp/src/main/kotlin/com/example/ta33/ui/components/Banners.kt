package com.example.ta33.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.example.ta33.resources.Res
import com.example.ta33.resources.denik_offline_banner
import com.example.ta33.ui.theme.Ta33Theme
import org.jetbrains.compose.resources.stringResource

/** `warning-tint` banner s ⚡ ikonou: „Offline režim — záznamy se uloží lokálně". */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Ta33Theme.colors.warningTint,
        shape = Ta33Theme.radius.md,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Ta33Theme.spacing.x4,
                vertical = Ta33Theme.spacing.x3,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x2),
        ) {
            Icon(
                imageVector = Ta33Icons.Zap,
                contentDescription = null,
                tint = Ta33Theme.colors.warning,
                modifier = Modifier.size(Ta33Theme.spacing.x5),
            )
            Text(
                text = stringResource(Res.string.denik_offline_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = Ta33Theme.colors.fgDefault,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Preview
@Composable
private fun OfflineBannerPreview() {
    Ta33Theme {
        OfflineBanner(modifier = Modifier.padding(Ta33Theme.spacing.x5))
    }
}
