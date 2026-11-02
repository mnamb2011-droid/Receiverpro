package com.smartreceiverpro.feature.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartreceiverpro.core.discovery.ThreeStageVerificationState
import com.smartreceiverpro.core.discovery.VerificationStage
import com.smartreceiverpro.core.design.AmberWarning
import com.smartreceiverpro.core.design.CrimsonDanger
import com.smartreceiverpro.core.design.ElectricBlue
import com.smartreceiverpro.core.design.EmeraldOnline
import com.smartreceiverpro.engine.api.DiscoveredDeviceCandidate

@Composable
fun DeviceDiscoveryScreen(
    discoveredCandidates: List<DiscoveredDeviceCandidate>,
    verificationState: ThreeStageVerificationState,
    isScanning: Boolean,
    onStartAutoScan: () -> Unit,
    onConnectByIp: (ip: String, port: Int, password: String?) -> Unit,
    onBack: () -> Unit
) {
    var manualIp by remember { mutableStateOf("192.168.1.") }
    var manualPort by remember { mutableStateOf("20000") }
    var manualPassword by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "اكتشاف الرسيفرات وفحص التوافق",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ElectricBlue
                )
                TextButton(onClick = onBack) {
                    Text("رجوع")
                }
            }
        }

        // Manual IP Connection & 3-Stage Verification Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "إدخال عنوان IP يدوياً (Senator Captain / G-MScreen / UPnP)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        label = { Text("عنوان IP المحلي (مثال: 192.168.1.50)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = manualPort,
                            onValueChange = { manualPort = it.filter(Char::isDigit) },
                            label = { Text("المنفذ (20000 افتراضياً)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = manualPassword,
                            onValueChange = { manualPassword = it },
                            label = { Text("كلمة المرور (اختياري)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = {
                            val portNum = manualPort.toIntOrNull() ?: 20000
                            onConnectByIp(manualIp.trim(), portNum, manualPassword.takeIf { it.isNotBlank() })
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("بدء الفحص ثلاثي المراحل والاتصال")
                    }
                }
            }
        }

        // 3-Stage Pipeline Progress Card
        if (verificationState.currentStage != VerificationStage.IDLE) {
            item {
                ThreeStagePipelineCard(verificationState)
            }
        }

        // Auto Discovery Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "الأجهزة المكتشفة تلقائياً في الشبكة (SSDP / UDP / mDNS)",
                    style = MaterialTheme.typography.titleMedium
                )
                FilledTonalButton(onClick = onStartAutoScan, enabled = !isScanning) {
                    Text(if (isScanning) "جاري البحث..." else "تحديث البحث")
                }
            }
        }

        if (discoveredCandidates.isEmpty() && !isScanning) {
            item {
                Text(
                    text = "لم يتم رصد بث تلقائي بعد. تأكد من اتصال الرسيفر بنفس شبكة Wi-Fi أو أدخل عنوان IP أعلاه.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(discoveredCandidates, key = { "${it.ipAddress}:${it.port}" }) { candidate ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${candidate.ipAddress}:${candidate.port}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "طريقة الاكتشاف: ${candidate.discoveryMethod} • العائلة: ${candidate.detectedFamilyHint}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (candidate.rawBannerOrServerHeader.isNotBlank()) {
                                Text(
                                    text = candidate.rawBannerOrServerHeader,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Button(onClick = { onConnectByIp(candidate.ipAddress, candidate.port, null) }) {
                            Text("تحقق واتصل")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreeStagePipelineCard(state: ThreeStageVerificationState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "نتيجة الفحص ثلاثي المراحل للعنوان ${state.targetIp}",
                style = MaterialTheme.typography.titleMedium,
                color = ElectricBlue
            )
            StageRow(
                title = "1. التحقق من الوصول الشبكي (Network Reachability)",
                passed = state.diagnosticReport?.icmpOrTcpReachable == true
            )
            StageRow(
                title = "2. تحديد البروتوكول (Protocol Identification)",
                passed = state.diagnosticReport?.provenProtocols?.isNotEmpty() == true
            )
            StageRow(
                title = "3. التحقق من قدرات الجهاز (Capability Verification)",
                passed = state.currentStage == VerificationStage.VERIFIED_READY
            )

            state.diagnosticReport?.let { report ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "النسخة المكتشفة: ${report.detectedHardwareVariant}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = report.diagnosticSummaryArabic,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (report.provenProtocols.isNotEmpty()) EmeraldOnline else AmberWarning
                )
            }

            state.failureReasonArabic?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CrimsonDanger
                )
            }
        }
    }
}

@Composable
private fun StageRow(title: String, passed: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = if (passed) "تم التحقق ✓" else "قيد الفحص / غير مكتمل",
            color = if (passed) EmeraldOnline else AmberWarning,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
