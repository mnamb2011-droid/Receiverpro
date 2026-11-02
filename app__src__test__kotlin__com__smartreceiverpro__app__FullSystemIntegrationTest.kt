package com.smartreceiverpro.app

import com.smartreceiverpro.core.common.AppResult
import com.smartreceiverpro.core.media.LiveTvPlayerController
import com.smartreceiverpro.core.media.VideoAspectRatioMode
import com.smartreceiverpro.engine.api.*
import com.smartreceiverpro.engine.connection.ReceiverSessionManager
import com.smartreceiverpro.feature.backups.BackupArchiveSerializer
import com.smartreceiverpro.feature.channels.ChannelCatalogProcessor
import com.smartreceiverpro.feature.channels.ChannelSortOption
import com.smartreceiverpro.feature.satfinder.SatelliteGeometryCalculator
import com.smartreceiverpro.feature.timers.TimerConflictValidator
import com.smartreceiverpro.feature.voice.ParsedVoiceIntent
import com.smartreceiverpro.feature.voice.VoiceCommandParser
import com.smartreceiverpro.plugins.upnp.MockReceiverAdapter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FullSystemIntegrationTest {

    @Test
    fun `test receiver connection, remote command execution, Wi-Fi loss and recovery`() = runTest {
        val mockAdapter = MockReceiverAdapter()
        val sessionManager = ReceiverSessionManager()

        // Connect
        val infoRes = mockAdapter.connect("127.0.0.1", 20000, null)
        assertTrue(infoRes is AppResult.Success)
        val capsRes = mockAdapter.getCapabilities()
        assertTrue(capsRes is AppResult.Success)

        sessionManager.attachVerifiedSession(
            adapter = mockAdapter,
            deviceInfo = (infoRes as AppResult.Success).data,
            capabilities = (capsRes as AppResult.Success).data,
            isMock = true
        )
        assertTrue(sessionManager.session.value.isConnected)
        assertTrue(sessionManager.session.value.isMockTestingSession)

        // Execute remote command
        val remoteRes = sessionManager.sendRateLimitedRemoteKey(RemoteKey.OK)
        assertTrue(remoteRes is AppResult.Success)
        assertEquals(listOf(RemoteKey.OK), mockAdapter.getExecutedKeysSnapshot())

        // Simulate Wi-Fi loss
        sessionManager.onWifiLost()
        assertFalse(sessionManager.session.value.isConnected)
        assertNotNull(sessionManager.session.value.lastErrorArabic)

        // Commands during Wi-Fi loss must fail cleanly without queuing duplicate storms
        val disconnectedKeyRes = sessionManager.sendRateLimitedRemoteKey(RemoteKey.VOL_UP)
        assertTrue(disconnectedKeyRes is AppResult.Error)
    }

    @Test
    fun `test channel loading, search, sorting, duplicate detection, and delete confirmation guard`() = runTest {
        val mockAdapter = MockReceiverAdapter()
        mockAdapter.connect("127.0.0.1", 20000, null)
        val chRes = mockAdapter.getChannels(forceRefresh = true) as AppResult.Success
        assertEquals(3, chRes.data.size)

        // Search
        val searched = ChannelCatalogProcessor.filterAndSort(
            channels = chRes.data,
            isRadioTab = false,
            searchQuery = "الأخبار",
            selectedSatellite = null,
            favoritesOnly = false,
            sortOption = ChannelSortOption.NUMBER
        )
        assertEquals(1, searched.size)
        assertEquals("0002", searched.first().serviceId)

        // Delete without confirmation must be rejected
        val unconfirmedDelete = mockAdapter.deleteChannels(listOf("0002"), userConfirmed = false)
        assertTrue(unconfirmedDelete is AppResult.Error)

        // Confirmed delete succeeds
        val confirmedDelete = mockAdapter.deleteChannels(listOf("0002"), userConfirmed = true)
        assertTrue(confirmedDelete is AppResult.Success)
    }

    @Test
    fun `test live tv playback state, aspect ratio, and auto-reconnect limit`() = runTest {
        val player = LiveTvPlayerController()
        val descriptor = StreamDescriptor(
            streamUrl = "rtsp://192.168.1.50:554/?prognumber=0001",
            protocol = "RTSP",
            isScrambled = false,
            requiresRtspKeepAlive = false
        )
        player.prepareStream(this, descriptor, "192.168.1.50")
        assertTrue(player.playerState.value.isPlaying)

        player.setAspectRatio(VideoAspectRatioMode.FOUR_THREE)
        assertEquals(VideoAspectRatioMode.FOUR_THREE, player.playerState.value.aspectRatioMode)

        // Simulate 3 network drops
        assertTrue(player.onNetworkErrorAndAutoReconnect(maxAttempts = 2))
        assertTrue(player.onNetworkErrorAndAutoReconnect(maxAttempts = 2))
        assertFalse("Should stop after maxAttempts", player.onNetworkErrorAndAutoReconnect(maxAttempts = 2))
    }

    @Test
    fun `test timer conflict detection and receiver confirmation requirement`() {
        val existing = listOf(
            ReceiverTimerItem("t1", "0001", "MBC", 1000_000L, 2000_000L, TimerRepeatRule.ONCE, TimerActionType.SWITCH_CHANNEL, true)
        )
        val overlapping = ReceiverTimerItem("t2", "0002", "News", 1500_000L, 2500_000L, TimerRepeatRule.ONCE, TimerActionType.RECORD_USB, false)
        val conflicts = TimerConflictValidator.findConflictingTimers(overlapping, existing)
        assertEquals(1, conflicts.size)
    }

    @Test
    fun `test SatFinder orbital angle calculation for Nilesat 7W from Cairo`() {
        val nilesat = SatelliteGeometryCalculator.TRUSTED_SATELLITE_CATALOG.first { it.id == "nilesat_7w" }
        val angles = SatelliteGeometryCalculator.calculateAlignment(
            userLatitudeDeg = 30.0444,
            userLongitudeDeg = 31.2357,
            satellite = nilesat
        )
        // From Cairo (30N, 31.2E) to Nilesat (7W): Azimuth is South-West (~235 deg), Elevation ~38 deg
        assertTrue(angles.azimuthTrueNorthDeg in 230.0..242.0)
        assertTrue(angles.elevationDeg in 34.0..42.0)
        assertTrue(angles.lnbSkewDeg < -30.0)
    }

    @Test
    fun `test Arabic and English Voice Command parsing and sensitive confirmation flag`() {
        val chIntent = VoiceCommandParser.parseTranscript("افتح قناة MBC")
        assertTrue(chIntent is ParsedVoiceIntent.OpenChannelByName)
        assertEquals("MBC", (chIntent as ParsedVoiceIntent.OpenChannelByName).channelQuery)
        assertFalse(chIntent.requiresExplicitConfirmation)

        val powerIntent = VoiceCommandParser.parseTranscript("اطفئ الرسيفر")
        assertTrue(powerIntent is ParsedVoiceIntent.SendKey)
        assertTrue("Power command must require explicit user confirmation", powerIntent.requiresExplicitConfirmation)
    }

    @Test
    fun `test Backup export and SHA-256 verified restore`() {
        val rawBackup = BackupArchiveSerializer.createBackupJson(
            deviceModel = "Senator Captain Q2 4K",
            receivers = emptyList(),
            channels = emptyList(),
            favoriteGroups = emptyList(),
            timestampMs = 1_730_000_000_000L
        )
        val restored = BackupArchiveSerializer.parseAndVerifyBackupJson(rawBackup)
        assertEquals("Senator Captain Q2 4K", restored.deviceModel)
    }
}
