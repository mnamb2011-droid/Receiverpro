package com.smartreceiverpro.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SavedReceiverEntity::class,
        ChannelEntity::class,
        FavoriteGroupEntity::class,
        ChannelFavoriteCrossRef::class,
        RemoteProfileEntity::class,
        ConnectionLogEntity::class,
        CachedEpgEntity::class,
        BackupMetadataEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class SmartReceiverDatabase : RoomDatabase() {
    abstract fun receiverDao(): ReceiverDao
    abstract fun channelDao(): ChannelDao
    abstract fun epgAndBackupDao(): EpgAndBackupDao
}
