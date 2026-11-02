package com.smartreceiverpro.core.common

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Prevents duplicate command storms and accidental repeated execution during
 * button mashing or network retries.
 */
class CommandRateLimiter(
    private val minIntervalMs: Long = 120L,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val mutex = Mutex()
    private val lastCommandTimestamps = mutableMapOf<String, Long>()

    suspend fun tryAcquire(commandKey: String, customIntervalMs: Long = minIntervalMs): Boolean = mutex.withLock {
        val now = timeProvider()
        val lastTime = lastCommandTimestamps[commandKey] ?: 0L
        if (now - lastTime < customIntervalMs) {
            return false
        }
        lastCommandTimestamps[commandKey] = now
        return true
    }

    suspend fun reset() = mutex.withLock {
        lastCommandTimestamps.clear()
    }
}
