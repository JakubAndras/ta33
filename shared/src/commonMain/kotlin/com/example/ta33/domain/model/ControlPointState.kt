package com.example.ta33.domain.model

/**
 * Canonical logical state of a control point in the deník (FR-04).
 * Colors/visuals are a UI concern (design system) and are NOT defined here.
 *
 * - LOCKED: uncollected and not the next control in ordinal order (a later control).
 * - ACTIVE: the next control to collect (first uncollected by ordinal); also the finish
 *           step once all controls are collected but the run is not yet finished.
 * - DONE:   collected.
 * - FINISH: terminal state of the finish step once the run is finished (FR-09 QR).
 */
enum class ControlPointState { LOCKED, ACTIVE, DONE, FINISH }
