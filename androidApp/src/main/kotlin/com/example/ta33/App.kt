package com.example.ta33

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.ta33.ui.theme.Ta33Theme

@Composable
@Preview
fun App() {
    Ta33Theme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize()
                .padding(Ta33Theme.spacing.x6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Ta33Theme.spacing.x4, Alignment.CenterVertically),
        ) {
            Text(
                text = "TA33",
                style = MaterialTheme.typography.displayLarge,
                color = Ta33Theme.colors.fgStrong,
            )
        }
    }
}
