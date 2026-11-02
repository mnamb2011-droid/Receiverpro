package com.smartreceiverpro.core.network

import com.smartreceiverpro.core.security.LocalNetworkGuard
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream

class GmScreenProtocolUnitTest {

    @Test
    fun `framePayload formats Start + 7 digit length + End + JSON correctly`() {
        val json = "{\"request\":\"15\"}"
        val framed = String(GmScreenFramedTcpClient.framePayload(json), Charsets.UTF_8)
        assertEquals("Start0000016End{\"request\":\"15\"}", framed)
    }

    @Test
    fun `decodeResponseBytes strips 16-byte GCDH ack header and decompresses zlib payload`() {
        val expectedJson = "[{\"StbStatus\":1,\"ProductName\":\"Senator Captain Q2 4K\"}]"
        val baos = ByteArrayOutputStream()
        DeflaterOutputStream(baos).use { it.write(expectedJson.toByteArray(Charsets.UTF_8)) }
        val zlibBytes = baos.toByteArray()

        val gcdhHeader = "GCDH000000000000".toByteArray(Charsets.US_ASCII)
        val combined = gcdhHeader + zlibBytes

        val decoded = GmScreenFramedTcpClient.decodeResponseBytes(combined)
        assertEquals(expectedJson, decoded)
    }

    @Test
    fun `LocalNetworkGuard allows RFC1918 LAN IPs and blocks external public IPs without consent`() {
        assertTrue(LocalNetworkGuard.isLocalLanAddress("192.168.1.50"))
        assertTrue(LocalNetworkGuard.isLocalLanAddress("10.0.0.15"))
        assertTrue(LocalNetworkGuard.isLocalLanAddress("127.0.0.1"))
        assertFalse(LocalNetworkGuard.verifyTargetAllowed("8.8.8.8", allowExternalWithExplicitConsent = false))
    }
}
