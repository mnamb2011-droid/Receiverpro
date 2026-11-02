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
import java.io.EOFException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import java.util.zip.InflaterInputStream

class GmScreenFramedTcpClient(private val allowExternalLan: Boolean = false) {
    private val socketMutex = Mutex()
    private var socket: Socket? = null

    companion object {
        const val DEFAULT_GMSCREEN_PORT = 20000
        const val BROADCAST_MAGIC_CODE = "39WwijOog54a"
        const val ACK_HEADER_PREFIX = "GCDH"
        const val HANDSHAKE_GREETING_SIZE = 108
        private const val MAX_RESPONSE_SIZE = 1_048_576

        fun framePayload(payload: String): ByteArray {
            val bytes = payload.toByteArray(Charsets.UTF_8)
            val len = String.format(Locale.US, "%07d", bytes.size)
            return "Start${len}End".toByteArray(Charsets.US_ASCII) + bytes
        }

        internal fun readExact(input: InputStream, length: Int): ByteArray {
            require(length >= 0)
            val result = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val n = input.read(result, offset, length - offset)
                if (n < 0) throw EOFException("Expected $length bytes; received $offset")
                if (n == 0) {
                    val one = input.read()
                    if (one < 0) throw EOFException("Expected $length bytes; received $offset")
                    result[offset++] = one.toByte()
                } else offset += n
            }
            return result
        }

        internal fun gcdhBodyLength(header: ByteArray): Int {
            require(header.size == 16)
            require(String(header, 0, 4, Charsets.US_ASCII) == ACK_HEADER_PREFIX)
            return (header[4].toInt() and 0xff) or
                ((header[5].toInt() and 0xff) shl 8)
        }

        
fun decodeResponseBytes(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""

    val isZlib = if (bytes.size >= 2) {
        val cmf = bytes[0].toInt() and 0xff
        val flg = bytes[1].toInt() and 0xff

        (cmf and 0x0f) == 8 &&
        (cmf shr 4) <= 7 &&
        ((cmf shl 8) + flg) % 31 == 0
    } else {
        false
    }

    val decoded = if (isZlib) {
        try {
            decompressZlib(bytes)
        } catch (_: Exception) {
            String(bytes, Charsets.UTF_8)
        }
    } else {
        String(bytes, Charsets.UTF_8)
    }

    return if (
        decoded.startsWith("Start") &&
        decoded.length >= 15 &&
        decoded.substring(12, 15) == "End"
    ) {
        decoded.substring(15)
    } else {
        decoded
    }
}


        private fun decompressZlib(bytes: ByteArray): String {
            InflaterInputStream(ByteArrayInputStream(bytes)).use { inflater ->
                ByteArrayOutputStream().use { out ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        val n = inflater.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                    }
                    return out.toString(Charsets.UTF_8.name())
                }
            }
        }
    }

    suspend fun connect(host: String, port: Int = DEFAULT_GMSCREEN_PORT, timeoutMs: Int = 5000): AppResult<Unit> =
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
                    val s = Socket()
                    s.soTimeout = timeoutMs
                    s.tcpNoDelay = true
                    s.connect(InetSocketAddress(host, port), timeoutMs)
                    socket = s
                    StructuredLogger.info("GmScreenTcp", "Connected to STB TCP port",
                        mapOf("host" to host, "port" to port))
                    AppResult.Success(Unit)
                } catch (e: Exception) {
                    closeInternal()
                    StructuredLogger.warn("GmScreenTcp", "TCP connection failed",
                        mapOf("host" to host, "port" to port, "reason" to e.message))
                    AppResult.Error(ErrorCode.PORT_CLOSED,
                        "تعذر فتح منفذ التحكم ($port) على الجهاز $host: ${e.message}", e)
                }
            }
        }

    suspend fun sendRequest(payload: String, expectResponse: Boolean = true): AppResult<String> =
        withContext(Dispatchers.IO) {
            socketMutex.withLock {
                val s = socket
                if (s == null || s.isClosed || !s.isConnected) {
                    return@withLock AppResult.Error(ErrorCode.NETWORK_UNREACHABLE, "الجهاز غير متصل حالياً.")
                }
                try {
                    s.getOutputStream().apply { write(framePayload(payload)); flush() }
                    if (!expectResponse) return@withLock AppResult.Success("")
                    val input = s.getInputStream()

                    // request 998 returns a raw 108-byte greeting, not a JSON/GCDH packet.
                    if (payload.contains("request=\"998\"") || payload.contains("request='998'")) {
                        val greeting = readExact(input, HANDSHAKE_GREETING_SIZE)
                        if (greeting[0] != '['.code.toByte() || greeting[1] != '['.code.toByte()) {
                            throw IllegalStateException("Invalid G-MScreen greeting prefix")
                        }
                        return@withLock AppResult.Success(String(greeting, Charsets.UTF_8))
                    }

                    val packet = readResponsePacket(input)
                    if (packet.isEmpty()) return@withLock AppResult.Error(
                        ErrorCode.INVALID_RESPONSE_PAYLOAD, "أرسل الرسيفر إقرار GCDH فارغاً دون بيانات.")
                    AppResult.Success(decodeResponseBytes(packet))
                } catch (e: Exception) {
                    StructuredLogger.error("GmScreenTcp", "Command execution error", e)
                    AppResult.Error(ErrorCode.CONNECTION_TIMEOUT,
                        "تعذر استلام استجابة كاملة من الرسيفر: ${e.message}", e)
                }
            }
        }

    private fun readResponsePacket(input: InputStream): ByteArray {
        val prefix = readExact(input, 4)
        if (String(prefix, Charsets.US_ASCII) == ACK_HEADER_PREFIX) {
            val header = prefix + readExact(input, 12)
            val length = gcdhBodyLength(header)
            if (length == 0) return ByteArray(0) // transport ACK only
            require(length <= MAX_RESPONSE_SIZE) { "GCDH body too large: $length" }
            return readExact(input, length)
        }
        // Fallback for non-GCDH firmware responses.
        val out = ByteArrayOutputStream()
        out.write(prefix)
        val pending = input.available()
        if (pending > 0) out.write(readExact(input, pending))
        return out.toByteArray()
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        socketMutex.withLock { closeInternal() }
    }

    private fun closeInternal() {
        try { socket?.close() } catch (_: Exception) { } finally { socket = null }
    }
}
