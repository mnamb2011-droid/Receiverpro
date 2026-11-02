package com.smartreceiverpro.feature.satfinder

import kotlin.math.*

data class SatelliteCatalogEntry(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val orbitalLongitudeDeg: Double, // East is positive (+), West is negative (-)
    val referenceTransponder: String
)

data class DishAlignmentAngles(
    val satellite: SatelliteCatalogEntry,
    val userLatitudeDeg: Double,
    val userLongitudeDeg: Double,
    val azimuthTrueNorthDeg: Double,
    val elevationDeg: Double,
    val lnbSkewDeg: Double
)

/**
 * Pure orbital spherical trigonometry calculator for geostationary satellites (Section 13).
 *
 * Strictly separated from hardware tuner signal reading:
 * - Calculates true Azimuth (0°..360°), Elevation (0°..90°), and LNB Skew tilt (-90°..+90°)
 *   using the geostationary orbit radius ratio (R_geo / R_earth ≈ 6.6107).
 */
object SatelliteGeometryCalculator {
    private const val EARTH_RADIUS_TO_GEO_ORBIT_RATIO = 0.15127 // R_earth (6378 km) / R_geo (42164 km)

    val TRUSTED_SATELLITE_CATALOG = listOf(
        SatelliteCatalogEntry("nilesat_7w", "نايل سات 7° غرباً", "Nilesat 201/301 (7.0°W)", -7.0, "11747 V 27500"),
        SatelliteCatalogEntry("badr_26e", "بدر / سهيل سات 26° شرقاً", "Badr 4/7/8 / Es'hail (26.0°E)", 26.0, "11919 H 27500"),
        SatelliteCatalogEntry("hotbird_13e", "الأوروبي هوت بيرد 13° شرقاً", "Hotbird 13F/13G (13.0°E)", 13.0, "10992 V 27500"),
        SatelliteCatalogEntry("astra_19e", "أسترا 19.2° شرقاً", "Astra 1KR/1L/1M/1N (19.2°E)", 19.2, "11494 H 22000"),
        SatelliteCatalogEntry("yahsat_52e", "ياه سات 52.5° شرقاً", "Al Yah 1 (52.5°E)", 52.5, "11977 H 27500"),
        SatelliteCatalogEntry("amos_4w", "عاموس 4° غرباً", "Amos 3/7 (4.0°W)", -4.0, "10722 H 27500"),
        SatelliteCatalogEntry("turksat_42e", "ترك سات 42° شرقاً", "Türksat 3A/4A/5B (42.0°E)", 42.0, "11096 V 30000"),
        SatelliteCatalogEntry("eutelsat_16e", "يوتلسات 16° شرقاً", "Eutelsat 16A (16.0°E)", 16.0, "11554 V 30000")
    )

    fun calculateAlignment(
        userLatitudeDeg: Double,
        userLongitudeDeg: Double,
        satellite: SatelliteCatalogEntry
    ): DishAlignmentAngles {
        val latRad = Math.toRadians(userLatitudeDeg)
        // deltaL = userLongitude - satLongitude
        val deltaLonRad = Math.toRadians(userLongitudeDeg - satellite.orbitalLongitudeDeg)

        // Azimuth calculation
        val azimuthRad = PI + atan2(tan(deltaLonRad), sin(latRad))
        val azimuthDeg = ((Math.toDegrees(azimuthRad) % 360.0) + 360.0) % 360.0

        // Elevation calculation
        val cosBeta = cos(latRad) * cos(deltaLonRad)
        val sinBeta = sqrt((1.0 - cosBeta * cosBeta).coerceAtLeast(1e-12))
        val elevationRad = atan2(cosBeta - EARTH_RADIUS_TO_GEO_ORBIT_RATIO, sinBeta)
        val elevationDeg = Math.toDegrees(elevationRad)

        // LNB Skew (Polarization Tilt) calculation
        val skewRad = -atan2(sin(deltaLonRad), tan(latRad))
        val lnbSkewDeg = Math.toDegrees(skewRad)

        return DishAlignmentAngles(
            satellite = satellite,
            userLatitudeDeg = userLatitudeDeg,
            userLongitudeDeg = userLongitudeDeg,
            azimuthTrueNorthDeg = roundToOneDecimal(azimuthDeg),
            elevationDeg = roundToOneDecimal(elevationDeg),
            lnbSkewDeg = roundToOneDecimal(lnbSkewDeg)
        )
    }

    private fun roundToOneDecimal(value: Double): Double = round(value * 10.0) / 10.0
}
