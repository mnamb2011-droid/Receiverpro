package com.smartreceiverpro.feature.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartreceiverpro.core.design.*
import com.smartreceiverpro.engine.api.EpgProgramItem

@Composable
fun EpgScreen(
    supportsEpg: Boolean,
    programs: List<EpgProgramItem>,
    nowEpochMs: Long = System.currentTimeMillis(),
    onScheduleReminder: (EpgProgramItem) -> Unit,
    onSwitchToChannel: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    if (!supportsEpg) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            UnavailableCapabilityBanner(
                featureTitle = "دليل البرامج الإلكتروني EPG",
                reason = "الرسيفر المتصل لا يرسل جداول EPG عبر البروتوكول الحالي."
            )
        }
        return
    }

    val filtered = remember(programs, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) programs
        else programs.filter { it.title.lowercase().contains(q) || it.synopsis.lowercase().contains(q) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "دليل البرامج الإلكتروني (EPG - الآن والتالي)",
            style = MaterialTheme.typography.headlineMedium,
            color = ElectricBlue
        )
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("ابحث عن برنامج أو فيلم...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.eventId }) { prog ->
                val duration = (prog.endEpochMs - prog.startEpochMs).coerceAtLeast(1L)
                val progress = ((nowEpochMs - prog.startEpochMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                val isCurrent = nowEpochMs in prog.startEpochMs..prog.endEpochMs

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = prog.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (isCurrent) "يُعرض الآن" else "قادم",
                                color = if (isCurrent) EmeraldOnline else ElectricBlue
                            )
                        }
                        if (prog.synopsis.isNotBlank()) {
                            Text(
                                text = prog.synopsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isCurrent) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = ElectricBlue
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { onSwitchToChannel(prog.serviceId) }) {
                                Text("الانتقال للقناة")
                            }
                            OutlinedButton(onClick = { onScheduleReminder(prog) }) {
                                Text("تذكير قبل البدء")
                            }
                        }
                    }
                }
            }
        }
    }
}
