package com.example.ta33.presentation

/**
 * DEV Sandbox panel state (UI-12) - the current value of each toggle, derived from the real app
 * state so it stays consistent even when a state changes elsewhere (e.g. a real finish scan).
 *
 * @param paid       payment status shown on the start-number card (dev override of [ProfileMock.paid]).
 * @param naTrase    an unfinished run exists → app is "on-route" (scan capsule, Deník na trase).
 * @param downloaded offline package present → readiness READY (else app shows Příprava).
 * @param finished   the latest run has a finish timestamp → Deník "Hotovo".
 * @param runExists  any run exists - gates the "Běh dokončen" toggle (meaningless without a run).
 */
data class SandboxUiState(
    val paid: Boolean = true,
    val naTrase: Boolean = false,
    val downloaded: Boolean = true,
    val finished: Boolean = false,
    val runExists: Boolean = false,
)
