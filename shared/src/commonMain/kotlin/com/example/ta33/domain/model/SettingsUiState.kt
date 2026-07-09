package com.example.ta33.domain.model

data class SettingsUiState(
    val notificationsEnabled: Boolean = true,
    val organizerContact: OrganizerContact? = null,
    val faq: List<FaqItem> = emptyList(),
    val loading: Boolean = true,
)
