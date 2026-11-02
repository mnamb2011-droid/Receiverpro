package com.smartreceiverpro.feature.timers

import com.smartreceiverpro.engine.api.ReceiverTimerItem

/**
 * Validates timer conflicts and enforces Section 12:
 * - Detects overlapping start/end intervals across existing timers.
 * - Never marks a timer as saved on the receiver before [ReceiverTimerItem.isConfirmedByReceiver] is true.
 */
object TimerConflictValidator {
    fun findConflictingTimers(
        candidate: ReceiverTimerItem,
        existingTimers: List<ReceiverTimerItem>
    ): List<ReceiverTimerItem> {
        require(candidate.endEpochMs > candidate.startEpochMs) { "Timer end time must be after start time" }
        return existingTimers.filter { existing ->
            existing.timerId != candidate.timerId &&
                candidate.startEpochMs < existing.endEpochMs &&
                candidate.endEpochMs > existing.startEpochMs
        }
    }
}
