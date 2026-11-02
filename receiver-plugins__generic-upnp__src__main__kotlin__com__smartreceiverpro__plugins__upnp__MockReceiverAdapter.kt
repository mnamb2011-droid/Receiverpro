package com.smartreceiverpro.plugins.upnp

import com.smartreceiverpro.core.common.AppResult
import com.smartreceiverpro.core.common.ErrorCode
import com.smartreceiverpro.engine.api.*
import com.smartreceiverpro.engine.capabilities.ReceiverCapabilities

/**
 * Automated Test & QA Mock Receiver Adapter per Section 21:
 * "أنشئ وضع Mock Receiver للاختبارات البرمجية فقط، مع تمييزه بوضوح عن الاتصال الحقيقي."
 *
 * Explicitly prefixes model name and ID with "[MOCK-TEST-ONLY]" so it can never be
 * confused with a real physical satellite receiver connection.
 */
class MockReceiverAdapter : ReceiverAdapter {
    override val pluginFamilyId: String = "mock-test-only"
    override val displayFamilyName: String = "[MOCK TEST MODE] محاكي اختبارات الجودة فقط"

    private var isConnected = false
    private val executedRemoteKeys = mutableListOf<RemoteKey>()
    private val channels = mutableListOf(
        ReceiverChannel("0001", 1, "[MOCK] قناة الاختبار الأولى HD", false, "Nilesat 7W", 11747, "V", 27500, false, false, true, videoPid = 101, audioPid = 102, pmtPid = 100),
        ReceiverChannel("0002", 2, "[MOCK] قناة الأخبار التجريبية", false, "Nilesat 7W", 11747, "V", 27500, false, false, false, videoPid = 201, audioPid = 202, pmtPid = 200),
        ReceiverChannel("0003", 3, "[MOCK] إذاعة القرآن الكريم", true, "Nilesat 7W", 11766, "H", 27500, false, false, true, videoPid = 0, audioPid = 302, pmtPid = 300)
    )
    private val timers = mutableListOf<ReceiverTimerItem>()

    fun getExecutedKeysSnapshot(): List<RemoteKey> = executedRemoteKeys.toList()

    override suspend fun discover(timeoutMs: Long): AppResult<List<DiscoveredDeviceCandidate>> =
        AppResult.Success(
            listOf(
                DiscoveredDeviceCandidate(
                    ipAddress = "127.0.0.1",
                    port = 20000,
                    discoveryMethod = DiscoveryTransport.MANUAL_IP,
                    rawBannerOrServerHeader = "MOCK_TEST_RECEIVER_DO_NOT_USE_IN_PROD",
                    detectedFamilyHint = "mock-test-only"
                )
            )
        )

    override suspend fun connect(ipAddress: String, port: Int, password: String?): AppResult<ReceiverDeviceInfo> {
        isConnected = true
        return AppResult.Success(
            ReceiverDeviceInfo(
                deviceId = "MOCK_TEST_DEVICE_127_0_0_1",
                modelName = "[MOCK-TEST-ONLY] Simulated Receiver",
                chipsetFamily = "MOCK_VIRTUAL_CHIPSET",
                firmwareVersion = "0.0.0-TEST",
                serialNumber = "MOCK-0000",
                ipAddress = ipAddress,
                macAddress = "02:00:00:00:00:01",
                controlPort = port,
                streamPort = 554,
                currentChannelCount = channels.size,
                maxProgramCapacity = 1000,
                isStandby = false,
                supportedProtocols = listOf("MOCK_TEST_PROTOCOL"),
                availableServices = listOf("ALL_TEST_SERVICES")
            )
        )
    }

    override suspend fun disconnect(): AppResult<Unit> {
        isConnected = false
        return AppResult.Success(Unit)
    }

    override suspend fun getDeviceInfo(): AppResult<ReceiverDeviceInfo> {
        if (!isConnected) return AppResult.Error(ErrorCode.NETWORK_UNREACHABLE, "Mock not connected")
        return connect("127.0.0.1", 20000, null)
    }

    override suspend fun getCapabilities(): AppResult<ReceiverCapabilities> {
        if (!isConnected) return AppResult.Error(ErrorCode.NETWORK_UNREACHABLE, "Mock not connected")
        return AppResult.Success(
            ReceiverCapabilities(
                supportsRemote = true,
                supportsTouchRemote = true,
                supportsMotionRemote = true,
                supportsChannelManagement = true,
                supportsChannelMove = true,
                supportsChannelRename = true,
                supportsChannelDelete = true,
                supportsChannelLock = true,
                supportsFavoritesEdit = true,
                supportsLiveTv = true,
                supportsRtspSatIpStream = true,
                supportsEPG = true,
                supportsTimers = true,
                supportsDLNA = true,
                supportsDlnaDmrRender = true,
                supportsDlnaDmpBrowse = true,
                supportsSatFinderSignal = true,
                supportsSleepTimer = true,
                supportsParentalControl = true,
                supportsScreenLock = true,
                supportsAudioDisplaySettings = true,
                supportsFactoryResetCommand = true,
                verifiedProtocolName = "MOCK_TEST_PROTOCOL_ONLY"
            )
        )
    }

    override suspend fun getChannels(forceRefresh: Boolean): AppResult<List<ReceiverChannel>> =
        if (isConnected) AppResult.Success(channels.toList())
        else AppResult.Error(ErrorCode.NETWORK_UNREACHABLE, "Mock disconnected")

    override suspend fun switchChannel(serviceId: String): AppResult<Unit> =
        if (channels.any { it.serviceId == serviceId }) AppResult.Success(Unit)
        else AppResult.Error(ErrorCode.INVALID_RESPONSE_PAYLOAD, "Channel not found")

    override suspend fun sendRemoteCommand(key: RemoteKey): AppResult<Unit> {
        if (!isConnected) return AppResult.Error(ErrorCode.NETWORK_UNREACHABLE, "Mock disconnected")
        executedRemoteKeys.add(key)
        return AppResult.Success(Unit)
    }

    override suspend fun getEpg(serviceId: String): AppResult<List<EpgProgramItem>> =
        AppResult.Success(
            listOf(
                EpgProgramItem(
                    eventId = "epg_1",
                    serviceId = serviceId,
                    channelName = "[MOCK] Ch",
                    title = "[MOCK] برنامج اختباري حالي",
                    synopsis = "وصف برنامج مخصص للاختبارات الآلية فقط",
                    startEpochMs = 1_700_000_000_000L,
                    endEpochMs = 1_700_003_600_000L
                )
            )
        )

    override suspend fun getTimers(): AppResult<List<ReceiverTimerItem>> =
        AppResult.Success(timers.toList())

    override suspend fun createTimer(timer: ReceiverTimerItem): AppResult<ReceiverTimerItem> {
        val confirmed = timer.copy(isConfirmedByReceiver = true)
        timers.add(confirmed)
        return AppResult.Success(confirmed)
    }

    override suspend fun updateTimer(timer: ReceiverTimerItem): AppResult<ReceiverTimerItem> {
        timers.removeAll { it.timerId == timer.timerId }
        val confirmed = timer.copy(isConfirmedByReceiver = true)
        timers.add(confirmed)
        return AppResult.Success(confirmed)
    }

    override suspend fun deleteTimer(timerId: String): AppResult<Unit> {
        timers.removeAll { it.timerId == timerId }
        return AppResult.Success(Unit)
    }

    override suspend fun getSignalInfo(): AppResult<TunerSignalInfo> =
        AppResult.Success(
            TunerSignalInfo(
                isLocked = true,
                strengthPercent = 88,
                qualityPercent = 82,
                snrDb = 13.1f,
                ber = 0f,
                satelliteName = "Nilesat 7W [MOCK]",
                transponderLabel = "11747 V 27500",
                timestampEpochMs = System.currentTimeMillis()
            )
        )

    override suspend fun getSettings(): AppResult<ReceiverSystemSettings> =
        AppResult.Success(ReceiverSystemSettings(0, false, false, "16:9", "2160p", "PCM", true))

    override suspend fun updateSettings(
        updatedSettings: ReceiverSystemSettings,
        newPasswordPlain: String?,
        factoryResetDoubleConfirmed: Boolean
    ): AppResult<ReceiverSystemSettings> = AppResult.Success(updatedSettings)

    override suspend fun getStreamUrl(serviceId: String): AppResult<StreamDescriptor> =
        AppResult.Success(
            StreamDescriptor(
                streamUrl = "rtsp://127.0.0.1:554/?prognumber=$serviceId",
                protocol = "MOCK_RTSP",
                isScrambled = false,
                requiresRtspKeepAlive = false
            )
        )

    override suspend fun getMediaCapabilities(): AppResult<MediaSharingCapabilities> =
        AppResult.Success(MediaSharingCapabilities(true, true, listOf("video/mp4")))

    override suspend fun moveChannel(serviceId: String, targetPosition: Int): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun renameChannel(serviceId: String, newName: String): AppResult<Unit> {
        val idx = channels.indexOfFirst { it.serviceId == serviceId }
        if (idx >= 0) channels[idx] = channels[idx].copy(serviceName = newName)
        return AppResult.Success(Unit)
    }
    override suspend fun deleteChannels(serviceIds: List<String>, userConfirmed: Boolean): AppResult<Unit> {
        if (!userConfirmed) return AppResult.Error(ErrorCode.CONFIRMATION_REQUIRED, "Confirmation required")
        channels.removeAll { it.serviceId in serviceIds }
        return AppResult.Success(Unit)
    }
    override suspend fun setChannelsLock(serviceIds: List<String>, locked: Boolean, parentalPin: String): AppResult<Unit> =
        AppResult.Success(Unit)
}
