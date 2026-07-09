package com.example.ta33.data.repository

import com.example.ta33.core.IdGenerator
import com.example.ta33.core.TimeProvider
import com.example.ta33.data.db.Ta33Database
import com.example.ta33.data.mapper.toDomain
import com.example.ta33.domain.model.Participant
import com.example.ta33.domain.model.SyncStatus
import com.example.ta33.domain.repository.ParticipantRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ParticipantRepositoryImpl(
    private val db: Ta33Database,
    private val ids: IdGenerator,
    private val time: TimeProvider,
) : ParticipantRepository {
    private val q get() = db.participantQueries

    override suspend fun getOrCreateLocalParticipant(): Participant =
        withContext(Dispatchers.Default) {
            val existing = q.selectAnyParticipant().executeAsOneOrNull()
            if (existing != null) {
                existing.toDomain()
            } else {
                val participant = Participant(
                    id = ids.newId(),
                    displayName = null,
                    createdAtMillis = time.nowMillis(),
                    syncStatus = SyncStatus.PENDING,
                )
                q.insertParticipant(
                    participant.id,
                    participant.displayName,
                    participant.createdAtMillis,
                    participant.syncStatus.name,
                )
                participant
            }
        }
}
