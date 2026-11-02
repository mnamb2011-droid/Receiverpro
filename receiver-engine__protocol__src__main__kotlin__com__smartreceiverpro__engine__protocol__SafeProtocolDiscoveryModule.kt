package com.smartreceiverpro.engine.protocol

import com.smartreceiverpro.core.common.AppResult
import com.smartreceiverpro.core.common.StructuredLogger
import com.smartreceiverpro.core.network.GmScreenFramedTcpClient
import com.smartreceiverpro.core.security.LocalNetworkGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Implements Section 5's Safe Protocol Discovery Module:
 * - Performs read-only, non-destructive local network probing on candidate STBs
 *   (specifically Senator Captain 4K [GX processor] vs Senator Captain Q2 4K CA [ALi 2661 processor]).
 * - Never sends unknown mutating commands that could alter receiver settings.
 * - Strictly separates proven capabilities from unverified/unknown endpoints.
 * - Produces a structured [CompatibilityDiagnosticReport].
 */
@Serializable
data class PortProbeOutcome(
    val port: Int,
    val serviceLabel: String,
    val isOpen: Boolean,
    val bannerOrResponseSnippet: String
)

@Serializable
data class CompatibilityDiagnosticReport(
    val targetIp: String,
    val icmpOrTcpReachable: Boolean,
    val detectedHardwareVariant: String,
    val probedPorts: List<PortProbeOutcome>,
    val provenProtocols: List<String>,
    val unverifiedOrMissingProtocols: List<String>,
    val requiresFirmwareSampleOrCapture: Boolean,
    val diagnosticSummaryArabic: String,
    val timestampEpochMs: Long
)

class SafeProtocolDiscoveryModule {

    companion object {
        // Strictly read-only, documented STB ports (no blind port scanning)
        val DOCUMENTED_STB_PORTS = listOf(
            20000 to "ALi / G-MScreen Control Protocol (TCP)",
            554 to "SAT>IP / ALi feng RTSP Streaming (TCP)",
            8080 to "STB HTTP Media Stream / Web Service",
            49152 to "UPnP / DLNA MediaRenderer Description",
            80 to "STB Web Interface / XML Status"
        )
    }

    suspend fun runSafeCompatibilityProbe(
        ipAddress: String,
        timeoutMs: Int = 1800
    ): CompatibilityDiagnosticReport = withContext(Dispatchers.IO) {
        if (!LocalNetworkGuard.isLocalLanAddress(ipAddress)) {
            return@withContext CompatibilityDiagnosticReport(
                targetIp = ipAddress,
                icmpOrTcpReachable = false,
                detectedHardwareVariant = "Blocked (Non-LAN IP)",
                probedPorts = emptyList(),
                provenProtocols = emptyList(),
                unverifiedOrMissingProtocols = listOf("Local LAN check failed"),
                requiresFirmwareSampleOrCapture = false,
                diagnosticSummaryArabic = "العنوان خارج الشبكة المحلية وتم رفض الفحص لحماية الأمان.",
                timestampEpochMs = System.currentTimeMillis()
            )
        }

        val outcomes = mutableListOf<PortProbeOutcome>()
        val proven = mutableListOf<String>()
        val unverified = mutableListOf<String>()

        for ((port, label) in DOCUMENTED_STB_PORTS) {
            val isOpen = checkTcpPortOpen(ipAddress, port, timeoutMs)
            var banner = ""
            if (isOpen && port == 20000) {
                // Safe read-only Request 15 (Get Device Info & Status)
                val tcpClient = GmScreenFramedTcpClient()
                val conn = tcpClient.connect(ipAddress, 20000, timeoutMs)
                if (conn is AppResult.Success) {
                    val infoResp = tcpClient.sendRequest("{\"request\":\"15\"}", expectResponse = true)
                    if (infoResp is AppResult.Success && infoResp.data.isNotBlank()) {
                        banner = infoResp.data.take(180)
                        proven.add("G-MScreen TCP Port 20000 (Request 15 Info Verified)")
                    }
                    tcpClient.disconnect()
                }
            } else if (isOpen && port == 554) {
                banner = probeRtspOptionsReadOnly(ipAddress, 554, timeoutMs)
                if (banner.contains("RTSP/1.0", ignoreCase = true)) {
                    proven.add("RTSP / SAT>IP Port 554 (${banner.lineSequence().firstOrNull().orEmpty()})")
                }
            } else if (isOpen) {
                proven.add("$label (Port $port Open)")
            } else {
                unverified.add("$label (Port $port Closed)")
            }

            outcomes.add(PortProbeOutcome(port, label, isOpen, banner))
        }

        val anyReachable = outcomes.any { it.isOpen } || try {
            InetAddress.getByName(ipAddress).isReachable(timeoutMs)
        } catch (_: Exception) {
            false
        }

        val gmScreenVerified = proven.any { it.contains("G-MScreen") }
        val detectedVariant = when {
            gmScreenVerified && outcomes.any { it.port == 20000 && it.bannerOrResponseSnippet.contains("Captain", true) } ->
                "Senator Captain Q2 4K CA (ALi 2661 - G-MScreen Verified)"
            gmScreenVerified ->
                "ALi 2661 / G-MScreen Compatible Receiver"
            anyReachable && outcomes.any { it.port == 49152 && it.isOpen } ->
                "Senator Captain 4K (GX Variant - UPnP/DLNA Only, Proprietary Control Unverified)"
            anyReachable ->
                "Reachable Host (No G-MScreen Port 20000 Response)"
            else ->
                "Offline / Unreachable"
        }

        val summaryAr = when {
            gmScreenVerified ->
                "تم إثبات توافق الرسيفر مع بروتوكول G-MScreen (منفذ 20000) واستخراج معلومات الجهاز بنجاح."
            anyReachable ->
                "الجهاز متصل بالشبكة ولكن منفذ G-MScreen (20000) مغلق أو يستخدم بروتوكول GX مختلفاً. يرجى تفعيل خيار G-MScreen / الشبكة من إعدادات الرسيفر أو تزويد عينة اتصال."
            else ->
                "تعذر الوصول إلى عنوان IP المحدد على الشبكة المحلية."
        }

        StructuredLogger.info(
            "ProtocolDiscovery",
            "Completed safe compatibility probe",
            mapOf("ip" to ipAddress, "variant" to detectedVariant, "provenCount" to proven.size)
        )

        CompatibilityDiagnosticReport(
            targetIp = ipAddress,
            icmpOrTcpReachable = anyReachable,
            detectedHardwareVariant = detectedVariant,
            probedPorts = outcomes,
            provenProtocols = proven,
            unverifiedOrMissingProtocols = unverified,
            requiresFirmwareSampleOrCapture = anyReachable && !gmScreenVerified,
            diagnosticSummaryArabic = summaryAr,
            timestampEpochMs = System.currentTimeMillis()
        )
    }

    private fun checkTcpPortOpen(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun probeRtspOptionsReadOnly(host: String, port: Int, timeoutMs: Int): String {
        return try {
            Socket().use { socket ->
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val req = "OPTIONS rtsp://$host:$port/ RTSP/1.0\r\nCSeq: 1\r\nUser-Agent: SmartReceiverPro-Probe\r\n\r\n"
                socket.getOutputStream().write(req.toByteArray(Charsets.US_ASCII))
                val buf = ByteArray(512)
                val n = socket.getInputStream().read(buf)
                if (n > 0) String(buf, 0, n, Charsets.UTF_8) else ""
            }
        } catch (_: Exception) {
            ""
        }
    }
}
