package com.smartreceiverpro.feature.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartreceiverpro.core.design.*
import com.smartreceiverpro.engine.api.RemoteKey
import kotlin.math.abs

enum class RemoteControlMode(val titleAr: String) {
    CLASSIC("كلاسيكي"),
    TOUCH("لوحة اللمس"),
    MOTION("تحكم بالحركة")
}

@Composable
fun AdvancedRemoteScreen(
    supportsRemote: Boolean,
    hapticEnabled: Boolean,
    onSendKey: (RemoteKey) -> Unit,
    onCalibrateMotion: () -> Unit,
    onToggleMotionActive: (Boolean, Float) -> Unit
) {
    var selectedMode by remember { mutableStateOf(RemoteControlMode.CLASSIC) }
    var screenTouchLocked by remember { mutableStateOf(false) }
    var motionActive by remember { mutableStateOf(false) }
    var motionSensitivity by remember { mutableFloatStateOf(1.0f) }
    val haptic = LocalHapticFeedback.current

    fun triggerKey(key: RemoteKey) {
        if (screenTouchLocked) return
        if (hapticEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        onSendKey(key)
    }

    // Automatically stop motion sensor if user switches away from MOTION tab
    DisposableEffect(selectedMode, motionActive, motionSensitivity) {
        if (selectedMode == RemoteControlMode.MOTION && motionActive && !screenTouchLocked) {
            onToggleMotionActive(true, motionSensitivity)
        } else {
            onToggleMotionActive(false, motionSensitivity)
        }
        onDispose {
            onToggleMotionActive(false, motionSensitivity)
        }
    }

    if (!supportsRemote) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            UnavailableCapabilityBanner(
                featureTitle = "الريموت الافتراضي",
                reason = "الرسيفر المتصل حالياً لا يدعم استقبال أوامر الريموت عبر بروتوكول الشبكة الحالي."
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
        // Top Row: Mode Switcher + Screen Touch Lock Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabRow(
                selectedTabIndex = selectedMode.ordinal,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
            ) {
                RemoteControlMode.entries.forEach { mode ->
                    Tab(
                        selected = selectedMode == mode,
                        onClick = { if (!screenTouchLocked) selectedMode = mode },
                        text = { Text(mode.titleAr) }
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = screenTouchLocked,
                onClick = { screenTouchLocked = !screenTouchLocked },
                label = { Text(if (screenTouchLocked) "فك القفل" else "قفل اللمس") }
            )
        }

        if (screenTouchLocked) {
            Card(
                colors = CardDefaults.cardColors(containerColor = AmberWarning.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "شاشة الريموت مقفلة لمنع اللمس غير المقصود أثناء المشاهدة. اضغط (فك القفل) أعلاه للمتابعة.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        when (selectedMode) {
            RemoteControlMode.CLASSIC -> ClassicRemoteLayout(onKeyClick = ::triggerKey)
            RemoteControlMode.TOUCH -> TouchpadRemoteLayout(onKeyClick = ::triggerKey)
            RemoteControlMode.MOTION -> MotionRemoteLayout(
                isActive = motionActive,
                sensitivity = motionSensitivity,
                onToggleActive = { motionActive = it },
                onSensitivityChange = { motionSensitivity = it },
                onCalibrate = onCalibrateMotion,
                onKeyClick = ::triggerKey
            )
        }
    }
}

@Composable
private fun ClassicRemoteLayout(onKeyClick: (RemoteKey) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Power, TV/Radio, SAT, Mute
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RemoteKeyButton(RemoteKey.POWER, Modifier.weight(1f), containerColor = CrimsonDanger, onClick = onKeyClick)
                RemoteKeyButton(RemoteKey.TV_RADIO, Modifier.weight(1f), onClick = onKeyClick)
                RemoteKeyButton(RemoteKey.SAT, Modifier.weight(1f), onClick = onKeyClick)
                RemoteKeyButton(RemoteKey.MUTE, Modifier.weight(1f), containerColor = AmberWarning.copy(alpha = 0.8f), onClick = onKeyClick)
            }
        }

        // Menu, EPG, Info, Exit, Back, Recall
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RemoteKeyButton(RemoteKey.MENU, Modifier.weight(1f), onClick = onKeyClick)
                RemoteKeyButton(RemoteKey.EPG, Modifier.weight(1f), onClick = onKeyClick)
                RemoteKeyButton(RemoteKey.INFO, Modifier.weight(1f), onClick = onKeyClick)
                RemoteKeyButton(RemoteKey.RECALL, Modifier.weight(1f), onClick = onKeyClick)
            }
        }

        // D-Pad + OK + Vol/CH Rockers
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Volume Rocker
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(84.dp)) {
                    RemoteKeyButton(RemoteKey.VOL_UP, Modifier.fillMaxWidth(), onClick = onKeyClick)
                    RemoteKeyButton(RemoteKey.VOL_DOWN, Modifier.fillMaxWidth(), onClick = onKeyClick)
                    RemoteKeyButton(RemoteKey.BACK, Modifier.fillMaxWidth(), onClick = onKeyClick)
                }

                // Center Navigation Pad
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RemoteKeyButton(RemoteKey.UP, Modifier.width(88.dp), onClick = onKeyClick)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RemoteKeyButton(RemoteKey.RIGHT, Modifier.width(74.dp), onClick = onKeyClick)
                        Button(
                            onClick = { onKeyClick(RemoteKey.OK) },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Text("OK", fontWeight = FontWeight.Bold)
                        }
                        RemoteKeyButton(RemoteKey.LEFT, Modifier.width(74.dp), onClick = onKeyClick)
                    }
                    RemoteKeyButton(RemoteKey.DOWN, Modifier.width(88.dp), onClick = onKeyClick)
                }

                // Channel & Page Rocker
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(84.dp)) {
                    RemoteKeyButton(RemoteKey.CH_UP, Modifier.fillMaxWidth(), onClick = onKeyClick)
                    RemoteKeyButton(RemoteKey.CH_DOWN, Modifier.fillMaxWidth(), onClick = onKeyClick)
                    RemoteKeyButton(RemoteKey.EXIT, Modifier.fillMaxWidth(), onClick = onKeyClick)
                }
            }
        }

        // Color Buttons (Red, Green, Yellow, Blue)
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RemoteKeyButton(RemoteKey.RED, Modifier.weight(1f), containerColor = Color(0xFFD32F2F), onClick = onKeyClick)
                RemoteKeyButton(RemoteKey.GREEN, Modifier.weight(1f), containerColor = Color(0xFF388E3C), onClick = onKeyClick)
                RemoteKeyButton(RemoteKey.YELLOW, Modifier.weight(1f), containerColor = Color(0xFFFBC02D), onClick = onKeyClick)
                RemoteKeyButton(RemoteKey.BLUE, Modifier.weight(1f), containerColor = Color(0xFF1976D2), onClick = onKeyClick)
            }
        }

        // Numeric Keypad 0-9 + Page Up / Down
        item {
            val rows = listOf(
                listOf(RemoteKey.DIGIT_1, RemoteKey.DIGIT_2, RemoteKey.DIGIT_3),
                listOf(RemoteKey.DIGIT_4, RemoteKey.DIGIT_5, RemoteKey.DIGIT_6),
                listOf(RemoteKey.DIGIT_7, RemoteKey.DIGIT_8, RemoteKey.DIGIT_9),
                listOf(RemoteKey.PAGE_UP, RemoteKey.DIGIT_0, RemoteKey.PAGE_DOWN)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in rows) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (digitKey in row) {
                            RemoteKeyButton(digitKey, Modifier.weight(1f), onClick = onKeyClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TouchpadRemoteLayout(onKeyClick: (RemoteKey) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onKeyClick(RemoteKey.OK) },
                        onDoubleTap = { onKeyClick(RemoteKey.BACK) }
                    )
                }
                .pointerInput(Unit) {
                    var totalDx = 0f
                    var totalDy = 0f
                    detectDragGestures(
                        onDragStart = {
                            totalDx = 0f
                            totalDy = 0f
                        },
                        onDragEnd = {
                            if (abs(totalDx) > 60f || abs(totalDy) > 60f) {
                                if (abs(totalDx) > abs(totalDy)) {
                                    onKeyClick(if (totalDx > 0) RemoteKey.RIGHT else RemoteKey.LEFT)
                                } else {
                                    onKeyClick(if (totalDy > 0) RemoteKey.DOWN else RemoteKey.UP)
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDx += dragAmount.x
                            totalDy += dragAmount.y
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("لوحة اللمس الذكية", style = MaterialTheme.typography.titleLarge, color = ElectricBlue)
                Spacer(modifier = Modifier.height(8.dp))
                Text("• نقرة واحدة = موافق (OK)", style = MaterialTheme.typography.bodyMedium)
                Text("• نقر مزدوج = رجوع (Back)", style = MaterialTheme.typography.bodyMedium)
                Text("• السحب في أي اتجاه = تنقل القوائم والقنوات", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteKeyButton(RemoteKey.MENU, Modifier.weight(1f), onClick = onKeyClick)
            RemoteKeyButton(RemoteKey.BACK, Modifier.weight(1f), onClick = onKeyClick)
            RemoteKeyButton(RemoteKey.EXIT, Modifier.weight(1f), onClick = onKeyClick)
        }
    }
}

@Composable
private fun MotionRemoteLayout(
    isActive: Boolean,
    sensitivity: Float,
    onToggleActive: (Boolean) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onCalibrate: () -> Unit,
    onKeyClick: (RemoteKey) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "التحكم بالحركة (Gyroscope & Accelerometer)",
                style = MaterialTheme.typography.titleLarge,
                color = ElectricBlue
            )
            Text(
                text = "حرّك الهاتف للأعلى أو الأسفل أو اليمين أو اليسار للتنقل، مع إيقاف تلقائي للمستشعرات عند مغادرة الصفحة للحفاظ على البطارية.",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("تفعيل مستشعر الحركة", style = MaterialTheme.typography.titleMedium)
                Switch(checked = isActive, onCheckedChange = onToggleActive)
            }
            Text("حساسية الحركة: ${String.format("%.1f", sensitivity)}x")
            Slider(
                value = sensitivity,
                onValueChange = onSensitivityChange,
                valueRange = 0.5f..2.5f
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCalibrate, modifier = Modifier.weight(1f)) {
                    Text("معايرة نقطة الصفر")
                }
                Button(onClick = { onKeyClick(RemoteKey.OK) }, modifier = Modifier.weight(1f)) {
                    Text("موافق (OK)")
                }
            }
        }
    }
}

@Composable
private fun RemoteKeyButton(
    key: RemoteKey,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    onClick: (RemoteKey) -> Unit
) {
    Button(
        onClick = { onClick(key) },
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor)
    ) {
        Text(
            text = key.arabicLabel,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimaryDark
        )
    }
}
