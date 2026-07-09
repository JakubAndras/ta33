package com.example.ta33.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ta33.presentation.navigation.AppUiState
import com.example.ta33.presentation.navigation.TopLevelDestination
import com.example.ta33.ui.denik.DenikScreen
import com.example.ta33.ui.theme.Ta33Theme

/**
 * Tabbed shell (READY stav): tři taby přepínané stavově (bez back-stacku, nav contract).
 * Bottom nav je plovoucí pill nad obsahem; obsah dostane spodní padding, aby ho pill nepřekrýval.
 * Scan FAB se ukáže jen při aktivním běhu (`activeRunId != null`).
 */
@Composable
fun MainShell(
    app: AppUiState,
    onScan: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(TopLevelDestination.DENIK) }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (app.activeRunId != null) {
                ScanFab(onClick = onScan, modifier = Modifier.navigationBarsPadding())
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = Ta33Theme.spacing.x10 + Ta33Theme.spacing.x4),
            ) {
                when (tab) {
                    TopLevelDestination.DENIK -> DenikScreen(onDownload = onDownload)
                    TopLevelDestination.MAPA -> StubScreen(title = "Mapa")
                    TopLevelDestination.PREHLED -> StubScreen(title = "Přehled")
                }
            }
            Ta33BottomNav(
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
