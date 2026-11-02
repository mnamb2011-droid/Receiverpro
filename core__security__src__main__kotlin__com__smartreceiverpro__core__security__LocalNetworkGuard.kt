package com.smartreceiverpro.core.security

import java.net.InetAddress

/**
 * Enforces the cybersecurity rule:
 * - Never send local network receiver data to external servers.
 * - Never execute receiver commands outside the local RFC1918/link-local LAN
 *   unless the user has explicitly granted remote-network consent.
 */
object LocalNetworkGuard {

    fun isLocalLanAddress(hostOrIp: String): Boolean {
        val trimmed = hostOrIp.trim()
        if (trimmed.isEmpty()) return false
        return try {
            val address = InetAddress.getByName(trimmed)
            address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress
        } catch (e: Exception) {
            false
        }
    }

    fun verifyTargetAllowed(hostOrIp: String, allowExternalWithExplicitConsent: Boolean): Boolean {
        if (isLocalLanAddress(hostOrIp)) return true
        return allowExternalWithExplicitConsent
    }
}
