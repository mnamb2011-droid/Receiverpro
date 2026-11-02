package com.smartreceiverpro.feature.voice

import com.smartreceiverpro.engine.api.RemoteKey

sealed interface ParsedVoiceIntent {
    val requiresExplicitConfirmation: Boolean

    data class SendKey(
        val key: RemoteKey,
        override val requiresExplicitConfirmation: Boolean = false
    ) : ParsedVoiceIntent

    data class OpenChannelByName(
        val channelQuery: String,
        override val requiresExplicitConfirmation: Boolean = false
    ) : ParsedVoiceIntent

    data class NavigateToScreen(
        val route: String,
        override val requiresExplicitConfirmation: Boolean = false
    ) : ParsedVoiceIntent

    data class Unrecognized(
        val rawTranscript: String,
        override val requiresExplicitConfirmation: Boolean = false
    ) : ParsedVoiceIntent
}

/**
 * Arabic & English Voice Command Intent Parser (Section 15).
 * Converts natural spoken phrases into unified internal commands and flags any
 * sensitive operation (such as Power Off) with [requiresExplicitConfirmation] = true.
 */
object VoiceCommandParser {

    fun parseTranscript(utterance: String): ParsedVoiceIntent {
        val text = utterance.trim().lowercase()
        if (text.isEmpty()) return ParsedVoiceIntent.Unrecognized(utterance)

        // Sensitive Power Commands -> Require Confirmation
        if (text.contains("اطفئ") || text.contains("أطفئ") || text.contains("قفل الجهاز") ||
            text.contains("power off") || text.contains("turn off")
        ) {
            return ParsedVoiceIntent.SendKey(RemoteKey.POWER, requiresExplicitConfirmation = true)
        }

        // Channel Switching by Name: "افتح قناة MBC" / "شغل قناة ..." / "open channel ..."
        val arabicChPrefixes = listOf("افتح قناة ", "افتح قناه ", "شغل قناة ", "شغل قناه ", "هات قناة ")
        for (prefix in arabicChPrefixes) {
            if (text.startsWith(prefix)) {
                val targetName = utterance.trim().substring(prefix.length).trim()
                if (targetName.isNotEmpty()) {
                    return ParsedVoiceIntent.OpenChannelByName(targetName)
                }
            }
        }
        if (text.startsWith("open channel ") || text.startsWith("watch channel ")) {
            val targetName = utterance.trim().substringAfter("channel ").trim()
            if (targetName.isNotEmpty()) {
                return ParsedVoiceIntent.OpenChannelByName(targetName)
            }
        }

        // Standard Navigation & Volume Commands
        return when {
            text.contains("القناة التالية") || text.contains("القناه التاليه") || text.contains("next channel") ->
                ParsedVoiceIntent.SendKey(RemoteKey.CH_UP)
            text.contains("القناة السابقة") || text.contains("القناه السابقه") || text.contains("previous channel") ->
                ParsedVoiceIntent.SendKey(RemoteKey.CH_DOWN)
            text.contains("ارفع الصوت") || text.contains("علي الصوت") || text.contains("volume up") || text.contains("louder") ->
                ParsedVoiceIntent.SendKey(RemoteKey.VOL_UP)
            text.contains("اخفض الصوت") || text.contains("وطي الصوت") || text.contains("volume down") || text.contains("quieter") ->
                ParsedVoiceIntent.SendKey(RemoteKey.VOL_DOWN)
            text.contains("اكتم الصوت") || text.contains("كتم الصوت") || text.contains("mute") ->
                ParsedVoiceIntent.SendKey(RemoteKey.MUTE)
            text.contains("دليل البرامج") || text.contains("epg") || text.contains("program guide") ->
                ParsedVoiceIntent.NavigateToScreen("epg")
            text.contains("قائمة القنوات") || text.contains("قائمه القنوات") || text.contains("channel list") ->
                ParsedVoiceIntent.NavigateToScreen("channels")
            else -> ParsedVoiceIntent.Unrecognized(utterance)
        }
    }
}
