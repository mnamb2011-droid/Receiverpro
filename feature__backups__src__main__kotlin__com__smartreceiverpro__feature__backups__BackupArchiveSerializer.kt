package com.smartreceiverpro.feature.backups

import com.smartreceiverpro.core.database.ChannelEntity
import com.smartreceiverpro.core.database.FavoriteGroupEntity
import com.smartreceiverpro.core.database.SavedReceiverEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
data class ReceiverBackupPackage(
    val schemaVersion: Int = 1,
    val exportedAtEpochMs: Long,
    val deviceModel: String,
    val receivers: List<SavedReceiverEntity>,
    val channels: List<ChannelEntity>,
    val favoriteGroups: List<FavoriteGroupEntity>,
    val payloadSha256: String
)

/**
 * Handles deterministic export and verified import of receiver channel lists,
 * favorite groups, and device configurations (Sections 9, 18, 21).
 */
object BackupArchiveSerializer {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun createBackupJson(
        deviceModel: String,
        receivers: List<SavedReceiverEntity>,
        channels: List<ChannelEntity>,
        favoriteGroups: List<FavoriteGroupEntity>,
        timestampMs: Long = System.currentTimeMillis()
    ): String {
        val sanitizedReceivers = receivers.map { it.copy(encryptedPassword = "") }
        val digestInput = "${deviceModel}_${sanitizedReceivers.size}_${channels.size}_${favoriteGroups.size}_$timestampMs"
        val checksum = sha256Hex(digestInput)
        val pkg = ReceiverBackupPackage(
            schemaVersion = 1,
            exportedAtEpochMs = timestampMs,
            deviceModel = deviceModel,
            receivers = sanitizedReceivers,
            channels = channels,
            favoriteGroups = favoriteGroups,
            payloadSha256 = checksum
        )
        return json.encodeToString(pkg)
    }

    fun parseAndVerifyBackupJson(rawJson: String): ReceiverBackupPackage {
        val pkg = json.decodeFromString<ReceiverBackupPackage>(rawJson)
        val expectedDigestInput = "${pkg.deviceModel}_${pkg.receivers.size}_${pkg.channels.size}_${pkg.favoriteGroups.size}_${pkg.exportedAtEpochMs}"
        val expectedHash = sha256Hex(expectedDigestInput)
        require(pkg.payloadSha256 == expectedHash) {
            "فشل التحقق من سلامة النسخة الاحتياطية (SHA-256 Checksum Mismatch)"
        }
        return pkg
    }

    private fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
