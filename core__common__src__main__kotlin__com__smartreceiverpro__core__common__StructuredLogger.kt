package com.smartreceiverpro.core.common

import android.util.Log

/**
 * Structured Android logger shared by the receiver core modules.
 * Redacts credentials before writing messages to Logcat.
 */
object StructuredLogger {
    private const val REDACTED = "[REDACTED]"

    // Handles JSON fields such as "Password":"0000" and "pin":"9876".
    private val sensitiveJson = Regex(
        """(?i)("(?:password|passwd|pwd|parental[_ -]?pin|pin|token|authorization|secret)"\s*:\s*")[^"]*(")"""
    )

    // Handles plain text fields such as password=secret and pin: 1234.
    private val sensitiveAssignment = Regex(
        """(?i)\b(password|passwd|pwd|parental[_ -]?pin|pin|token|authorization|secret)\b(\s*[:=]\s*)(?:"[^"]*"|'[^']*'|[^\s,;&}]+)"""
    )

    /**
     * Removes credential values from key=value and JSON-like diagnostic text.
     */
    fun sanitize(message: String): String {
        val jsonRedacted = sensitiveJson.replace(message) { match ->
            match.groupValues[1] + REDACTED + match.groupValues[2]
        }
        return sensitiveAssignment.replace(jsonRedacted) { match ->
            match.groupValues[1] + match.groupValues[2] + REDACTED
        }
    }

    private fun format(message: String, fields: Map<String, Any?>): String {
        val combined = if (fields.isEmpty()) {
            message
        } else {
            val suffix = fields.entries.joinToString(" ") { (key, value) -> "$key=$value" }
            "$message | $suffix"
        }
        return sanitize(combined)
    }

    fun info(tag: String, message: String, fields: Map<String, Any?> = emptyMap()) {
        Log.i(tag, format(message, fields))
    }

    fun warn(tag: String, message: String, fields: Map<String, Any?> = emptyMap()) {
        Log.w(tag, format(message, fields))
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        val safeMessage = sanitize(message)
        if (throwable == null) Log.e(tag, safeMessage) else Log.e(tag, safeMessage, throwable)
    }
}
