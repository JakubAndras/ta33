package com.example.ta33.dev

import com.example.ta33.presentation.ProfileMock
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * DEV / TESTING ONLY - mutable overrides for the Profil „Sandbox" panel (UI-12).
 *
 * Holds dev-only state that has no DB backing. Currently the „paid" flag: the release build reads
 * the static [ProfileMock.paid], the DEBUG Sandbox panel overrides it here so the payment status on
 * the start-number card can be toggled. NOT persisted - resets to the release default on relaunch
 * (DB-backed states like run/preparation survive on their own). Single instance via Koin.
 */
class SandboxState {
    val paid = MutableStateFlow(ProfileMock.paid)
}
