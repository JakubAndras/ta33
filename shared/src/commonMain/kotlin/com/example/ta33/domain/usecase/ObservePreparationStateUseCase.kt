package com.example.ta33.domain.usecase

import com.example.ta33.domain.model.PreparationState
import com.example.ta33.domain.repository.PreparationRepository
import kotlinx.coroutines.flow.Flow

class ObservePreparationStateUseCase(private val prep: PreparationRepository) {
    operator fun invoke(): Flow<PreparationState> = prep.observePreparationState()
}
