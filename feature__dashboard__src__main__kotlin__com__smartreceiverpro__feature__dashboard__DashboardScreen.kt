package com.smartreceiverpro.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartreceiverpro.core.database.SavedReceiverEntity
import com.smartreceiverpro.core.design.*
import com.smartreceiverpro.engine.connection.ActiveReceiverSession

data class QuickShortcutItem(
    val route: String,
    val titleAr: String,
    val subtitleAr: String,
    val icon: ImageVector,
    val isSupported: Boolean
)

@Composable
fun DashboardScreen(
    activeSession: ActiveReceiverSession,
    savedReceivers: List<SavedReceiverEntity>,
    isScanning: Boolean,
    onScanDevicesClick: () -> Unit,
    onAddManualIpClick: () -> Unit,
    onReconnectSavedDevice: (SavedReceiverEntity) -> Unit,
    onNavigateToFeature: (String) -> Unit
) {
    val caps = activeSession.capabilities
    val shortcuts = remember(caps, activeSession.isConnected) {
        listOf(
            QuickShortcutItem("remote", "الريموت الذكي", "كلاسيكي • لمس • حركة", Icons.Default.SettingsRemote, !activeSession.isConnected || caps.supportsRemote),
            QuickShortcutItem("channels", "إدارة القنوات", "فرز • مفضلة • تعديل", Icons.Default.List, !activeSession.isConnected || caps.supportsChannelManagement),
            QuickShortcutItem("live-tv", "البث المباشر", "مشاهدة القنوات على الهاتف", Icons.Default.LiveTv, !activeSession.isConnected || caps.supportsLiveTv),
            QuickShortcutItem("epg", "دليل البرامج EPG", "الآن والتالي • جدول أسبوعي", Icons.Default.Schedule, !activeSession.isConnected || caps.supportsEPG),
            QuickShortcutItem("timers", "إدارة المؤقتات", "تسجيل وتذكير البرامج", Icons.Default.Timer, !activeSession.isConnected || caps.supportsTimers),
            QuickShortcutItem("satfinder", "ضبط الطبق SatFinder", "حساب الزوايا + إشارة التونر", Icons.Default.Explore, true),
            QuickShortcutItem("media-sharing", "مشاركة الوسائط DLNA", "بث الفيديو والصور للرسيفر", Icons.Default.Cast, !activeSession.isConnected || caps.supportsDLNA),
            QuickShortcutItem("voice-control", "التحكم الصوتي", "أوامر صوتية بالعربية والإنجليزية", Icons.Default.Mic, !activeSession.isConnected || caps.supportsRemote),
            QuickShortcutItem("settings", "إعدادات الرسيفر", "النوم • الرقابة • النظام", Icons.Default.Settings, true),
            QuickShortcutItem("backups", "النسخ الاحتياطي", "تصدير واستيراد القنوات والإعدادات", Icons.Default.Backup, true)
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with Logo and Animated Status Badge
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SMART RECEIVER PRO",
                        style = MaterialTheme.typography.headlineMedium,
                        color = ElectricBlue
                    )
                    Text(
                        text = "منصة التحكم الاحترافية بأجهزة الاستقبال الفضائي",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AnimatedConnectionBadge(
                    isConnected = activeSession.isConnected,
                    isConnecting = isScanning,
                    statusLabel = when {
                        activeSession.isConnected && activeSession.isMockTestingSession -> "وضع محاكاة اختباري"
                        activeSession.isConnected -> "متصل فعلياً"
                        isScanning -> "جاري الفحص..."
                        else -> "غير متصل"
                    }
                )
            }
        }

        // Error Banner if Wi-Fi dropped or connection failed
        activeSession.lastErrorArabic?.let { err ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CrimsonDanger.copy(alpha = 0.16f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonDanger)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = err, style = MaterialTheme.typography.bodyMedium, color = TextPrimaryDark)
                    }
                }
            }
        }

        // Connected Receiver Status Card
        item {
            ConnectedReceiverHeroCard(
                activeSession = activeSession,
                onScanDevicesClick = onScanDevicesClick,
                onAddManualIpClick = onAddManualIpClick
            )
        }

        // Quick Feature Grid
        item {
            Text(
                text = "الوظائف الأساسية",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        items(shortcuts.chunked(2)) { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (item in pair) {
                    ShortcutCard(
                        item = item,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToFeature(item.route) }
                    )
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Recent / Saved Receivers Section
        item {
            Text(
                text = "آخر الأجهزة المحفوظة",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (isScanning) {
            item { SkeletonLoadingList(itemCount = 2) }
        } else if (savedReceivers.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "لا توجد أجهزة محفوظة حتى الآن",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "ابدأ بالبحث التلقائي في الشبكة المحلية أو أضف عنوان IP الخاص بجهاز Senator Captain يدوياً.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(savedReceivers, key = { it.deviceId }) { saved ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onReconnectSavedDevice(saved) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = saved.customName.ifBlank { saved.modelName }, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "IP: ${saved.ipAddress}:${saved.controlPort} • إصدار: ${saved.firmwareVersion}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(onClick = { onReconnectSavedDevice(saved) }) {
                            Text("اتصال")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedReceiverHeroCard(
    activeSession: ActiveReceiverSession,
    onScanDevicesClick: () -> Unit,
    onAddManualIpClick: () -> Unit
) {
    val info = activeSession.deviceInfo
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            if (activeSession.isConnected && info != null) {
                Text(
                    text = info.modelName,
                    style = MaterialTheme.typography.titleLarge,
                    color = ElectricBlue
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "عنوان IP: ${info.ipAddress}:${info.controlPort} | المعالج: ${info.chipsetFamily}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "إصدار السوفتوير: ${info.firmwareVersion} | عدد القنوات: ${info.currentChannelCount}",
                    style = MaterialTheme.typography.bodyMedium
                )
                activeSession.currentChannelName?.let { ch ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "القناة الحالية: $ch",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldOnline
                    )
                }
                if (activeSession.capabilities.unverifiedFeaturesNotes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = activeSession.capabilities.unverifiedFeaturesNotes.first(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AmberWarning
                    )
                }
            } else {
                Text(
                    text = "لم يتم الاتصال برسيفر بعد",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "يدعم Senator Captain Q2 4K CA و Senator Captain 4K والأجهزة المتوافقة مع G-MScreen و UPnP.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onScanDevicesClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.WifiFind, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("بحث عن الأجهزة")
                }
                OutlinedButton(
                    onClick = onAddManualIpClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AddLink, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إضافة IP يدوياً")
                }
            }
        }
    }
}

@Composable
private fun ShortcutCard(
    item: QuickShortcutItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (item.isSupported) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.titleAr,
                    tint = if (item.isSupported) ElectricBlue else TextSecondaryDark
                )
                if (!item.isSupported) {
                    Text(
                        text = "غير مدعوم",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AmberWarning
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = item.titleAr,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = item.subtitleAr,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
