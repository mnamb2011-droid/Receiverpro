package com.smartreceiverpro.core.common

/**
 * Unified result wrapper that explicitly distinguishes verified device responses,
 * unsupported capabilities, and transport/protocol errors.
 * Never returns fake success when a receiver does not respond or lacks a feature.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>

    data class UnsupportedCapability(
        val capabilityName: String,
        val reason: String
    ) : AppResult<Nothing>

    data class Error(
        val code: ErrorCode,
        val message: String,
        val cause: Throwable? = null
    ) : AppResult<Nothing>
}

enum class ErrorCode {
    NETWORK_UNREACHABLE,
    CONNECTION_TIMEOUT,
    PORT_CLOSED,
    PROTOCOL_MISMATCH,
    HANDSHAKE_REJECTED,
    AUTHENTICATION_FAILED,
    UNSUPPORTED_OPERATION,
    DEVICE_BUSY,
    INVALID_RESPONSE_PAYLOAD,
    EXTERNAL_NETWORK_BLOCKED,
    RATE_LIMITED,
    CONFIRMATION_REQUIRED,
    STORAGE_ERROR
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.UnsupportedCapability -> this
    is AppResult.Error -> this
}
