package com.smartreceiverpro.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiverDao {
    @Query("SELECT * FROM saved_receivers ORDER BY isLastConnected DESC, lastConnectedEpochMs DESC")
    fun observeReceivers(): Flow<List<SavedReceiverEntity>>

    @Query("SELECT * FROM saved_receivers WHERE isLastConnected = 1 LIMIT 1")
    fun observeLastConnectedReceiver(): Flow<SavedReceiverEntity?>

    @Query("SELECT * FROM saved_receivers WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getReceiverById(deviceId: String): SavedReceiverEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReceiver(receiver: SavedReceiverEntity)

    @Query("UPDATE saved_receivers SET isLastConnected = CASE WHEN deviceId = :deviceId THEN 1 ELSE 0 END")
    suspend fun markAsLastConnected(deviceId: String)

    @Query("UPDATE saved_receivers SET customName = :newName WHERE deviceId = :deviceId")
    suspend fun renameReceiver(deviceId: String, newName: String)

    @Query("DELETE FROM saved_receivers WHERE deviceId = :deviceId")
    suspend fun deleteReceiver(deviceId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnectionLog(log: ConnectionLogEntity)

    @Query("SELECT * FROM connection_logs WHERE deviceId = :deviceId ORDER BY timestampEpochMs DESC LIMIT 50")
    fun observeConnectionLogs(deviceId: String): Flow<List<ConnectionLogEntity>>
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE deviceId = :deviceId AND isRadio = :isRadio ORDER BY channelNumber ASC")
    fun observeChannels(deviceId: String, isRadio: Boolean): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE deviceId = :deviceId ORDER BY channelNumber ASC")
    suspend fun getAllChannelsForDevice(deviceId: String): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannels(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE deviceId = :deviceId")
    suspend fun clearChannelsForDevice(deviceId: String)

    @Transaction
    suspend fun replaceDeviceChannels(deviceId: String, channels: List<ChannelEntity>) {
        clearChannelsForDevice(deviceId)
        upsertChannels(channels)
    }

    @Query("DELETE FROM channels WHERE deviceId = :deviceId AND serviceId IN (:serviceIds)")
    suspend fun deleteChannelsByIds(deviceId: String, serviceIds: List<String>)

    @Query("UPDATE channels SET serviceName = :newName WHERE deviceId = :deviceId AND serviceId = :serviceId")
    suspend fun updateChannelName(deviceId: String, serviceId: String, newName: String)

    @Query("UPDATE channels SET isLocked = :locked WHERE deviceId = :deviceId AND serviceId IN (:serviceIds)")
    suspend fun updateChannelsLockState(deviceId: String, serviceIds: List<String>, locked: Boolean)

    @Query("UPDATE channels SET isFavorite = :favorite WHERE deviceId = :deviceId AND serviceId IN (:serviceIds)")
    suspend fun updateChannelsFavoriteState(deviceId: String, serviceIds: List<String>, favorite: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavoriteGroup(group: FavoriteGroupEntity): Long

    @Query("SELECT * FROM favorite_groups WHERE deviceId = :deviceId ORDER BY sortOrder ASC")
    fun observeFavoriteGroups(deviceId: String): Flow<List<FavoriteGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addChannelToFavoriteGroup(ref: ChannelFavoriteCrossRef)
}

@Dao
interface EpgAndBackupDao {
    @Query("SELECT * FROM cached_epg_events WHERE deviceId = :deviceId AND serviceId = :serviceId AND endEpochMs >= :nowEpochMs ORDER BY startEpochMs ASC")
    fun observeChannelEpg(deviceId: String, serviceId: String, nowEpochMs: Long): Flow<List<CachedEpgEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpgEvents(events: List<CachedEpgEntity>)

    @Query("DELETE FROM cached_epg_events WHERE endEpochMs < :expiredBeforeEpochMs")
    suspend fun purgeExpiredEpg(expiredBeforeEpochMs: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRemoteProfile(profile: RemoteProfileEntity)

    @Query("SELECT * FROM remote_button_layouts WHERE deviceId = :deviceId LIMIT 1")
    fun observeRemoteProfile(deviceId: String): Flow<RemoteProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupMetadata(backup: BackupMetadataEntity)

    @Query("SELECT * FROM local_backups ORDER BY createdEpochMs DESC")
    fun observeBackups(): Flow<List<BackupMetadataEntity>>
}
