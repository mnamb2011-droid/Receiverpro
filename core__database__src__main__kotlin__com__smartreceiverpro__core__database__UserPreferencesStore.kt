package com.smartreceiverpro.core.database

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "smart_receiver_pro_prefs")

data class AppUserPreferences(
    val languageCode: String = "ar",
    val useDarkTheme: Boolean = true,
    val hapticFeedback: Boolean = true,
    val remoteSoundEffects: Boolean = false,
    val allowExternalLanConsent: Boolean = false,
    val preferredPlayerPackage: String = "internal_media3",
    val motionSensitivity: Float = 1.0f,
    val lastSatLatitude: Double = 30.1449,  // Default Cairo / Al Shorouk
    val lastSatLongitude: Double = 31.6306
)

class UserPreferencesStore(private val context: Context) {
    private object Keys {
        val LANGUAGE = stringPreferencesKey("language_code")
        val DARK_THEME = booleanPreferencesKey("use_dark_theme")
        val HAPTIC = booleanPreferencesKey("haptic_feedback")
        val SOUND = booleanPreferencesKey("remote_sound_effects")
        val EXTERNAL_LAN = booleanPreferencesKey("allow_external_lan_consent")
        val PLAYER_PKG = stringPreferencesKey("preferred_player_package")
        val MOTION_SENS = floatPreferencesKey("motion_sensitivity")
    }

    val preferencesFlow: Flow<AppUserPreferences> = context.dataStore.data.map { prefs ->
        AppUserPreferences(
            languageCode = prefs[Keys.LANGUAGE] ?: "ar",
            useDarkTheme = prefs[Keys.DARK_THEME] ?: true,
            hapticFeedback = prefs[Keys.HAPTIC] ?: true,
            remoteSoundEffects = prefs[Keys.SOUND] ?: false,
            allowExternalLanConsent = prefs[Keys.EXTERNAL_LAN] ?: false,
            preferredPlayerPackage = prefs[Keys.PLAYER_PKG] ?: "internal_media3",
            motionSensitivity = prefs[Keys.MOTION_SENS] ?: 1.0f
        )
    }

    suspend fun setLanguage(languageCode: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = languageCode }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC] = enabled }
    }

    suspend fun setPreferredPlayer(playerPackage: String) {
        context.dataStore.edit { it[Keys.PLAYER_PKG] = playerPackage }
    }
}
