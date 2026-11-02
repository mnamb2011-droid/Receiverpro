package com.smartreceiverpro.core.common

import android.util.Log

/**
 * Small structured Android logger shared by the receiver core modules.
 * Keeps diagnostics searchable in Logcat without an extra logging dependency.
 */
object StructuredLogger {
    private fun format(message: String, fields: Map<String, Any?>): String {
        if (fields.isEmpty()) return message
        val suffix = fields.entries.joinToString(" ") { (key, value) -> "$key=$value" }
        return "$message | $suffix"
    }

    fun info(
        tag: String,
        message: String,
        fields: Map<String, Any?> = emptyMap()
    ) {
        Log.i(tag, format(message, fields))
    }

    fun warn(
        tag: String,
        message: String,
        fields: Map<String, Any?> = emptyMap()
    ) {
        Log.w(tag, format(message, fields))
    }

    fun error(
        tag: String,
        message: String,
        throwable: Throwable? = null
    ) {
        if (throwable == null) {
            Log.e(tag, message)
        } else {
            Log.e(tag, message, throwable)
        }
    }
}
