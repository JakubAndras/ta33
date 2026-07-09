package com.example.ta33.domain.usecase

import com.example.ta33.domain.model.Participant
import com.example.ta33.domain.repository.ParticipantRepository

class EnsureLocalParticipantUseCase(
    private val participantRepo: ParticipantRepository,
) {
    suspend operator fun invoke(): Participant = participantRepo.getOrCreateLocalParticipant()
}
