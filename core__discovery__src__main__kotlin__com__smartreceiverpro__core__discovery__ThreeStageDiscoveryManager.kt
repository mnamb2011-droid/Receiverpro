package com.smartreceiverpro.core.discovery

import com.smartreceiverpro.core.common.AppResult
import com.smartreceiverpro.core.common.ErrorCode
import com.smartreceiverpro.core.common.StructuredLogger
import com.smartreceiverpro.core.network.GmScreenFramedTcpClient
import com.smartreceiverpro.engine.api.DiscoveredDeviceCandidate
import com.smartreceiverpro.engine.api.DiscoveryTransport
import com.smartreceiverpro.engine.api.ReceiverAdapter
import com.smartreceiverpro.engine.api.ReceiverDeviceInfo
import com.smartreceiverpro.engine.capabilities.ReceiverCapabilities
import com.smartreceiverpro.engine.protocol.CompatibilityDiagnosticReport
import com.smartreceiverpro.engine.protocol.SafeProtocolDiscoveryModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Enforces Section 7's mandatory 3-Stage Verification Pipeline:
 *   Stage 1: Network Reachability (ICMP / TCP socket handshake)
 *   Stage 2: Protocol Identification (G-MScreen TCP 20000, UPnP SSDP, or Senator variant detection)
 *   Stage 3: Capability Verification (Extracting real supported features from the connected adapter)
 *
 * Appearance of an IP address alone is NEVER treated as proof of compatibility.
 */
enum class VerificationStage {
    IDLE,
    STAGE_1_NETWORK_REACHABILITY,
    STAGE_2_PROTOCOL_IDENTIFICATION,
    STAGE_3_CAPABILITY_VERIFICATION,
    VERIFIED_READY,
    FAILED
}

data class ThreeStageVerificationState(
    val targetIp: String = "",
    val currentStage: VerificationStage = VerificationStage.IDLE,
    val diagnosticReport: CompatibilityDiagnosticReport? = null,
    val verifiedDeviceInfo: ReceiverDeviceInfo? = null,
    val verifiedCapabilities: ReceiverCapabilities? = null,
    val failureReasonArabic: String? = null
)

class ThreeStageDiscoveryManager(
    private val protocolDiscovery: SafeProtocolDiscoveryModule = SafeProtocolDiscoveryModule()
) {
    private val _verificationState = MutableStateFlow(ThreeStageVerificationState())
    val verificationState: StateFlow<ThreeStageVerificationState> = _verificationState.asStateFlow()

    /**
     * Broadcasts SSDP M-SEARCH and G-MScreen UDP Magic ("39WwijOog54a") on the local LAN.
     */
    suspend fun scanLanForCandidates(timeoutMs: Int = 2500): List<DiscoveredDeviceCandidate> =
        withContext(Dispatchers.IO) {
            val found = mutableMapOf<String, DiscoveredDeviceCandidate>()
            found.putAll(scanSsdpUpnp(timeoutMs / 2).associateBy { it.ipAddress })
            found.putAll(scanGmScreenBroadcast(timeoutMs / 2).associateBy { it.ipAddress })
            found.values.toList()
        }

    private fun scanSsdpUpnp(timeoutMs: Int): List<DiscoveredDeviceCandidate> {
        val results = mutableListOf<DiscoveredDeviceCandidate>()
        val mSearch = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: 239.255.255.250:1900\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 2\r\n")
            append("ST: ssdp:all\r\n\r\n")
        }.toByteArray(Charsets.US_ASCII)

        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val group = InetAddress.getByName("239.255.255.250")
                socket.send(DatagramPacket(mSearch, mSearch.size, group, 1900))
                val buf = ByteArray(2048)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val ip = packet.address.hostAddress ?: continue
                    val respText = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val familyHint = when {
                        respText.contains("Senator", true) -> "senator"
                        respText.contains("ALi", true) || respText.contains("feng", true) -> "gmscreen"
                        else -> "generic-upnp"
                    }
                    results.add(
                        DiscoveredDeviceCandidate(
                            ipAddress = ip,
                            port = 49152,
                            discoveryMethod = DiscoveryTransport.SSDP_UPNP,
                            rawBannerOrServerHeader = respText.lineSequence().firstOrNull().orEmpty(),
                            detectedFamilyHint = familyHint
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Normal socket timeout at end of discovery window
        }
        return results
    }

    private fun scanGmScreenBroadcast(timeoutMs: Int): List<DiscoveredDeviceCandidate> {
        val results = mutableListOf<DiscoveredDeviceCandidate>()
        val magicBytes = GmScreenFramedTcpClient.BROADCAST_MAGIC_CODE.toByteArray(Charsets.US_ASCII)
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = timeoutMs
                val broadcastAddr = InetAddress.getByName("255.255.255.255")
                socket.send(DatagramPacket(magicBytes, magicBytes.size, broadcastAddr, 25860))
                val buf = ByteArray(1024)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val ip = packet.address.hostAddress ?: continue
                    val payload = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    results.add(
                        DiscoveredDeviceCandidate(
                            ipAddress = ip,
                            port = GmScreenFramedTcpClient.DEFAULT_GMSCREEN_PORT,
                            discoveryMethod = DiscoveryTransport.GMSCREEN_UDP_BROADCAST,
                            rawBannerOrServerHeader = payload.take(120),
                            detectedFamilyHint = if (payload.contains("Captain", true)) "senator" else "gmscreen"
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return results
    }

    /**
     * Executes all 3 verification stages against [ipAddress] and selects the matching [ReceiverAdapter].
     */
    suspend fun verifyAndConnect(
        ipAddress: String,
        port: Int,
        password: String?,
        availableAdapters: List<ReceiverAdapter>
    ): AppResult<Pair<ReceiverAdapter, ReceiverDeviceInfo>> {
        // Stage 1: Network Reachability
        _verificationState.value = ThreeStageVerificationState(
            targetIp = ipAddress,
            currentStage = VerificationStage.STAGE_1_NETWORK_REACHABILITY
        )
        val report = protocolDiscovery.runSafeCompatibilityProbe(ipAddress)
        if (!report.icmpOrTcpReachable) {
            val msg = "المرحلة 1 (الوصول الشبكي): فشل الوصول إلى العنوان $ipAddress. تأكد من اتصال الهاتف والرسيفر بنفس شبكة Wi-Fi."
            _verificationState.value = _verificationState.value.copy(
                currentStage = VerificationStage.FAILED,
                diagnosticReport = report,
                failureReasonArabic = msg
            )
            return AppResult.Error(ErrorCode.NETWORK_UNREACHABLE, msg)
        }

        // Stage 2: Protocol Identification
        _verificationState.value = _verificationState.value.copy(
            currentStage = VerificationStage.STAGE_2_PROTOCOL_IDENTIFICATION,
            diagnosticReport = report
        )
        if (report.provenProtocols.isEmpty()) {
            val msg = "المرحلة 2 (تحديد البروتوكول): تم العثور على العنوان $ipAddress ولكن لم يستجب أي بروتوكول تحكم معروف (مثل G-MScreen 20000 أو UPnP)."
            _verificationState.value = _verificationState.value.copy(
                currentStage = VerificationStage.FAILED,
                failureReasonArabic = msg
            )
            return AppResult.Error(ErrorCode.PROTOCOL_MISMATCH, msg)
        }

        val chosenAdapter = selectAdapterForReport(report, availableAdapters)
            ?: return AppResult.Error(ErrorCode.UNSUPPORTED_OPERATION, "لا يتوفر محول (Adapter) متوافق مع البروتوكول المكتشف.")

        val adapterPort = when (chosenAdapter.pluginFamilyId) {
    "senator", "gmscreen" ->
        GmScreenFramedTcpClient.DEFAULT_GMSCREEN_PORT

    "generic-upnp" ->
        if (port <= 0) 49152 else port

    else -> port
}

val connectResult = chosenAdapter.connect(
    ipAddress,
    adapterPort,
    password
)
        if (connectResult !is AppResult.Success) {
            val errMsg = (connectResult as? AppResult.Error)?.message ?: "فشل الاتصال بالبروتوكول"
            _verificationState.value = _verificationState.value.copy(
                currentStage = VerificationStage.FAILED,
                failureReasonArabic = "المرحلة 2 (مصادقة البروتوكول): $errMsg"
            )
            return connectResult as AppResult.Error
        }

        // Stage 3: Capability Verification
        _verificationState.value = _verificationState.value.copy(
            currentStage = VerificationStage.STAGE_3_CAPABILITY_VERIFICATION,
            verifiedDeviceInfo = connectResult.data
        )
        val capResult = chosenAdapter.getCapabilities()
        if (capResult !is AppResult.Success) {
            val msg = "المرحلة 3 (التحقق من القدرات): تعذر استخراج قدرات الجهاز الحقيقية."
            _verificationState.value = _verificationState.value.copy(
                currentStage = VerificationStage.FAILED,
                failureReasonArabic = msg
            )
            return AppResult.Error(ErrorCode.PROTOCOL_MISMATCH, msg)
        }

        _verificationState.value = _verificationState.value.copy(
            currentStage = VerificationStage.VERIFIED_READY,
            verifiedCapabilities = capResult.data,
            failureReasonArabic = null
        )
        StructuredLogger.info(
            "ThreeStageDiscovery",
            "Device passed all 3 verification stages",
            mapOf("ip" to ipAddress, "adapter" to chosenAdapter.pluginFamilyId, "model" to connectResult.data.modelName)
        )
        return AppResult.Success(chosenAdapter to connectResult.data)
    }

    private fun selectAdapterForReport(
        report: CompatibilityDiagnosticReport,
        adapters: List<ReceiverAdapter>
    ): ReceiverAdapter? {
        return when {
            report.detectedHardwareVariant.contains("Senator", ignoreCase = true) ->
                adapters.firstOrNull { it.pluginFamilyId == "senator" }
            report.provenProtocols.any { it.contains("G-MScreen", ignoreCase = true) } ->
                adapters.firstOrNull { it.pluginFamilyId == "senator" }
                    ?: adapters.firstOrNull { it.pluginFamilyId == "gmscreen" }
            report.provenProtocols.any { it.contains("UPnP", ignoreCase = true) } ->
                adapters.firstOrNull { it.pluginFamilyId == "generic-upnp" }
            else -> adapters.firstOrNull()
        }
    }
}
