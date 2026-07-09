package com.example.ta33.domain.repository

import com.example.ta33.domain.model.Participant

interface ParticipantRepository {
    suspend fun getOrCreateLocalParticipant(): Participant
}
