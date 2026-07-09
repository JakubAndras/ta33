package com.example.ta33

import androidx.compose.runtime.Composable
import com.example.ta33.ui.denik.DenikScreen
import com.example.ta33.ui.theme.Ta33Theme

@Composable
fun App() {
    Ta33Theme {
        DenikScreen(onDownload = { /* TODO: navigace na Preparation (app-shell) */ })
    }
}
