package com.example.ta33.data.mapper

import com.example.ta33.domain.model.CollectedControl
import com.example.ta33.domain.model.ControlPoint
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.Participant
import com.example.ta33.domain.model.Route
import com.example.ta33.domain.model.RunSession
import com.example.ta33.domain.model.SyncStatus
import com.example.ta33.domain.model.Trackpoint
import com.example.ta33.data.db.CollectedControl as CollectedControlRow
import com.example.ta33.data.db.ControlPoint as ControlPointRow
import com.example.ta33.data.db.Participant as ParticipantRow
import com.example.ta33.data.db.Route as RouteRow
import com.example.ta33.data.db.RunSession as RunSessionRow
import com.example.ta33.data.db.Trackpoint as TrackpointRow

fun ControlPointRow.toDomain(): ControlPoint = ControlPoint(
    id = id,
    routeId = routeId,
    ordinal = ordinal.toInt(),
    name = name,
    location = GeoPoint(latitude, longitude),
    radiusMeters = radiusMeters,
)

fun RouteRow.toDomain(controls: List<ControlPoint> = emptyList()): Route = Route(
    id = id,
    name = name,
    distanceKm = distanceKm,
    controls = controls,
)

fun ParticipantRow.toDomain(): Participant = Participant(
    id = id,
    displayName = displayName,
    createdAtMillis = createdAtMillis,
    syncStatus = SyncStatus.fromDb(syncStatus),
)

fun RunSessionRow.toDomain(): RunSession = RunSession(
    id = id,
    routeId = routeId,
    participantId = participantId,
    startedAtMillis = startedAtMillis,
    finishedAtMillis = finishedAtMillis,
    syncStatus = SyncStatus.fromDb(syncStatus),
)

fun CollectedControlRow.toDomain(): CollectedControl = CollectedControl(
    id = id,
    runSessionId = runSessionId,
    controlId = controlId,
    collectedAtMillis = collectedAtMillis,
    syncStatus = SyncStatus.fromDb(syncStatus),
)

fun TrackpointRow.toDomain(): Trackpoint = Trackpoint(
    id = id,
    runSessionId = runSessionId,
    location = GeoPoint(latitude, longitude),
    accuracyMeters = accuracyMeters,
    timestampMillis = timestampMillis,
    syncStatus = SyncStatus.fromDb(syncStatus),
)
