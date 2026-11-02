
package com.smartreceiverpro.core.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import com.smartreceiverpro.core.common.StructuredLogger
import com.smartreceiverpro.engine.api.StreamDescriptor
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

class LiveTvPlayerController(context: Context) {

    private val appContext = context.applicationContext

    private var exoPlayer: ExoPlayer? = null

    private val _playerState = MutableStateFlow(LivePlayerUiState())
    val playerState: StateFlow<LivePlayerUiState> =
        _playerState.asStateFlow()

    fun initializePlayer(): ExoPlayer {
        exoPlayer?.let { return it }

        val player = ExoPlayer.Builder(appContext).build()

        player.addListener(object : Player.Listener {

            override fun onPlaybackStateChanged(playbackState: Int) {
                val buffering =
                    playbackState == Player.STATE_BUFFERING

                val playing =
                    playbackState == Player.STATE_READY &&
                    player.isPlaying

                _playerState.value =
                    _playerState.value.copy(
                        isBuffering = buffering,
                        isPlaying = playing
                    )
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.value =
                    _playerState.value.copy(
                        isPlaying = isPlaying
                    )
            }

            override fun onPlayerError(error: PlaybackException) {
                StructuredLogger.warn(
                    "LiveTvPlayer",
                    "Playback error",
                    mapOf("error" to error.message)
                )

                _playerState.value =
                    _playerState.value.copy(
                        isPlaying = false,
                        isBuffering = false,
                        errorArabic =
                            "تعذر تشغيل البث: ${error.message ?: "خطأ غير معروف"}"
                    )
            }
        })

        exoPlayer = player
        return player
    }

    fun prepareStream(descriptor: StreamDescriptor) {

        val url = descriptor.streamUrl

        if (url.isBlank()) {
            _playerState.value =
                _playerState.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorArabic = "رابط البث غير صالح."
                )
            return
        }

        if (!url.startsWith("rtsp://", true)) {
            _playerState.value =
                _playerState.value.copy(
                    isPlaying = false,
                    errorArabic = "نوع رابط البث غير مدعوم في مشغل RTSP."
                )
            return
        }

        try {
            val player = initializePlayer()

            _playerState.value =
                _playerState.value.copy(
                    isPlaying = false,
                    isBuffering = true,
                    currentStreamUrl = url,
                    isScrambledChannel = descriptor.isScrambled,
                    errorArabic = null
                )

            val mediaItem =
                MediaItem.fromUri(Uri.parse(url))

            val mediaSource =
                RtspMediaSource.Factory()
                    .setForceUseRtpTcp(false)
                    .createMediaSource(mediaItem)

            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true

        } catch (e: Exception) {

            StructuredLogger.warn(
                "LiveTvPlayer",
                "Failed to prepare stream",
                mapOf("error" to e.message)
            )

            _playerState.value =
                _playerState.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorArabic =
                        "فشل تجهيز البث: ${e.message}"
                )
        }
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        exoPlayer?.play()
    }

    fun stop() {
        exoPlayer?.stop()

        _playerState.value =
            _playerState.value.copy(
                isPlaying = false,
                isBuffering = false
            )
    }

    fun setVolume(percent: Int) {
        val value = percent.coerceIn(0, 100)

        exoPlayer?.volume = value / 100f

        _playerState.value =
            _playerState.value.copy(
                volumePercent = value,
                isMuted = value == 0
            )
    }

    fun toggleMute() {
        val muted = !_playerState.value.isMuted

        exoPlayer?.volume =
            if (muted) 0f
            else _playerState.value.volumePercent / 100f

        _playerState.value =
            _playerState.value.copy(isMuted = muted)
    }

    fun setAspectRatio(mode: VideoAspectRatioMode) {
        _playerState.value =
            _playerState.value.copy(
                aspectRatioMode = mode
            )
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null

        _playerState.value = LivePlayerUiState()
    }
}
