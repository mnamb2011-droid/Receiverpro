package com.smartreceiverpro.core.network

import com.smartreceiverpro.core.common.AppResult
import com.smartreceiverpro.core.common.ErrorCode
import com.smartreceiverpro.core.common.StructuredLogger
import com.smartreceiverpro.core.security.LocalNetworkGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import java.util.zip.InflaterInputStream

/**
 * Implements the ALi / G-MScreen binary-framed TCP protocol (default port 20000)
 * used by ALi 2661 (Senator Captain Q2 4K CA) and ALi 35xx/36xx/38xx receivers.
 *
 * Packet Framing Format:
 *   "Start" + 7-digit zero-padded byte length + "End" + UTF-8 JSON/XML payload
 *   Example: Start0000016End{"request":"15"}
 *
 * Response Format:
 *   1. Optional 16-byte "GCDH..." header acknowledgement
 *   2. Zlib-compressed (0x78 0x9C) or plain UTF-8 JSON/XML response
 */
class GmScreenFramedTcpClient(
    private val allowExternalLan: Boolean = false
) {
    private val socketMutex = Mutex()
    private var socket: Socket? = null
    private var currentHost: String? = null
    private var currentPort: Int = DEFAULT_GMSCREEN_PORT

    companion object {
        const val DEFAULT_GMSCREEN_PORT = 20000
        const val BROADCAST_MAGIC_CODE = "39WwijOog54a"
        const val ACK_HEADER_PREFIX = "GCDH"

        fun framePayload(payload: String): ByteArray {
            val payloadBytes = payload.toByteArray(Charsets.UTF_8)
            val lengthFormatted = String.format(Locale.US, "%07d", payloadBytes.size)
            val header = "Start${lengthFormatted}End".toByteArray(Charsets.UTF_8)
            return header + payloadBytes
        }

        fun decodeResponseBytes(rawBytes: ByteArray): String {
            if (rawBytes.isEmpty()) return ""
            // Strip leading 16-byte GCDH header if attached in the same TCP read buffer
            val payloadBytes = if (rawBytes.size > 16 &&
                String(rawBytes, 0, 4, Charsets.US_ASCII) == ACK_HEADER_PREFIX
            ) {
                rawBytes.copyOfRange(16, rawBytes.size)
            } else {
                rawBytes
            }

            if (payloadBytes.size >= 2 &&
                payloadBytes[0] == 0x78.toByte() &&
                (payloadBytes[1] == 0x9C.toByte() || payloadBytes[1] == 0x01.toByte() || payloadBytes[1] == 0xDA.toByte())
            ) {
                return decompressZlib(payloadBytes)
            }
            // Also check if a Start00000XXEnd wrapper is present on uncompressed response
            val rawString = String(payloadBytes, Charsets.UTF_8)
            if (rawString.startsWith("Start") && rawString.length > 15 && rawString.substring(12, 15) == "End") {
                return rawString.substring(15)
            }
            return rawString
        }

        private fun decompressZlib(compressed: ByteArray): String {
            ByteArrayInputStream(compressed).use { bais ->
                InflaterInputStream(bais).use { inflater ->
                    ByteArrayOutputStream().use { baos ->
                        val buffer = ByteArray(4096)
                        var read: Int
                        while (inflater.read(buffer).also { read = it } != -1) {
                            baos.write(buffer, 0, read)
                        }
                        return baos.toString(Charsets.UTF_8.name())
                    }
                }
            }
        }
    }

    suspend fun connect(host: String, port: Int = DEFAULT_GMSCREEN_PORT, timeoutMs: Int = 3500): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            socketMutex.withLock {
                if (!LocalNetworkGuard.verifyTargetAllowed(host, allowExternalLan)) {
                    return@withLock AppResult.Error(
                        ErrorCode.EXTERNAL_NETWORK_BLOCKED,
                        "تم حظر الاتصال خارج الشبكة المحلية حفاظاً على الأمان."
                    )
                }
                try {
                    closeInternal()
                    val newSocket = Socket()
                    newSocket.soTimeout = timeoutMs
                    newSocket.connect(InetSocketAddress(host, port), timeoutMs)
                    socket = newSocket
                    currentHost = host
                    currentPort = port
                    StructuredLogger.info("GmScreenTcp", "Connected to STB TCP port", mapOf("host" to host, "port" to port))
                    AppResult.Success(Unit)
                } catch (e: Exception) {
                    StructuredLogger.warn("GmScreenTcp", "TCP connection failed", mapOf("host" to host, "port" to port, "reason" to e.message))
                    AppResult.Error(ErrorCode.PORT_CLOSED, "تعذر فتح منفذ التحكم ($port) على الجهاز $host: ${e.message}", e)
                }
            }
        }

    suspend fun sendRequest(payload: String, expectResponse: Boolean = true): AppResult<String> =
        withContext(Dispatchers.IO) {
            socketMutex.withLock {
                val activeSocket = socket
                if (activeSocket == null || activeSocket.isClosed || !activeSocket.isConnected) {
                    return@withLock AppResult.Error(ErrorCode.NETWORK_UNREACHABLE, "الجهاز غير متصل حالياً.")
                }
                try {
                    val framed = framePayload(payload)
                    val out = activeSocket.getOutputStream()
                    out.write(framed)
                    out.flush()

                    if (!expectResponse) {
                        return@withLock AppResult.Success("")
                    }

                    val input = activeSocket.getInputStream()
                    val responseBytes = readResponsePacket(input)
                    if (responseBytes.isEmpty()) {
                        return@withLock AppResult.Error(ErrorCode.INVALID_RESPONSE_PAYLOAD, "لم يرسل الرسيفر أي استجابة.")
                    }
                    val decoded = decodeResponseBytes(responseBytes)
                    AppResult.Success(decoded)
                } catch (e: Exception) {
                    StructuredLogger.error("GmScreenTcp", "Command execution error", e)
                    AppResult.Error(ErrorCode.CONNECTION_TIMEOUT, "انتهت مهلة انتظار استجابة الرسيفر: ${e.message}", e)
                }
            }
        }

    private fun readResponsePacket(input: InputStream): ByteArray {
        val firstChunk = ByteArray(8192)
        val firstRead = input.read(firstChunk)
        if (firstRead <= 0) return ByteArray(0)

        val baos = ByteArrayOutputStream()
        // If the first read is purely the 16-byte GCDH acknowledgement header, read the next frame
        if (firstRead == 16 && String(firstChunk, 0, 4, Charsets.US_ASCII) == ACK_HEADER_PREFIX) {
            val secondRead = input.read(firstChunk)
            if (secondRead > 0) {
                baos.write(firstChunk, 0, secondRead)
            }
        } else {
            baos.write(firstChunk, 0, firstRead)
        }
        return baos.toByteArray()
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        socketMutex.withLock {
            closeInternal()
        }
    }

    private fun closeInternal() {
        try {
            socket?.close()
        } catch (_: Exception) {
        } finally {
            socket = null
        }
    }
}
