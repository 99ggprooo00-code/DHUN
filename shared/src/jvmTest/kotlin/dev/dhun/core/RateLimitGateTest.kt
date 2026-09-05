package dev.dhun.core

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

/**
 * Real-clock tests (not runTest virtual time — the gate's contract IS
 * suspension duration). Bounds are generous downward, tight only where the
 * semantics demand it.
 */
class RateLimitGateTest {

    @Test
    fun awaitPassesImmediatelyWhenNeverTripped() = runBlocking {
        val gate = RateLimitGate()
        val start = TimeSource.Monotonic.markNow()
        gate.await()
        assertTrue(start.elapsedNow() < 2_000.milliseconds, "untripped gate must not suspend")
    }

    @Test
    fun awaitSuspendsUntilTrippedCooldownExpires() = runBlocking {
        val gate = RateLimitGate()
        gate.trip(120.milliseconds)
        val start = TimeSource.Monotonic.markNow()
        gate.await()
        val elapsed = start.elapsedNow()
        assertTrue(elapsed >= 100.milliseconds, "await returned after only $elapsed — cooldown not honored")
    }

    @Test
    fun reTripNeverShortensAnActiveCooldown() = runBlocking {
        val gate = RateLimitGate()
        gate.trip(300.milliseconds)
        gate.trip(50.milliseconds) // would shorten — must be ignored
        val start = TimeSource.Monotonic.markNow()
        gate.await()
        val elapsed = start.elapsedNow()
        assertTrue(elapsed >= 250.milliseconds, "await returned after only $elapsed — later trip shortened cooldown")
    }

    @Test
    fun concurrentAwaitersAllResumeAfterExpiry() = runBlocking {
        val gate = RateLimitGate()
        gate.trip(100.milliseconds)
        val start = TimeSource.Monotonic.markNow()
        val jobs = (1..5).map { async { gate.await() } }
        jobs.forEach { it.await() }
        val elapsed = start.elapsedNow()
        assertTrue(elapsed >= 90.milliseconds, "awaiters resumed too early: $elapsed")
        assertTrue(elapsed < 5_000.milliseconds, "awaiters resumed too late: $elapsed")
    }
}
