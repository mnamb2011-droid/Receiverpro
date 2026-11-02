package com.smartreceiverpro.feature.timers

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
import com.smartreceiverpro.engine.api.ReceiverTimerItem

@Composable
fun TimerManagerScreen(
    supportsTimers: Boolean,
    timers: List<ReceiverTimerItem>,
    onDeleteTimer: (String) -> Unit
) {
    if (!supportsTimers) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            UnavailableCapabilityBanner(
                featureTitle = "إدارة المؤقتات",
                reason = "الرسيفر المتصل لا يدعم جدولة المؤقتات عن بُعد."
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "إدارة مؤقتات الرسيفر",
            style = MaterialTheme.typography.headlineMedium,
            color = ElectricBlue
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(timers, key = { it.timerId }) { timer ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = timer.channelName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (timer.isConfirmedByReceiver) "محفوظ ومؤكد في الرسيفر ✓" else "بانتظار تأكيد الرسيفر...",
                                color = if (timer.isConfirmedByReceiver) EmeraldOnline else AmberWarning
                            )
                        }
                        TextButton(onClick = { onDeleteTimer(timer.timerId) }) {
                            Text("حذف", color = CrimsonDanger)
                        }
                    }
                }
            }
        }
    }
}
