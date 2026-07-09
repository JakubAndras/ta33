package com.example.ta33.presentation

import com.example.ta33.FakeAppPreferencesRepository
import com.example.ta33.data.repository.StaticAppInfoRepository
import com.example.ta33.domain.usecase.ObserveNotificationsEnabledUseCase
import com.example.ta33.domain.usecase.SetNotificationsEnabledUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var prefsRepo: FakeAppPreferencesRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        prefsRepo = FakeAppPreferencesRepository()
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SettingsViewModel(
        ObserveNotificationsEnabledUseCase(prefsRepo),
        SetNotificationsEnabledUseCase(prefsRepo),
        StaticAppInfoRepository(),
    )

    @Test
    fun initialState_carriesOrganizerAndFaq() {
        val vm = viewModel()
        assertTrue(vm.state.value.organizerContact != null)
        assertTrue(vm.state.value.faq.isNotEmpty())
    }

    @Test
    fun setNotificationsEnabled_persistsAndReEmits() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.state.value.notificationsEnabled)

        vm.setNotificationsEnabled(false)
        advanceUntilIdle()

        assertFalse(prefsRepo.getNotificationsEnabled())
        assertFalse(vm.state.value.notificationsEnabled)
        assertEquals(false, vm.state.value.loading)
    }
}
