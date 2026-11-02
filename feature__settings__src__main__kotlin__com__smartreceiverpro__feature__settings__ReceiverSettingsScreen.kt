package com.smartreceiverpro.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartreceiverpro.core.design.*
import com.smartreceiverpro.engine.api.ReceiverDeviceInfo
import com.smartreceiverpro.engine.api.ReceiverSystemSettings
import com.smartreceiverpro.engine.capabilities.ReceiverCapabilities

/**
 * Receiver Settings Screen (Section 16):
 * - Displays ONLY options supported by the connected receiver's [ReceiverCapabilities].
 * - Protects Factory Reset with a mandatory 2-step confirmation dialog ([DoubleConfirmDangerDialog]).
 */
@Composable
fun ReceiverSettingsScreen(
    deviceInfo: ReceiverDeviceInfo?,
    capabilities: ReceiverCapabilities,
    currentSettings: ReceiverSystemSettings?,
    onUpdateSleepTimer: (Int) -> Unit,
    onExecuteFactoryResetDoubleConfirmed: () -> Unit
) {
    var factoryResetStep by remember { mutableIntStateOf(0) } // 0 = hidden, 1 = step 1, 2 = step 2

    if (factoryResetStep > 0) {
        DoubleConfirmDangerDialog(
            title = "تحذير خطير: استعادة ضبط المصنع للرسيفر",
            stepOneMessage = "الخطوة 1 من 2: سيؤدي ضبط المصنع إلى مسح جميع القنوات والأقمار والإعدادات من الرسيفر. هل تريد المتابعة للتأكيد النهائي؟",
            stepTwoMessage = "الخطوة 2 من 2 (التأكيد النهائي): هل أنت متأكد تماماً من إرسال أمر Factory Reset إلى الرسيفر الآن؟ لا يمكن التراجع عن هذا الإجراء.",
            currentStep = factoryResetStep,
            onAdvanceStep = { factoryResetStep = 2 },
            onConfirmFinal = {
                factoryResetStep = 0
                onExecuteFactoryResetDoubleConfirmed()
            },
            onDismiss = { factoryResetStep = 0 }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "إعدادات الرسيفر المتصل",
                style = MaterialTheme.typography.headlineMedium,
                color = ElectricBlue
            )
        }

        if (deviceInfo != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("معلومات الجهاز (Device Information)", style = MaterialTheme.typography.titleMedium, color = ElectricBlue)
                        Text("الموديل: ${deviceInfo.modelName}")
                        Text("المعالج: ${deviceInfo.chipsetFamily}")
                        Text("إصدار السوفتوير: ${deviceInfo.firmwareVersion}")
                        Text("عنوان الشبكة: ${deviceInfo.ipAddress}:${deviceInfo.controlPort}")
                        Text("البروتوكول الموثق: ${capabilities.verifiedProtocolName}")
                    }
                }
            }
        }

        if (capabilities.supportsSleepTimer) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("مؤقت النوم (Sleep Timer)", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 30, 60, 120).forEach { mins ->
                                OutlinedButton(onClick = { onUpdateSleepTimer(mins) }) {
                                    Text(if (mins == 0) "إيقاف" else "$mins د")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (capabilities.supportsFactoryResetCommand) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CrimsonDanger.copy(alpha = 0.14f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("منطقة الخطر: استعادة ضبط المصنع", style = MaterialTheme.typography.titleMedium, color = CrimsonDanger)
                        Text("محمي بتأكيدين مزدوجين لمنع المسح غير المقصود للقنوات.")
                        Button(
                            onClick = { factoryResetStep = 1 },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger)
                        ) {
                            Text("بدء إجراءات ضبط المصنع...")
                        }
                    }
                }
            }
        }
    }
}
