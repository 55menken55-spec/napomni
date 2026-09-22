package ru.napomni.app.data.repository

import kotlinx.coroutines.flow.Flow
import ru.napomni.app.data.db.OccurrenceDao
import ru.napomni.app.data.model.Occurrence
import java.time.Instant

/** Доступ к истории срабатываний (ТЗ, FR-6.1). */
class OccurrenceRepository(private val dao: OccurrenceDao) {

    fun observeBetween(from: Instant, to: Instant): Flow<List<Occurrence>> =
        dao.observeBetween(from, to)

    suspend fun between(from: Instant, to: Instant): List<Occurrence> =
        dao.between(from, to)
}
