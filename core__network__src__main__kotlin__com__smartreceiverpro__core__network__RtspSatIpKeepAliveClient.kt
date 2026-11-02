package com.smartreceiverpro.core.network

import com.smartreceiverpro.core.common.StructuredLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Prevents the ALi feng/2.1.0_rc1 SAT>IP RTSP server (port 554) from tearing down
 * live TV streams after 41 seconds by sending a periodic RTSP OPTIONS heartbeat
 * with the active Session ID every 28 seconds.
 */
class RtspSatIpKeepAliveClient {
    private var keepAliveJob: Job? = null
    private var cSeqCounter = 10

    fun startKeepAlive(
        scope: CoroutineScope,
        host: String,
        rtspPort: Int = 554,
        sessionId: String?,
        intervalMs: Long = 28_000L
    ) {
        stopKeepAlive()
        keepAliveJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(intervalMs)
                sendOptionsPing(host, rtspPort, sessionId)
            }
        }
    }

    private fun sendOptionsPing(host: String, port: Int, sessionId: String?) {
        try {
            Socket().use { socket ->
                socket.soTimeout = 2500
                socket.connect(InetSocketAddress(host, port), 2500)
                val cseq = cSeqCounter++
                val sessionHeader = if (!sessionId.isNullOrBlank()) "Session: $sessionId\r\n" else ""
                val request = buildString {
                    append("OPTIONS rtsp://$host:$port/ RTSP/1.0\r\n")
                    append("CSeq: $cseq\r\n")
                    append(sessionHeader)
                    append("User-Agent: SmartReceiverPro/1.0\r\n\r\n")
                }
                socket.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
                socket.getOutputStream().flush()
            }
        } catch (e: Exception) {
            StructuredLogger.warn("RtspKeepAlive", "RTSP OPTIONS heartbeat failed", mapOf("host" to host, "reason" to e.message))
        }
    }

    fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }
}
