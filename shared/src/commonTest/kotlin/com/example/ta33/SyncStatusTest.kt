package com.example.ta33

import com.example.ta33.domain.model.CollectedControl
import com.example.ta33.domain.model.Participant
import com.example.ta33.domain.model.RunSession
import com.example.ta33.domain.model.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncStatusTest {

    @Test
    fun fromDb_null_empty_unknown_fallsBackToPending() {
        assertEquals(SyncStatus.PENDING, SyncStatus.fromDb(null))
        assertEquals(SyncStatus.PENDING, SyncStatus.fromDb(""))
        assertEquals(SyncStatus.PENDING, SyncStatus.fromDb("NOPE"))
    }

    @Test
    fun fromDb_knownValues_parse() {
        assertEquals(SyncStatus.SYNCED, SyncStatus.fromDb("SYNCED"))
        assertEquals(SyncStatus.FAILED, SyncStatus.fromDb("FAILED"))
        assertEquals(SyncStatus.PENDING, SyncStatus.fromDb("PENDING"))
    }

    @Test
    fun newModels_defaultToPending() {
        assertEquals(
            SyncStatus.PENDING,
            Participant(id = "p", createdAtMillis = 1L).syncStatus,
        )
        assertEquals(
            SyncStatus.PENDING,
            RunSession(id = "r", routeId = "rt", participantId = "p").syncStatus,
        )
        assertEquals(
            SyncStatus.PENDING,
            CollectedControl(id = "c", runSessionId = "r", controlId = "ctrl", collectedAtMillis = 1L).syncStatus,
        )
    }
}
