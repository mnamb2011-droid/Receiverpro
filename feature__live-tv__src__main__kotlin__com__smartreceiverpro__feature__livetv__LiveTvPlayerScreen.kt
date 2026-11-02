package com.smartreceiverpro.feature.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartreceiverpro.core.design.*
import com.smartreceiverpro.core.media.LivePlayerUiState
import com.smartreceiverpro.core.media.VideoAspectRatioMode
import com.smartreceiverpro.engine.api.ReceiverChannel

@Composable
fun LiveTvPlayerScreen(
    supportsLiveTv: Boolean,
    playerState: LivePlayerUiState,
    channels: List<ReceiverChannel>,
    currentChannel: ReceiverChannel?,
    onSelectChannel: (ReceiverChannel) -> Unit,
    onChangeAspectRatio: (VideoAspectRatioMode) -> Unit,
    onOpenExternalPlayer: () -> Unit,
    onRetryStream: () -> Unit
) {
    var showSideChannelDrawer by remember { mutableStateOf(true) }

    if (!supportsLiveTv) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            UnavailableCapabilityBanner(
                featureTitle = "مشغل البث المباشر Live TV",
                reason = "هذا الرسيفر لا يوفر خدمة بث SAT>IP أو RTSP/HTTP للقنوات على الشبكة المحلية."
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
        // Main Media3 Video Surface + Controls
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(DarkSurface, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    playerState.errorArabic != null -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                            Text(text = playerState.errorArabic ?: "", color = CrimsonDanger, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = onRetryStream) {
                                Text("إعادة المحاولة")
                            }
                        }
                    }
                    playerState.isBuffering -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = ElectricBlue)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("جاري تخزين البث مؤقتاً (Buffering)...")
                        }
                    }
                    playerState.currentStreamUrl != null -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = currentChannel?.serviceName ?: "بث مباشر نشط",
                                style = MaterialTheme.typography.headlineMedium,
                                color = ElectricBlue
                            )
                            Text(
                                text = "Media3 ExoPlayer • النسبة: ${playerState.aspectRatioMode.labelAr}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondaryDark
                            )
                            if (playerState.isScrambledChannel) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "تنبيه: القناة مشفرة وتعتمد على حالة فك التشفير الشرعي داخل الرسيفر.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AmberWarning
                                )
                            }
                        }
                    }
                    else -> {
                        Text("اختر قناة من القائمة لبدء المشاهدة المباشرة")
                    }
                }
            }

            // Player Control Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoAspectRatioMode.entries.forEach { mode ->
                        FilterChip(
                            selected = playerState.aspectRatioMode == mode,
                            onClick = { onChangeAspectRatio(mode) },
                            label = { Text(mode.labelAr) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onOpenExternalPlayer,
                        enabled = !playerState.currentStreamUrl.isNullOrBlank()
                    ) {
                        Text("مشغل خارجي (VLC)")
                    }
                    FilledTonalButton(onClick = { showSideChannelDrawer = !showSideChannelDrawer }) {
                        Text(if (showSideChannelDrawer) "إخفاء القنوات" else "إظهار القنوات")
                    }
                }
            }
        }

        // Side Channel Drawer
        if (showSideChannelDrawer) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.width(240.dp).fillMaxHeight()
            ) {
                LazyColumn(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(channels, key = { it.serviceId }) { ch ->
                        TextButton(
                            onClick = { onSelectChannel(ch) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${ch.channelNumber}. ${ch.serviceName}",
                                color = if (currentChannel?.serviceId == ch.serviceId) ElectricBlue else TextPrimaryDark
                            )
                        }
                    }
                }
            }
        }
    }
}
