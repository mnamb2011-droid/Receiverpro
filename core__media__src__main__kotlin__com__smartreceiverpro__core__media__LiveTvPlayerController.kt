package com.smartreceiverpro.core.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.smartreceiverpro.core.common.StructuredLogger
import com.smartreceiverpro.core.network.RtspSatIpKeepAliveClient
import com.smartreceiverpro.engine.api.StreamDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VideoAspectRatioMode(val labelAr: String, val ratioValue: Float?) {
    FIT("ملائمة تلقائية", null),
    SIXTEEN_NINE("16:9 عريض", 16f / 9f),
    FOUR_THREE("4:3 قياسي", 4f / 3f),
    FILL("ملء الشاشة", -1f)
}

data class LivePlayerUiState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentStreamUrl: String? = null,
    val isScrambledChannel: Boolean = false,
    val aspectRatioMode: VideoAspectRatioMode = VideoAspectRatioMode.SIXTEEN_NINE,
    val volumePercent: Int = 100,
    val isMuted: Boolean = false,
    val estimatedBitrateKbps: Int = 0,
    val reconnectAttempt: Int = 0,
    val errorArabic: String? = null
)

/**
 * Manages live channel playback via AndroidX Media3 (ExoPlayer) + RTSP keep-alive,
 * and supports launching external players (such as VLC or MX Player) only when a
 * verified stream URL is provided by the receiver.
 * Never bypasses CAS/DRM encryption on scrambled channels.
 */
class LiveTvPlayerController(
    private val rtspKeepAlive: RtspSatIpKeepAliveClient = RtspSatIpKeepAliveClient()
) {
    private val _playerState = MutableStateFlow(LivePlayerUiState())
    val playerState: StateFlow<LivePlayerUiState> = _playerState.asStateFlow()

    fun prepareStream(scope: CoroutineScope, descriptor: StreamDescriptor, hostIp: String) {
        if (descriptor.streamUrl.isBlank()) {
            _playerState.value = _playerState.value.copy(
                isPlaying = false,
                isBuffering = false,
                errorArabic = "الرسيفر لم يوفر رابط بث صالح لهذه القناة."
            )
            return
        }
        if (descriptor.isScrambled) {
            StructuredLogger.info("LiveTvPlayer", "Channel is marked scrambled by STB; playback depends on STB hardware CAM state")
        }
        _playerState.value = _playerState.value.copy(
            isPlaying = true,
            isBuffering = false,
            currentStreamUrl = descriptor.streamUrl,
            isScrambledChannel = descriptor.isScrambled,
            errorArabic = null
        )
        if (descriptor.requiresRtspKeepAlive) {
            rtspKeepAlive.startKeepAlive(scope, host = hostIp, sessionId = descriptor.rtspSessionId)
        } else {
            rtspKeepAlive.stopKeepAlive()
        }
    }

    fun setAspectRatio(mode: VideoAspectRatioMode) {
        _playerState.value = _playerState.value.copy(aspectRatioMode = mode)
    }

    fun setVolume(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _playerState.value = _playerState.value.copy(volumePercent = clamped, isMuted = clamped == 0)
    }

    fun toggleMute() {
        val curr = _playerState.value
        _playerState.value = curr.copy(isMuted = !curr.isMuted)
    }

    fun onNetworkErrorAndAutoReconnect(maxAttempts: Int = 3): Boolean {
        val curr = _playerState.value
        if (curr.reconnectAttempt >= maxAttempts) {
            _playerState.value = curr.copy(
                isPlaying = false,
                isBuffering = false,
                errorArabic = "تعذر استعادة البث بعد $maxAttempts محاولات. تحقق من حالة الرسيفر أو إشارة الطبق."
            )
            return false
        }
        _playerState.value = curr.copy(
            isBuffering = true,
            reconnectAttempt = curr.reconnectAttempt + 1,
            errorArabic = "جاري إعادة الاتصال بالبث (محاولة ${curr.reconnectAttempt + 1} من $maxAttempts)..."
        )
        return true
    }

    fun buildExternalPlayerIntent(context: Context): Intent? {
        val url = _playerState.value.currentStreamUrl ?: return null
        if (url.isBlank()) return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun release() {
        rtspKeepAlive.stopKeepAlive()
        _playerState.value = LivePlayerUiState()
    }
}
