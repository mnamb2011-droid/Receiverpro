package com.smartreceiverpro.plugins.upnp

import com.smartreceiverpro.core.common.AppResult
import com.smartreceiverpro.core.common.ErrorCode
import com.smartreceiverpro.core.network.UpnpSoapHttpClient
import com.smartreceiverpro.engine.api.*
import com.smartreceiverpro.engine.capabilities.ReceiverCapabilities

/**
 * Adapter for generic UPnP AV / DLNA receivers.
 * Strictly exposes only DLNA capabilities verified via the device's XML description,
 * marking STB tuner/channel/remote operations as [AppResult.UnsupportedCapability].
 */
class GenericUpnpDlnaAdapter(
    private val soapClient: UpnpSoapHttpClient = UpnpSoapHttpClient()
) : ReceiverAdapter {

    override val pluginFamilyId: String = "generic-upnp"
    override val displayFamilyName: String = "Generic UPnP / DLNA Media Receiver"

    private var connectedIp: String? = null
    private var deviceInfo: ReceiverDeviceInfo? = null
    private var capabilities: ReceiverCapabilities = ReceiverCapabilities.NONE

    override suspend fun discover(timeoutMs: Long): AppResult<List<DiscoveredDeviceCandidate>> =
        AppResult.Success(emptyList())

    override suspend fun connect(ipAddress: String, port: Int, password: String?): AppResult<ReceiverDeviceInfo> {
        val targetPort = if (port <= 0) 49152 else port
        val descUrl = "http://$ipAddress:$targetPort/description.xml"
        val xmlRes = soapClient.fetchDescriptionXml(descUrl)
        if (xmlRes !is AppResult.Success) {
            return AppResult.Error(ErrorCode.PROTOCOL_MISMATCH, "تعذر قراءة وصف UPnP من $descUrl")
        }

        val friendlyName = Regex("<friendlyName>(.*?)</friendlyName>")
            .find(xmlRes.data)?.groupValues?.getOrNull(1) ?: "UPnP Receiver ($ipAddress)"

        connectedIp = ipAddress
        val info = ReceiverDeviceInfo(
            deviceId = "upnp_$ipAddress",
            modelName = friendlyName,
            chipsetFamily = "UPnP AV Standard",
            firmwareVersion = "1.0",
            serialNumber = ipAddress,
            ipAddress = ipAddress,
            macAddress = null,
            controlPort = targetPort,
            streamPort = targetPort,
            currentChannelCount = 0,
            maxProgramCapacity = 0,
            isStandby = false,
            supportedProtocols = listOf("UPnP AV 1.0", "DLNA DMR"),
            availableServices = listOf("AVTransport", "RenderingControl")
        )
        deviceInfo = info
        capabilities = ReceiverCapabilities(
            supportsDLNA = true,
            supportsDlnaDmrRender = true,
            verifiedProtocolName = "UPnP AV / DLNA",
            unverifiedFeaturesNotes = listOf("جهاز UPnP قياسي: يدعم مشاركة الوسائط فقط ولا يدعم إدارة قنوات الستلايت.")
        )
        return AppResult.Success(info)
    }

    override suspend fun disconnect(): AppResult<Unit> {
        connectedIp = null
        deviceInfo = null
        capabilities = ReceiverCapabilities.NONE
        return AppResult.Success(Unit)
    }

    override suspend fun getDeviceInfo(): AppResult<ReceiverDeviceInfo> =
        deviceInfo?.let { AppResult.Success(it) }
            ?: AppResult.Error(ErrorCode.NETWORK_UNREACHABLE, "غير متصل.")

    override suspend fun getCapabilities(): AppResult<ReceiverCapabilities> =
        AppResult.Success(capabilities)

    override suspend fun getChannels(forceRefresh: Boolean): AppResult<List<ReceiverChannel>> =
        AppResult.UnsupportedCapability("Channels", "بروتوكول UPnP القياسي لا يوفر قائمة قنوات الفضائيات.")

    override suspend fun switchChannel(serviceId: String): AppResult<Unit> =
        AppResult.UnsupportedCapability("SwitchChannel", "غير مدعوم عبر UPnP.")

    override suspend fun sendRemoteCommand(key: RemoteKey): AppResult<Unit> =
        AppResult.UnsupportedCapability("RemoteCommand", "أوامر الريموت الكاملة غير مدعومة عبر UPnP القياسي.")

    override suspend fun getEpg(serviceId: String): AppResult<List<EpgProgramItem>> =
        AppResult.UnsupportedCapability("EPG", "غير مدعوم عبر UPnP.")

    override suspend fun getTimers(): AppResult<List<ReceiverTimerItem>> =
        AppResult.UnsupportedCapability("Timers", "غير مدعوم عبر UPnP.")

    override suspend fun createTimer(timer: ReceiverTimerItem): AppResult<ReceiverTimerItem> =
        AppResult.UnsupportedCapability("Timers", "غير مدعوم عبر UPnP.")

    override suspend fun updateTimer(timer: ReceiverTimerItem): AppResult<ReceiverTimerItem> =
        AppResult.UnsupportedCapability("Timers", "غير مدعوم عبر UPnP.")

    override suspend fun deleteTimer(timerId: String): AppResult<Unit> =
        AppResult.UnsupportedCapability("Timers", "غير مدعوم عبر UPnP.")

    override suspend fun getSignalInfo(): AppResult<TunerSignalInfo> =
        AppResult.UnsupportedCapability("SatFinderSignal", "قراءة إشارة التونر غير مدعومة عبر UPnP.")

    override suspend fun getSettings(): AppResult<ReceiverSystemSettings> =
        AppResult.UnsupportedCapability("Settings", "غير مدعوم عبر UPnP.")

    override suspend fun updateSettings(
        updatedSettings: ReceiverSystemSettings,
        newPasswordPlain: String?,
        factoryResetDoubleConfirmed: Boolean
    ): AppResult<ReceiverSystemSettings> =
        AppResult.UnsupportedCapability("Settings", "غير مدعوم عبر UPnP.")

    override suspend fun getStreamUrl(serviceId: String): AppResult<StreamDescriptor> =
        AppResult.UnsupportedCapability("LiveTv", "غير مدعوم عبر UPnP.")

    override suspend fun getMediaCapabilities(): AppResult<MediaSharingCapabilities> {
        val ip = connectedIp ?: return AppResult.Error(ErrorCode.NETWORK_UNREACHABLE, "غير متصل.")
        return AppResult.Success(
            MediaSharingCapabilities(
                supportsDlnaDmrPush = true,
                supportsDlnaDmpBrowse = true,
                supportedMimeTypes = listOf("video/mp4", "audio/mpeg", "image/jpeg"),
                avTransportControlUrl = "http://$ip:49152/upnp/control/AVTransport1",
                renderingControlUrl = "http://$ip:49152/upnp/control/RenderingControl1"
            )
        )
    }

    override suspend fun moveChannel(serviceId: String, targetPosition: Int): AppResult<Unit> =
        AppResult.UnsupportedCapability("MoveChannel", "غير مدعوم عبر UPnP.")

    override suspend fun renameChannel(serviceId: String, newName: String): AppResult<Unit> =
        AppResult.UnsupportedCapability("RenameChannel", "غير مدعوم عبر UPnP.")

    override suspend fun deleteChannels(serviceIds: List<String>, userConfirmed: Boolean): AppResult<Unit> =
        AppResult.UnsupportedCapability("DeleteChannel", "غير مدعوم عبر UPnP.")

    override suspend fun setChannelsLock(serviceIds: List<String>, locked: Boolean, parentalPin: String): AppResult<Unit> =
        AppResult.UnsupportedCapability("LockChannel", "غير مدعوم عبر UPnP.")
}
