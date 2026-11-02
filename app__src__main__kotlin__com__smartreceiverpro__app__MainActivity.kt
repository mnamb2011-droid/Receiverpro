package com.smartreceiverpro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smartreceiverpro.core.common.AppResult
import com.smartreceiverpro.core.design.SmartReceiverProTheme
import com.smartreceiverpro.core.discovery.ThreeStageDiscoveryManager
import com.smartreceiverpro.core.media.LiveTvPlayerController
import com.smartreceiverpro.engine.api.DiscoveredDeviceCandidate
import com.smartreceiverpro.engine.api.ReceiverChannel
import com.smartreceiverpro.engine.connection.ReceiverSessionManager
import com.smartreceiverpro.feature.channels.ChannelManagerScreen
import com.smartreceiverpro.feature.dashboard.DashboardScreen
import com.smartreceiverpro.feature.devices.DeviceDiscoveryScreen
import com.smartreceiverpro.feature.epg.EpgScreen
import com.smartreceiverpro.feature.livetv.LiveTvPlayerScreen
import com.smartreceiverpro.feature.remote.AdvancedRemoteScreen
import com.smartreceiverpro.feature.remote.MotionSensorController
import com.smartreceiverpro.feature.satfinder.SatFinderScreen
import com.smartreceiverpro.feature.settings.ReceiverSettingsScreen
import com.smartreceiverpro.feature.timers.TimerManagerScreen
import com.smartreceiverpro.plugins.gmscreen.GeantReceiverAdapter
import com.smartreceiverpro.plugins.gmscreen.GmScreenReceiverAdapter
import com.smartreceiverpro.plugins.gmscreen.StarSatReceiverAdapter
import com.smartreceiverpro.plugins.gmscreen.TigerReceiverAdapter
import com.smartreceiverpro.plugins.senator.SenatorCaptainAdapter
import com.smartreceiverpro.plugins.upnp.GenericUpnpDlnaAdapter
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val sessionManager = ReceiverSessionManager()
    private val discoveryManager = ThreeStageDiscoveryManager()
    private val livePlayerController = LiveTvPlayerController()
    private lateinit var motionSensorController: MotionSensorController

    // Registered production receiver plugins (Senator Captain first, followed by G-MScreen, StarSat, Tiger, Geant, UPnP)
    private val registeredPlugins by lazy {
        listOf(
            SenatorCaptainAdapter(),
            GmScreenReceiverAdapter(),
            StarSatReceiverAdapter(),
            TigerReceiverAdapter(),
            GeantReceiverAdapter(),
            GenericUpnpDlnaAdapter()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        motionSensorController = MotionSensorController(applicationContext)

        lifecycleScope.launch {
            motionSensorController.motionKeyEvents.collect { key ->
                sessionManager.sendRateLimitedRemoteKey(key)
            }
        }

        setContent {
            var languageCode by remember { mutableStateOf("ar") }
            val session by sessionManager.session.collectAsState()
            val verificationState by discoveryManager.verificationState.collectAsState()
            val playerState by livePlayerController.playerState.collectAsState()
            var discoveredCandidates by remember { mutableStateOf<List<DiscoveredDeviceCandidate>>(emptyList()) }
            var loadedChannels by remember { mutableStateOf<List<ReceiverChannel>>(emptyList()) }
            var currentChannel by remember { mutableStateOf<ReceiverChannel?>(null) }
            var isScanning by remember { mutableStateOf(false) }

            SmartReceiverProTheme(darkTheme = true, languageCode = languageCode) {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "dashboard") {
                    composable("dashboard") {
                        DashboardScreen(
                            activeSession = session,
                            savedReceivers = emptyList(),
                            isScanning = isScanning,
                            onScanDevicesClick = { navController.navigate("devices") },
                            onAddManualIpClick = { navController.navigate("devices") },
                            onReconnectSavedDevice = { saved ->
                                lifecycleScope.launch {
                                    connectAndVerify(saved.ipAddress, saved.controlPort, null) { chs ->
                                        loadedChannels = chs
                                    }
                                }
                            },
                            onNavigateToFeature = { route -> navController.navigate(route) }
                        )
                    }

                    composable("devices") {
                        DeviceDiscoveryScreen(
                            discoveredCandidates = discoveredCandidates,
                            verificationState = verificationState,
                            isScanning = isScanning,
                            onStartAutoScan = {
                                lifecycleScope.launch {
                                    isScanning = true
                                    discoveredCandidates = discoveryManager.scanLanForCandidates()
                                    isScanning = false
                                }
                            },
                            onConnectByIp = { ip, port, pass ->
                                lifecycleScope.launch {
                                    connectAndVerify(ip, port, pass) { chs ->
                                        loadedChannels = chs
                                    }
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("remote") {
                        AdvancedRemoteScreen(
                            supportsRemote = session.capabilities.supportsRemote,
                            hapticEnabled = true,
                            onSendKey = { key ->
                                lifecycleScope.launch { sessionManager.sendRateLimitedRemoteKey(key) }
                            },
                            onCalibrateMotion = { motionSensorController.calibrateCenter() },
                            onToggleMotionActive = { enabled, sens ->
                                if (enabled) motionSensorController.startListening(sens)
                                else motionSensorController.stopListening()
                            }
                        )
                    }

                    composable("channels") {
                        ChannelManagerScreen(
                            channels = loadedChannels,
                            capabilities = session.capabilities,
                            onSwitchChannel = { ch ->
                                lifecycleScope.launch {
                                    session.adapter?.switchChannel(ch.serviceId)
                                    currentChannel = ch
                                }
                            },
                            onWatchLiveOnPhone = { ch ->
                                lifecycleScope.launch {
                                    val streamRes = session.adapter?.getStreamUrl(ch.serviceId)
                                    if (streamRes is AppResult.Success && session.deviceInfo != null) {
                                        currentChannel = ch
                                        livePlayerController.prepareStream(
                                            lifecycleScope,
                                            streamRes.data,
                                            session.deviceInfo!!.ipAddress
                                        )
                                        navController.navigate("live-tv")
                                    }
                                }
                            },
                            onRenameChannel = { ch, newName ->
                                lifecycleScope.launch { session.adapter?.renameChannel(ch.serviceId, newName) }
                            },
                            onDeleteSelected = { ids, confirmed ->
                                lifecycleScope.launch { session.adapter?.deleteChannels(ids, confirmed) }
                            },
                            onToggleLockSelected = { ids, locked, pin ->
                                lifecycleScope.launch { session.adapter?.setChannelsLock(ids, locked, pin) }
                            },
                            onExportChannelsBackup = { navController.navigate("backups") }
                        )
                    }

                    composable("live-tv") {
                        LiveTvPlayerScreen(
                            supportsLiveTv = session.capabilities.supportsLiveTv,
                            playerState = playerState,
                            channels = loadedChannels,
                            currentChannel = currentChannel,
                            onSelectChannel = { ch ->
                                lifecycleScope.launch {
                                    val streamRes = session.adapter?.getStreamUrl(ch.serviceId)
                                    if (streamRes is AppResult.Success && session.deviceInfo != null) {
                                        currentChannel = ch
                                        livePlayerController.prepareStream(
                                            lifecycleScope,
                                            streamRes.data,
                                            session.deviceInfo!!.ipAddress
                                        )
                                    }
                                }
                            },
                            onChangeAspectRatio = { livePlayerController.setAspectRatio(it) },
                            onOpenExternalPlayer = {
                                livePlayerController.buildExternalPlayerIntent(this@MainActivity)?.let {
                                    startActivity(it)
                                }
                            },
                            onRetryStream = {
                                livePlayerController.onNetworkErrorAndAutoReconnect()
                            }
                        )
                    }

                    composable("epg") {
                        EpgScreen(
                            supportsEpg = session.capabilities.supportsEPG,
                            programs = emptyList(),
                            onScheduleReminder = {},
                            onSwitchToChannel = { serviceId ->
                                lifecycleScope.launch { session.adapter?.switchChannel(serviceId) }
                            }
                        )
                    }

                    composable("timers") {
                        TimerManagerScreen(
                            supportsTimers = session.capabilities.supportsTimers,
                            timers = emptyList(),
                            onDeleteTimer = { timerId ->
                                lifecycleScope.launch { session.adapter?.deleteTimer(timerId) }
                            }
                        )
                    }

                    composable("satfinder") {
                        var tunerSignal by remember { mutableStateOf<com.smartreceiverpro.engine.api.TunerSignalInfo?>(null) }
                        SatFinderScreen(
                            userLat = 30.1449,
                            userLon = 31.6306,
                            currentHeadingDeg = 210.5f,
                            supportsTunerSignal = session.capabilities.supportsSatFinderSignal,
                            realTunerSignal = tunerSignal,
                            onRefreshTunerSignal = {
                                lifecycleScope.launch {
                                    val sig = session.adapter?.getSignalInfo()
                                    if (sig is AppResult.Success) {
                                        tunerSignal = sig.data
                                    }
                                }
                            }
                        )
                    }

                    composable("settings") {
                        ReceiverSettingsScreen(
                            deviceInfo = session.deviceInfo,
                            capabilities = session.capabilities,
                            currentSettings = null,
                            onUpdateSleepTimer = {},
                            onExecuteFactoryResetDoubleConfirmed = {
                                lifecycleScope.launch {
                                    val cur = session.adapter?.getSettings()
                                    if (cur is AppResult.Success) {
                                        session.adapter?.updateSettings(cur.data, factoryResetDoubleConfirmed = true)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private suspend fun connectAndVerify(
        ip: String,
        port: Int,
        password: String?,
        onChannelsLoaded: (List<ReceiverChannel>) -> Unit
    ) {
        val result = discoveryManager.verifyAndConnect(ip, port, password, registeredPlugins)
        if (result is AppResult.Success) {
            val (adapter, info) = result.data
            val capsRes = adapter.getCapabilities()
            if (capsRes is AppResult.Success) {
                sessionManager.attachVerifiedSession(adapter, info, capsRes.data, isMock = false)
                if (capsRes.data.supportsChannelManagement) {
                    val chRes = adapter.getChannels(forceRefresh = true)
                    if (chRes is AppResult.Success) {
                        onChannelsLoaded(chRes.data)
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        motionSensorController.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        motionSensorController.stopListening()
        livePlayerController.release()
    }
}
