package com.smartreceiverpro.plugins.senator

import com.smartreceiverpro.core.common.AppResult
import com.smartreceiverpro.core.common.ErrorCode
import com.smartreceiverpro.core.common.StructuredLogger
import com.smartreceiverpro.engine.api.ReceiverDeviceInfo
import com.smartreceiverpro.engine.capabilities.ReceiverCapabilities
import com.smartreceiverpro.engine.protocol.CompatibilityDiagnosticReport
import com.smartreceiverpro.engine.protocol.SafeProtocolDiscoveryModule
import com.smartreceiverpro.plugins.gmscreen.GmScreenReceiverAdapter

/**
 * Primary target plugin for the Senator Captain family (Section 5):
 * - Senator Captain Q2 4K CA (ALi 2661 Dual-Core 4K processor, 16MB Flash, 4GB RAM)
 * - Senator Captain 4K (1st Gen GX processor variant)
 *
 * Strictly enforces Section 1 & Section 5:
 * - Never assumes identical protocols across Senator Captain hardware revisions.
 * - Executes [SafeProtocolDiscoveryModule] before enabling control capabilities.
 * - Separates proven capabilities (e.g. G-MScreen TCP 20000 on ALi 2661) from
 *   unverified/unavailable endpoints (e.g. GX variant when G-MScreen port 20000 is closed).
 */
class SenatorCaptainAdapter(
    private val discoveryModule: SafeProtocolDiscoveryModule = SafeProtocolDiscoveryModule()
) : GmScreenReceiverAdapter(
    pluginFamilyId = "senator",
    displayFamilyName = "Senator Captain 4K / Captain Q2 4K CA"
) {
    var lastCompatibilityReport: CompatibilityDiagnosticReport? = null
        private set

    override suspend fun connect(ipAddress: String, port: Int, password: String?): AppResult<ReceiverDeviceInfo> {
        // Step 1: Run safe non-destructive protocol discovery against the Senator receiver
        val probeReport = discoveryModule.runSafeCompatibilityProbe(ipAddress)
        lastCompatibilityReport = probeReport

        if (!probeReport.icmpOrTcpReachable) {
            return AppResult.Error(
                ErrorCode.NETWORK_UNREACHABLE,
                "تعذر الوصول إلى جهاز Senator Captain على العنوان $ipAddress."
            )
        }

        val hasGmScreenPort = probeReport.probedPorts.any { it.port == 20000 && it.isOpen }
        val hasUpnpPort = probeReport.probedPorts.any { it.port == 49152 && it.isOpen }

        if (hasGmScreenPort) {
            // Connect via verified ALi 2661 G-MScreen protocol (Senator Captain Q2 4K CA)
            val baseResult = super.connect(ipAddress, port, password)
            if (baseResult is AppResult.Success) {
                val upgradedInfo = baseResult.data.copy(
                    modelName = if (baseResult.data.modelName.contains("Captain", true)) {
                        baseResult.data.modelName
                    } else {
                        "Senator Captain Q2 4K (${baseResult.data.modelName})"
                    },
                    chipsetFamily = "ALi 2661 4K (Verified G-MScreen)"
                )
                cachedDeviceInfo = upgradedInfo
                return AppResult.Success(upgradedInfo)
            }
            return baseResult
        }

        if (hasUpnpPort) {
            // Senator Captain 4K (GX variant or firmware with G-MScreen disabled, UPnP active)
            connectedIp = ipAddress
            connectedPort = 49152
            val limitedInfo = ReceiverDeviceInfo(
                deviceId = "senator_gx_$ipAddress",
                modelName = "Senator Captain 4K (GX / UPnP Mode)",
                chipsetFamily = "GX NationalChip (G-MScreen Port 20000 Closed)",
                firmwareVersion = "UPnP-Detected",
                serialNumber = "UNVERIFIED",
                ipAddress = ipAddress,
                macAddress = null,
                controlPort = 49152,
                streamPort = 0,
                currentChannelCount = 0,
                maxProgramCapacity = 0,
                isStandby = false,
                supportedProtocols = listOf("UPnP / DLNA DMR (Port 49152)"),
                availableServices = listOf("DLNA Media Sharing")
            )
            cachedDeviceInfo = limitedInfo
            verifiedCapabilities = ReceiverCapabilities(
                supportsRemote = false,
                supportsChannelManagement = false,
                supportsLiveTv = false,
                supportsEPG = false,
                supportsTimers = false,
                supportsDLNA = true,
                supportsDlnaDmrRender = true,
                supportsSatFinderSignal = false,
                verifiedProtocolName = "UPnP/DLNA Only (Control Protocol Unverified)",
                unverifiedFeaturesNotes = listOf(
                    "منفذ التحكم G-MScreen (20000) غير مفتوح على هذا الإصدار من Senator Captain.",
                    "تم تفعيل مشاركة الوسائط DLNA فقط؛ التحكم في القنوات والريموت يتطلب تفعيل G-MScreen من إعدادات الرسيفر أو تزويد عينة اتصال لإصدار GX."
                )
            )
            StructuredLogger.info("SenatorAdapter", "Connected in limited UPnP mode; control protocol unverified")
            return AppResult.Success(limitedInfo)
        }

        return AppResult.Error(
            ErrorCode.PROTOCOL_MISMATCH,
            probeReport.diagnosticSummaryArabic
        )
    }
}
