package me.devvy.blockshuffle.util

import org.bukkit.Material

private const val WAXED = "WAXED"
private const val EXPOSED = "EXPOSED"
private const val WEATHERED = "WEATHERED"
private const val OXIDIZED = "OXIDIZED"

fun performOxidization(material: Material): Material? {

    var query = material.name

    // Annoying edge case, copper block variants don't contain "block"
    if (material == Material.COPPER_BLOCK)
        query = "COPPER"
    else if (material == Material.WAXED_COPPER_BLOCK)
        query = "WAXED_COPPER"

    // First query for a material with "Exposed" inserted at the beginning.
    // If we get a hit, that means we had a valid starting copper block.
    runCatching { Material.valueOf("${EXPOSED}_${query}") }
        .onSuccess { return it }

    // Try to transition from EXPOSED to WEATHERED
    if (query.contains(EXPOSED)) {
        val potentialWeatheredPhase = runCatching {
            Material.valueOf(query.replace(EXPOSED, WEATHERED))
        }
        potentialWeatheredPhase.onSuccess { return it }
    }

    // Try to transition from WEATHERED to OXIDIZED
    if (query.contains(WEATHERED)) {
        val potentialOxidizedPhase = runCatching {
            Material.valueOf(query.replace(WEATHERED, OXIDIZED))
        }
        potentialOxidizedPhase.onSuccess { return it }
    }

    // One more edge case, blocks that go from the first "waxed" state to waxed exposed
    // have the "exposed" prefix as the 2nd adjective after "waxed", meaning we have to catch that.
    if (query.contains(WAXED)) {
        val potentialWaxedExposedPhase = runCatching {
            Material.valueOf(query.replace(WAXED, "${WAXED}_$EXPOSED"))
        }
        potentialWaxedExposedPhase.onSuccess { return it }
    }

    // No valid oxidization phase exists for this material
    return null
}