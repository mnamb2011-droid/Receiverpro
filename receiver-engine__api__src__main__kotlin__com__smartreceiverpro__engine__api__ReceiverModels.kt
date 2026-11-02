package com.smartreceiverpro.engine.api

import kotlinx.serialization.Serializable

@Serializable
data class DiscoveredDeviceCandidate(
    val ipAddress: String,
    val port: Int,
    val discoveryMethod: DiscoveryTransport,
    val rawBannerOrServerHeader: String,
    val detectedFamilyHint: String,
    val macAddress: String? = null
)

enum class DiscoveryTransport {
    GMSCREEN_UDP_BROADCAST,
    SSDP_UPNP,
    MDNS_NSD,
    TCP_PORT_PROBE,
    MANUAL_IP
}

@Serializable
data class ReceiverDeviceInfo(
    val deviceId: String,
    val modelName: String,
    val chipsetFamily: String,
    val firmwareVersion: String,
    val serialNumber: String,
    val ipAddress: String,
    val macAddress: String?,
    val controlPort: Int,
    val streamPort: Int,
    val currentChannelCount: Int,
    val maxProgramCapacity: Int,
    val isStandby: Boolean,
    val supportedProtocols: List<String>,
    val availableServices: List<String>
)

@Serializable
data class ReceiverChannel(
    val serviceId: String,
    val channelNumber: Int,
    val serviceName: String,
    val isRadio: Boolean,
    val satelliteName: String,
    val frequencyMhz: Int,
    val polarization: String,
    val symbolRate: Int,
    val isScrambled: Boolean,
    val isLocked: Boolean,
    val isFavorite: Boolean,
    val genre: String = "General",
    val videoPid: Int = 0,
    val audioPid: Int = 0,
    val pmtPid: Int = 0,
    val rawStreamQuery: String = ""
)

enum class RemoteKey(val gmScreenKeyCode: Int, val arabicLabel: String, val englishLabel: String) {
    POWER(116, "طاقة", "Power"),
    MUTE(113, "كتم", "Mute"),
    VOL_UP(115, "رفع الصوت", "Volume +"),
    VOL_DOWN(114, "خفض الصوت", "Volume -"),
    CH_UP(402, "قناة +", "CH +"),
    CH_DOWN(403, "قناة -", "CH -"),
    UP(1, "أعلى", "Up"),
    DOWN(2, "أسفل", "Down"),
    LEFT(3, "يسار", "Left"),
    RIGHT(4, "يمين", "Right"),
    OK(28, "موافق", "OK"),
    BACK(158, "رجوع", "Back"),
    MENU(139, "القائمة", "Menu"),
    EXIT(174, "خروج", "Exit"),
    INFO(358, "معلومات", "Info"),
    EPG(365, "دليل البرامج", "EPG"),
    TV_RADIO(377, "تلفاز/راديو", "TV/Radio"),
    SAT(381, "الأقمار", "SAT"),
    RECALL(405, "السابق", "Recall"),
    PAGE_UP(104, "صفحة +", "Page Up"),
    PAGE_DOWN(109, "صفحة -", "Page Down"),
    RED(398, "أحمر", "Red"),
    GREEN(399, "أخضر", "Green"),
    YELLOW(400, "أصفر", "Yellow"),
    BLUE(401, "أزرق", "Blue"),
    DIGIT_0(11, "0", "0"),
    DIGIT_1(2, "1", "1"),
    DIGIT_2(3, "2", "2"),
    DIGIT_3(4, "3", "3"),
    DIGIT_4(5, "4", "4"),
    DIGIT_5(6, "5", "5"),
    DIGIT_6(7, "6", "6"),
    DIGIT_7(8, "7", "7"),
    DIGIT_8(9, "8", "8"),
    DIGIT_9(10, "9", "9")
}

@Serializable
data class EpgProgramItem(
    val eventId: String,
    val serviceId: String,
    val channelName: String,
    val title: String,
    val synopsis: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val language: String = "ar"
)

@Serializable
data class ReceiverTimerItem(
    val timerId: String,
    val serviceId: String,
    val channelName: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val repeatRule: TimerRepeatRule,
    val actionType: TimerActionType,
    val isConfirmedByReceiver: Boolean
)

enum class TimerRepeatRule { ONCE, DAILY, WEEKLY, WEEKDAYS }
enum class TimerActionType { SWITCH_CHANNEL, RECORD_USB, STANDBY }

@Serializable
data class TunerSignalInfo(
    val isLocked: Boolean,
    val strengthPercent: Int,
    val qualityPercent: Int,
    val snrDb: Float?,
    val ber: Float?,
    val satelliteName: String,
    val transponderLabel: String,
    val timestampEpochMs: Long
)

@Serializable
data class ReceiverSystemSettings(
    val sleepTimerMinutes: Int?,
    val parentalControlEnabled: Boolean?,
    val screenLockEnabled: Boolean?,
    val aspectRatioMode: String?,
    val hdmiResolution: String?,
    val digitalAudioOutput: String?,
    val networkDhcpEnabled: Boolean?
)

@Serializable
data class StreamDescriptor(
    val streamUrl: String,
    val protocol: String,
    val isScrambled: Boolean,
    val requiresRtspKeepAlive: Boolean,
    val rtspSessionId: String? = null,
    val availableQualities: List<String> = listOf("Original TS")
)

@Serializable
data class MediaSharingCapabilities(
    val supportsDlnaDmrPush: Boolean,
    val supportsDlnaDmpBrowse: Boolean,
    val supportedMimeTypes: List<String>,
    val avTransportControlUrl: String? = null,
    val renderingControlUrl: String? = null
)
