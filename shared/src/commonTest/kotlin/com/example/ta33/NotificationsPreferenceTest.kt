package com.example.ta33

import com.example.ta33.domain.usecase.ObserveNotificationsEnabledUseCase
import com.example.ta33.domain.usecase.SetNotificationsEnabledUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationsPreferenceTest {

    @Test
    fun defaultIsTrue() = runTest {
        val repo = FakeAppPreferencesRepository()
        assertTrue(ObserveNotificationsEnabledUseCase(repo)().first())
    }

    @Test
    fun setFalse_persistsAndReEmitsFalse() = runTest {
        val repo = FakeAppPreferencesRepository()
        SetNotificationsEnabledUseCase(repo)(false)

        assertFalse(repo.getNotificationsEnabled())
        assertEquals(false, ObserveNotificationsEnabledUseCase(repo)().first())
    }

    @Test
    fun setTrueAgain_flipsBack() = runTest {
        val repo = FakeAppPreferencesRepository(initialNotificationsEnabled = false)
        val set = SetNotificationsEnabledUseCase(repo)
        set(true)
        assertTrue(ObserveNotificationsEnabledUseCase(repo)().first())
    }
}
