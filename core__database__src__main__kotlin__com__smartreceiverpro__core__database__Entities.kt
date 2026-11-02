package com.smartreceiverpro.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "saved_receivers",
    indices = [Index(value = ["ipAddress"], unique = false)]
)
data class SavedReceiverEntity(
    @PrimaryKey val deviceId: String,
    val customName: String,
    val modelName: String,
    val familyPluginId: String,
    val firmwareVersion: String,
    val ipAddress: String,
    val controlPort: Int,
    val streamPort: Int,
    val macAddress: String?,
    val encryptedPassword: String,
    val verifiedCapabilitiesJson: String,
    val isLastConnected: Boolean,
    val lastConnectedEpochMs: Long
)

@Serializable
@Entity(
    tableName = "channels",
    primaryKeys = ["deviceId", "serviceId"],
    indices = [Index(value = ["deviceId", "channelNumber"]), Index(value = ["deviceId", "satelliteName"])]
)
data class ChannelEntity(
    val deviceId: String,
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
    val genre: String,
    val videoPid: Int,
    val audioPid: Int,
    val pmtPid: Int,
    val rawStreamParams: String
)

@Serializable
@Entity(tableName = "favorite_groups")
data class FavoriteGroupEntity(
    @PrimaryKey(autoGenerate = true) val groupId: Long = 0,
    val deviceId: String,
    val groupName: String,
    val sortOrder: Int
)

@Serializable
@Entity(
    tableName = "channel_favorite_cross_ref",
    primaryKeys = ["deviceId", "serviceId", "groupId"]
)
data class ChannelFavoriteCrossRef(
    val deviceId: String,
    val serviceId: String,
    val groupId: Long
)

@Serializable
@Entity(tableName = "remote_button_layouts")
data class RemoteProfileEntity(
    @PrimaryKey val deviceId: String,
    val hapticEnabled: Boolean,
    val soundEnabled: Boolean,
    val motionSensitivity: Float,
    val customShortcutsJson: String
)

@Serializable
@Entity(tableName = "connection_logs")
data class ConnectionLogEntity(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0,
    val deviceId: String,
    val ipAddress: String,
    val stageReached: String,
    val success: Boolean,
    val diagnosticMessage: String,
    val timestampEpochMs: Long
)

@Serializable
@Entity(
    tableName = "cached_epg_events",
    primaryKeys = ["deviceId", "serviceId", "eventId"]
)
data class CachedEpgEntity(
    val deviceId: String,
    val serviceId: String,
    val eventId: String,
    val title: String,
    val description: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val language: String
)

@Serializable
@Entity(tableName = "local_backups")
data class BackupMetadataEntity(
    @PrimaryKey val backupId: String,
    val deviceId: String,
    val deviceModel: String,
    val createdEpochMs: Long,
    val channelCount: Int,
    val filePath: String,
    val checksumSha256: String
)
