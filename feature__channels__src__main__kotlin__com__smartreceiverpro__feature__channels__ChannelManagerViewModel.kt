package com.smartreceiverpro.feature.channels

import com.smartreceiverpro.engine.api.ReceiverChannel

enum class ChannelSortOption(val labelAr: String) {
    NUMBER("حسب الرقم"),
    NAME_AZ("أبجدياً"),
    FREQUENCY("حسب التردد"),
    SATELLITE("حسب القمر")
}

/**
 * Pure domain logic for Channel Manager (Section 9):
 * - Instant search, sorting, satellite/genre filtering, duplicate detection,
 *   and batch selection.
 */
object ChannelCatalogProcessor {

    fun filterAndSort(
        channels: List<ReceiverChannel>,
        isRadioTab: Boolean,
        searchQuery: String,
        selectedSatellite: String?,
        favoritesOnly: Boolean,
        sortOption: ChannelSortOption
    ): List<ReceiverChannel> {
        val q = searchQuery.trim().lowercase()
        val filtered = channels.filter { ch ->
            ch.isRadio == isRadioTab &&
                (selectedSatellite == null || ch.satelliteName.equals(selectedSatellite, ignoreCase = true)) &&
                (!favoritesOnly || ch.isFavorite) &&
                (q.isEmpty() || ch.serviceName.lowercase().contains(q) || ch.channelNumber.toString() == q)
        }
        return when (sortOption) {
            ChannelSortOption.NUMBER -> filtered.sortedBy { it.channelNumber }
            ChannelSortOption.NAME_AZ -> filtered.sortedBy { it.serviceName.lowercase() }
            ChannelSortOption.FREQUENCY -> filtered.sortedWith(compareBy({ it.frequencyMhz }, { it.channelNumber }))
            ChannelSortOption.SATELLITE -> filtered.sortedWith(compareBy({ it.satelliteName }, { it.channelNumber }))
        }
    }

    /**
     * Detects duplicate channels that share the same normalized name and transponder frequency.
     */
    fun findDuplicateChannelGroups(channels: List<ReceiverChannel>): Map<String, List<ReceiverChannel>> {
        return channels
            .groupBy { "${it.serviceName.trim().lowercase()}_${it.frequencyMhz}_${it.polarization.uppercase()}" }
            .filterValues { it.size > 1 }
    }
}
