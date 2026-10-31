package dev.dhun.presentation.settings

import dev.dhun.core.CountryRegions as SharedCountryRegions

/** Source-compatible UI alias for the shared region model. */
typealias CountryRegion = dev.dhun.core.CountryRegion

/**
 * Settings-facing facade. Networking and UI deliberately share the same code
 * list and normalization so an invalid persisted value cannot be displayed as
 * US while a different raw value is sent as InnerTube `gl`.
 */
object CountryRegions {
    val all: List<CountryRegion> = SharedCountryRegions.all
    val default: CountryRegion = SharedCountryRegions.default

    fun find(code: String?): CountryRegion? = SharedCountryRegions.find(code)
    fun isSupportedCode(code: String): Boolean = SharedCountryRegions.isSupportedCode(code)
    fun normalize(code: String?): String? = SharedCountryRegions.normalize(code)
    fun search(query: String): List<CountryRegion> = SharedCountryRegions.search(query)
}
