package com.smartreceiverpro.engine.connection

import com.smartreceiverpro.core.common.AppResult
import com.smartreceiverpro.core.common.CommandRateLimiter
import com.smartreceiverpro.core.common.ErrorCode
import com.smartreceiverpro.core.common.StructuredLogger
import com.smartreceiverpro.engine.api.ReceiverAdapter
import com.smartreceiverpro.engine.api.ReceiverDeviceInfo
import com.smartreceiverpro.engine.api.RemoteKey
import com.smartreceiverpro.engine.capabilities.ReceiverCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveReceiverSession(
    val isConnected: Boolean = false,
    val isMockTestingSession: Boolean = false,
    val adapter: ReceiverAdapter? = null,
    val deviceInfo: ReceiverDeviceInfo? = null,
    val capabilities: ReceiverCapabilities = ReceiverCapabilities.NONE,
    val currentChannelName: String? = null,
    val lastErrorArabic: String? = null
)

/**
 * Coordinates the active receiver connection lifecycle, rate-limiting commands,
 * handling Wi-Fi drops cleanly without duplicate command replay, and exposing
 * reactive [StateFlow] state to all feature modules.
 */
class ReceiverSessionManager(
    private val rateLimiter: CommandRateLimiter = CommandRateLimiter(minIntervalMs = 110L)
) {
    private val _session = MutableStateFlow(ActiveReceiverSession())
    val session: StateFlow<ActiveReceiverSession> = _session.asStateFlow()

    fun attachVerifiedSession(
        adapter: ReceiverAdapter,
        deviceInfo: ReceiverDeviceInfo,
        capabilities: ReceiverCapabilities,
        isMock: Boolean = false
    ) {
        _session.value = ActiveReceiverSession(
            isConnected = true,
            isMockTestingSession = isMock,
            adapter = adapter,
            deviceInfo = deviceInfo,
            capabilities = capabilities,
            lastErrorArabic = null
        )
    }

    suspend fun sendRateLimitedRemoteKey(key: RemoteKey): AppResult<Unit> {
        val current = _session.value
        val activeAdapter = current.adapter
        if (!current.isConnected || activeAdapter == null) {
            return AppResult.Error(ErrorCode.NETWORK_UNREACHABLE, "لا يوجد رسيفر متصل لإرسال الأمر.")
        }
        if (!current.capabilities.supportsRemote) {
            return AppResult.UnsupportedCapability("RemoteControl", "هذا الجهاز لا يدعم أوامر الريموت عبر الشبكة.")
        }
        if (!rateLimiter.tryAcquire("REMOTE_${key.name}")) {
            return AppResult.Error(ErrorCode.RATE_LIMITED, "تم تجاهل التكرار السريع للأمر ${key.arabicLabel}.")
        }
        return activeAdapter.sendRemoteCommand(key)
    }

    suspend fun onWifiLost() {
        val current = _session.value
        if (current.isConnected) {
            StructuredLogger.warn("SessionManager", "Wi-Fi connectivity lost; marking session disconnected")
            current.adapter?.disconnect()
            _session.value = current.copy(
                isConnected = false,
                lastErrorArabic = "انقطع اتصال Wi-Fi بالرسيفر. بانتظار عودة الشبكة لإعادة الاتصال الآمن."
            )
        }
    }

    suspend fun disconnectActive() {
        val current = _session.value
        current.adapter?.disconnect()
        _session.value = ActiveReceiverSession()
    }
}
