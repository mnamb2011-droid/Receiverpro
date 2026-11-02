package com.smartreceiverpro.feature.satfinder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartreceiverpro.core.design.*
import com.smartreceiverpro.engine.api.TunerSignalInfo

/**
 * SatFinder Pro Screen (Section 13):
 * Strictly separates:
 *  1. Geographic Dish Alignment Calculations (Azimuth, Elevation, LNB Skew + Phone Compass)
 *  2. Real Hardware Tuner Signal Quality & Strength from the connected Receiver
 *
 * NEVER displays fake or simulated Signal Quality numbers when the receiver is
 * disconnected or when [supportsSatFinderSignal] is false.
 */
@Composable
fun SatFinderScreen(
    userLat: Double,
    userLon: Double,
    currentHeadingDeg: Float,
    supportsTunerSignal: Boolean,
    realTunerSignal: TunerSignalInfo?,
    onRefreshTunerSignal: () -> Unit
) {
    var selectedSat by remember { mutableStateOf(SatelliteGeometryCalculator.TRUSTED_SATELLITE_CATALOG.first()) }
    val alignment = remember(userLat, userLon, selectedSat) {
        SatelliteGeometryCalculator.calculateAlignment(userLat, userLon, selectedSat)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "ضبط طبق الدش الاحترافي (SatFinder Pro)",
                style = MaterialTheme.typography.headlineMedium,
                color = ElectricBlue
            )
        }

        // Satellite Selector
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. اختيار القمر الصناعي المستهدف", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SatelliteGeometryCalculator.TRUSTED_SATELLITE_CATALOG.take(4).forEach { sat ->
                            FilterChip(
                                selected = selectedSat.id == sat.id,
                                onClick = { selectedSat = sat },
                                label = { Text(sat.nameAr) }
                            )
                        }
                    }
                    Text(
                        text = "التردد المرجعي للضبط: ${selectedSat.referenceTransponder}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EmeraldOnline
                    )
                }
            }
        }

        // Section A: Pure Geographic Dish Alignment Calculations (Independent of STB Tuner)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "القسم أ: الحسابات الجغرافية لاتجاه الطبق (حقيقية حسب الإحداثيات)",
                        style = MaterialTheme.typography.titleMedium,
                        color = ElectricBlue
                    )
                    Text("الموقع الحالي: دائرة عرض $userLat° ، خط طول $userLon°")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AngleStatBox("اتجاه الأفقي Azimuth", "${alignment.azimuthTrueNorthDeg}°")
                        AngleStatBox("زاوية الارتفاع Elevation", "${alignment.elevationDeg}°")
                        AngleStatBox("ميل العدسة LNB Skew", "${alignment.lnbSkewDeg}°")
                    }
                    Text(
                        text = "بوصلة الهاتف الحالية: ${String.format("%.1f", currentHeadingDeg)}° (وجه الطبق نحو ${alignment.azimuthTrueNorthDeg}°)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Section B: Real Tuner Signal from Connected Receiver (Strictly Separated)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "القسم ب: قراءة قوة وجودة الإشارة الفعلية من تونر الرسيفر",
                        style = MaterialTheme.typography.titleMedium,
                        color = ElectricBlue
                    )

                    if (!supportsTunerSignal || realTunerSignal == null) {
                        UnavailableCapabilityBanner(
                            featureTitle = "قراءة إشارة التونر الحية",
                            reason = "لا يوجد اتصال نشط ببيانات التونر من الرسيفر حالياً. التزاماً بالدقة الهندسية، لا يعرض التطبيق أي نسب إشارة وهمية أو تقديرية."
                        )
                    } else {
                        Text(
                            text = if (realTunerSignal.isLocked) "حالة التونر: تم التقاط الإشارة (LOCK ✓)" else "حالة التونر: لا يوجد قفل إشارة (NO LOCK)",
                            color = if (realTunerSignal.isLocked) EmeraldOnline else CrimsonDanger,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text("قوة الإشارة (Strength): ${realTunerSignal.strengthPercent}%")
                        LinearProgressIndicator(
                            progress = { realTunerSignal.strengthPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = ElectricBlue
                        )
                        Text("جودة الإشارة (Quality): ${realTunerSignal.qualityPercent}%")
                        LinearProgressIndicator(
                            progress = { realTunerSignal.qualityPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (realTunerSignal.qualityPercent >= 60) EmeraldOnline else AmberWarning
                        )
                        Button(onClick = onRefreshTunerSignal) {
                            Text("تحديث قراءة التونر الآن")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AngleStatBox(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondaryDark)
        Text(text = value, style = MaterialTheme.typography.headlineMedium, color = TextPrimaryDark)
    }
}
