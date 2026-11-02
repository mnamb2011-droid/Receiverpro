package com.smartreceiverpro.feature.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.smartreceiverpro.engine.api.ReceiverChannel
import com.smartreceiverpro.engine.capabilities.ReceiverCapabilities

@Composable
fun ChannelManagerScreen(
    channels: List<ReceiverChannel>,
    capabilities: ReceiverCapabilities,
    onSwitchChannel: (ReceiverChannel) -> Unit,
    onWatchLiveOnPhone: (ReceiverChannel) -> Unit,
    onRenameChannel: (ReceiverChannel, String) -> Unit,
    onDeleteSelected: (List<String>, Boolean) -> Unit,
    onToggleLockSelected: (List<String>, Boolean, String) -> Unit,
    onExportChannelsBackup: () -> Unit
) {
    var isRadioTab by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(ChannelSortOption.NUMBER) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (!capabilities.supportsChannelManagement) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            UnavailableCapabilityBanner(
                featureTitle = "إدارة القنوات",
                reason = "الرسيفر المتصل لا يوفر واجهة برمجية لتحميل أو تعديل قائمة القنوات."
            )
        }
        return
    }

    val displayedChannels = remember(channels, isRadioTab, searchQuery, favoritesOnly, sortOption) {
        ChannelCatalogProcessor.filterAndSort(
            channels = channels,
            isRadioTab = isRadioTab,
            searchQuery = searchQuery,
            selectedSatellite = null,
            favoritesOnly = favoritesOnly,
            sortOption = sortOption
        )
    }

    val duplicates = remember(channels) {
        ChannelCatalogProcessor.findDuplicateChannelGroups(channels)
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("تأكيد حذف القنوات المحددة", color = CrimsonDanger) },
            text = { Text("هل أنت متأكد من حذف (${selectedIds.size}) قناة من الرسيفر نهائياً؟ لا يمكن التراجع عن هذه العملية إلا باستعادة نسخة احتياطية.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSelected(selectedIds.toList(), true)
                        selectedIds = emptySet()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger)
                ) {
                    Text("تأكيد الحذف")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // TV / Radio Tabs + Export Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !isRadioTab, onClick = { isRadioTab = false }, label = { Text("قنوات التلفاز") })
                FilterChip(selected = isRadioTab, onClick = { isRadioTab = true }, label = { Text("إذاعات الراديو") })
                FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly }, label = { Text("المفضلة ★") })
            }
            TextButton(onClick = onExportChannelsBackup) {
                Text("تصدير القائمة")
            }
        }

        // Instant Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("ابحث باسم القناة أو رقمها...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Duplicate Warning & Batch Action Bar
        if (duplicates.isNotEmpty()) {
            Text(
                text = "تم اكتشاف ${duplicates.size} مجموعات قنوات مكررة في التردد.",
                style = MaterialTheme.typography.bodyMedium,
                color = AmberWarning
            )
        }

        if (selectedIds.isNotEmpty() && capabilities.supportsChannelDelete) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showDeleteConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger)
                ) {
                    Text("حذف المحدد (${selectedIds.size})")
                }
                OutlinedButton(onClick = { onToggleLockSelected(selectedIds.toList(), true, "0000") }) {
                    Text("قفل أبوي")
                }
                TextButton(onClick = { selectedIds = emptySet() }) {
                    Text("إلغاء التحديد")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(displayedChannels, key = { it.serviceId }) { ch ->
                val isSelected = ch.serviceId in selectedIds
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSwitchChannel(ch) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) selectedIds + ch.serviceId else selectedIds - ch.serviceId
                                }
                            )
                            Column {
                                Text(
                                    text = "${ch.channelNumber}. ${ch.serviceName}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${ch.satelliteName} • ${ch.frequencyMhz} ${ch.polarization} ${ch.symbolRate}" +
                                        (if (ch.isScrambled) " • مشفرة" else " • مفتوحة FTA") +
                                        (if (ch.isLocked) " • مقفلة 🔒" else ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (capabilities.supportsLiveTv) {
                            FilledTonalButton(onClick = { onWatchLiveOnPhone(ch) }) {
                                Text("بث للهاتف")
                            }
                        }
                    }
                }
            }
        }
    }
}
