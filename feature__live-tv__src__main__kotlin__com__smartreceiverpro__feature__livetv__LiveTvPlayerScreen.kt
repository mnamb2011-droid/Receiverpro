
package com.smartreceiverpro.feature.livetv

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.smartreceiverpro.core.design.*
import com.smartreceiverpro.core.media.LivePlayerUiState
import com.smartreceiverpro.core.media.VideoAspectRatioMode
import com.smartreceiverpro.engine.api.ReceiverChannel

@OptIn(UnstableApi::class)
@Composable
fun LiveTvPlayerScreen(
    supportsLiveTv: Boolean,
    playerState: LivePlayerUiState,
    channels: List<ReceiverChannel>,
    currentChannel: ReceiverChannel?,
    onSelectChannel: (ReceiverChannel) -> Unit,
    onChangeAspectRatio: (VideoAspectRatioMode) -> Unit,
    onOpenExternalPlayer: () -> Unit,
    onRetryStream: () -> Unit,
    player: ExoPlayer? = null
) {
    var showSideChannelDrawer by remember {
        mutableStateOf(true)
    }

    val context = LocalContext.current

    if (!supportsLiveTv) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            UnavailableCapabilityBanner(
                featureTitle = "مشغل البث المباشر Live TV",
                reason = "الرسيفر لا يوفر خدمة بث متوافقة مع التطبيق."
            )
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalBlack)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        DarkSurface,
                        RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {

                // سطح عرض الفيديو الحقيقي
                if (player != null) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = false
                                resizeMode =
                                    when (playerState.aspectRatioMode) {
                                        VideoAspectRatioMode.FILL ->
                                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM

                                        else ->
                                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                            }
                        },
                        update = { view ->
                            view.player = player
                            view.resizeMode =
                                when (playerState.aspectRatioMode) {
                                    VideoAspectRatioMode.FILL ->
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM

                                    else ->
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                        }
                    )
                }

                // رسائل الحالة تظهر فوق الفيديو عند الحاجة
                when {
                    !playerState.errorArabic.isNullOrBlank() -> {
    val errorMessage = playerState.errorArabic.orEmpty()
    Column(...) {
        Text(
            text = errorMessage,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .background(DarkSurface.copy(alpha = 0.85f))
                                .padding(16.dp)
                        ) {
                            Text(
                                playerState.errorArabic,
                                color = CrimsonDanger,
                                style = MaterialTheme.typography.titleMedium
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(onClick = onRetryStream) {
                                Text("إعادة المحاولة")
                            }
                        }
                    }

                    playerState.isBuffering -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .background(DarkSurface.copy(alpha = 0.75f))
                                .padding(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = ElectricBlue
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text("جاري تحميل البث...")
                        }
                    }

                    player == null -> {
                        Text(
                            "مشغل الفيديو غير متصل",
                            color = CrimsonDanger
                        )
                    }

                    playerState.currentStreamUrl == null -> {
                        Text(
                            "اختر قناة لبدء المشاهدة",
                            color = TextPrimaryDark
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VideoAspectRatioMode.entries.forEach { mode ->
                        FilterChip(
                            selected =
                                playerState.aspectRatioMode == mode,
                            onClick = {
                                onChangeAspectRatio(mode)
                            },
                            label = { Text(mode.labelAr) }
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenExternalPlayer,
                        enabled = !playerState.currentStreamUrl.isNullOrBlank()
                    ) {
                        Text("مشغل خارجي VLC")
                    }

                    FilledTonalButton(
                        onClick = {
                            showSideChannelDrawer =
                                !showSideChannelDrawer
                        }
                    ) {
                        Text(
                            if (showSideChannelDrawer)
                                "إخفاء القنوات"
                            else
                                "إظهار القنوات"
                        )
                    }
                }
            }

            currentChannel?.let { channel ->
                Text(
                    text = "القناة الحالية: ${channel.serviceName}",
                    color = ElectricBlue
                )
            }
        }

        if (showSideChannelDrawer) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
            ) {
                LazyColumn(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        channels,
                        key = { it.serviceId }
                    ) { channel ->
                        TextButton(
                            onClick = {
                                onSelectChannel(channel)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${channel.channelNumber}. ${channel.serviceName}",
                                color =
                                    if (currentChannel?.serviceId ==
                                        channel.serviceId
                                    ) ElectricBlue
                                    else TextPrimaryDark
                            )
                        }
                    }
                }
            }
        }
    }
}
