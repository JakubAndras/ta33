package com.example.ta33

import app.cash.turbine.turbineScope
import com.example.ta33.data.location.SharedLocationStream
import com.example.ta33.domain.model.GeoPoint
import com.example.ta33.domain.model.GeoPosition
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LocationStreamTest {

    private fun pos(t: Long) = GeoPosition(GeoPoint(50.0, 16.0 + t / 1_000_000.0), 5.0, t)

    @Test
    fun twoCollectors_shareOneUpstreamSubscription() = runTest {
        val provider = FakeLocationProvider()
        val stream = SharedLocationStream(provider, backgroundScope)

        turbineScope {
            val a = stream.positions().testIn(backgroundScope)
            val b = stream.positions().testIn(backgroundScope)
            runCurrent() // let both collectors subscribe before emitting

            provider.emit(pos(0))
            provider.emit(pos(1_000))

            assertEquals(pos(0), a.awaitItem())
            assertEquals(pos(1_000), a.awaitItem())
            assertEquals(pos(0), b.awaitItem())
            assertEquals(pos(1_000), b.awaitItem())
            a.cancel()
            b.cancel()
        }

        // A single upstream subscription served both collectors.
        assertEquals(1, provider.maxSubscriptions)
    }

    @Test
    fun lateSubscriber_getsReplayedLastFix() = runTest {
        val provider = FakeLocationProvider()
        val stream = SharedLocationStream(provider, backgroundScope)

        turbineScope {
            // Keep upstream alive so it never drops to zero subscribers.
            val keepAlive = stream.positions().testIn(backgroundScope)
            runCurrent()
            provider.emit(pos(0))
            provider.emit(pos(2_000))
            // Await consumption so the shared replay cache has advanced to the last fix.
            assertEquals(pos(0), keepAlive.awaitItem())
            assertEquals(pos(2_000), keepAlive.awaitItem())

            val replayed = stream.positions().first() // late subscriber gets replay=1
            assertEquals(pos(2_000), replayed)
            assertEquals(1, provider.maxSubscriptions)
            keepAlive.cancelAndIgnoreRemainingEvents()
        }
    }
}
