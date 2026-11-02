package com.smartreceiverpro.core.network

import com.smartreceiverpro.core.security.LocalNetworkGuard
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.DeflaterOutputStream

class GmScreenProtocolUnitTest {
    @Test fun `frame payload uses byte length`() {
        assertEquals("Start0000016End{\"request\":\"15\"}",
            String(GmScreenFramedTcpClient.framePayload("{\"request\":\"15\"}"), Charsets.UTF_8))
    }

    @Test fun `readExact joins fragmented reads`() {
        val src = object : InputStream() {
            val data = "abcdefgh".toByteArray()
            var pos = 0
            override fun read(): Int = if (pos == data.size) -1 else data[pos++].toInt() and 255
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (pos == data.size) return -1
                val n = minOf(2, len, data.size - pos)
                System.arraycopy(data, pos, b, off, n); pos += n; return n
            }
        }
        assertArrayEquals("abcdefgh".toByteArray(), GmScreenFramedTcpClient.readExact(src, 8))
    }

    @Test fun `readExact fails on early EOF`() {
        try {
            GmScreenFramedTcpClient.readExact(ByteArrayInputStream(byteArrayOf(1, 2)), 3)
            fail("Expected EOFException")
        } catch (_: java.io.EOFException) { }
    }

    @Test fun `GCDH length uses unsigned little endian bytes`() {
        val h = ByteArray(16); "GCDH".toByteArray().copyInto(h)
        h[4] = 0x34; h[5] = 0x12
        assertEquals(0x1234, GmScreenFramedTcpClient.gcdhBodyLength(h))
    }

    @Test fun `zero GCDH length is empty acknowledgement`() {
        val h = ByteArray(16); "GCDH".toByteArray().copyInto(h)
        assertEquals(0, GmScreenFramedTcpClient.gcdhBodyLength(h))
    }

    @Test fun `decode zlib response`() {
        val expected = "[{\"ProductName\":\"Senator Captain Q2 4K\"}]"
        val out = ByteArrayOutputStream()
        DeflaterOutputStream(out).use { it.write(expected.toByteArray(Charsets.UTF_8)) }
        assertEquals(expected, GmScreenFramedTcpClient.decodeResponseBytes(out.toByteArray()))
    }

    @Test fun `LAN guard`() {
        assertTrue(LocalNetworkGuard.isLocalLanAddress("192.168.1.50"))
        assertTrue(LocalNetworkGuard.isLocalLanAddress("10.0.0.15"))
        assertFalse(LocalNetworkGuard.verifyTargetAllowed("8.8.8.8", false))
    }
}
