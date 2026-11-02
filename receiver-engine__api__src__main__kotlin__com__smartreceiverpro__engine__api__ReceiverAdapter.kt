package com.smartreceiverpro.engine.api

import com.smartreceiverpro.core.common.AppResult
import com.smartreceiverpro.engine.capabilities.ReceiverCapabilities

/**
 * Unified contract for every satellite receiver brand/model plugin.
 * Every operation MUST verify actual device support via [getCapabilities]
 * and never return fake success when the receiver is disconnected or lacks support.
 */
interface ReceiverAdapter {
    val pluginFamilyId: String
    val displayFamilyName: String

    suspend fun discover(timeoutMs: Long = 3500L): AppResult<List<DiscoveredDeviceCandidate>>

    suspend fun connect(ipAddress: String, port: Int, password: String? = null): AppResult<ReceiverDeviceInfo>

    suspend fun disconnect(): AppResult<Unit>

    suspend fun getDeviceInfo(): AppResult<ReceiverDeviceInfo>

    suspend fun getCapabilities(): AppResult<ReceiverCapabilities>

    suspend fun getChannels(forceRefresh: Boolean = false): AppResult<List<ReceiverChannel>>

    suspend fun switchChannel(serviceId: String): AppResult<Unit>

    suspend fun sendRemoteCommand(key: RemoteKey): AppResult<Unit>

    suspend fun getEpg(serviceId: String): AppResult<List<EpgProgramItem>>

    suspend fun getTimers(): AppResult<List<ReceiverTimerItem>>

    suspend fun createTimer(timer: ReceiverTimerItem): AppResult<ReceiverTimerItem>

    suspend fun updateTimer(timer: ReceiverTimerItem): AppResult<ReceiverTimerItem>

    suspend fun deleteTimer(timerId: String): AppResult<Unit>

    suspend fun getSignalInfo(): AppResult<TunerSignalInfo>

    suspend fun getSettings(): AppResult<ReceiverSystemSettings>

    suspend fun updateSettings(
        updatedSettings: ReceiverSystemSettings,
        newPasswordPlain: String? = null,
        factoryResetDoubleConfirmed: Boolean = false
    ): AppResult<ReceiverSystemSettings>

    suspend fun getStreamUrl(serviceId: String): AppResult<StreamDescriptor>

    suspend fun getMediaCapabilities(): AppResult<MediaSharingCapabilities>

    // Channel editing extensions guarded by capability flags
    suspend fun moveChannel(serviceId: String, targetPosition: Int): AppResult<Unit>
    suspend fun renameChannel(serviceId: String, newName: String): AppResult<Unit>
    suspend fun deleteChannels(serviceIds: List<String>, userConfirmed: Boolean): AppResult<Unit>
    suspend fun setChannelsLock(serviceIds: List<String>, locked: Boolean, parentalPin: String): AppResult<Unit>
}
