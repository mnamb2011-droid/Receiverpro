package com.smartreceiverpro.engine.capabilities

import kotlinx.serialization.Serializable

/**
 * Dynamic capability matrix extracted strictly after connecting and querying
 * the target receiver. Never assumed statically before verification.
 */
@Serializable
data class ReceiverCapabilities(
    val supportsRemote: Boolean = false,
    val supportsTouchRemote: Boolean = false,
    val supportsMotionRemote: Boolean = false,
    val supportsChannelManagement: Boolean = false,
    val supportsChannelMove: Boolean = false,
    val supportsChannelRename: Boolean = false,
    val supportsChannelDelete: Boolean = false,
    val supportsChannelLock: Boolean = false,
    val supportsFavoritesEdit: Boolean = false,
    val supportsLiveTv: Boolean = false,
    val supportsRtspSatIpStream: Boolean = false,
    val supportsHttpStream: Boolean = false,
    val supportsEPG: Boolean = false,
    val supportsTimers: Boolean = false,
    val supportsDLNA: Boolean = false,
    val supportsDlnaDmrRender: Boolean = false,
    val supportsDlnaDmpBrowse: Boolean = false,
    val supportsSatFinderSignal: Boolean = false,
    val supportsSleepTimer: Boolean = false,
    val supportsParentalControl: Boolean = false,
    val supportsScreenLock: Boolean = false,
    val supportsAudioDisplaySettings: Boolean = false,
    val supportsFactoryResetCommand: Boolean = false,
    val verifiedProtocolName: String = "UNVERIFIED",
    val unverifiedFeaturesNotes: List<String> = emptyList()
) {
    companion object {
        val NONE = ReceiverCapabilities()
    }
}
