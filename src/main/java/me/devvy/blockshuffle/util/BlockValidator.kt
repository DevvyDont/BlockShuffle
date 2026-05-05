package me.devvy.blockshuffle.util

import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockSupport

/**
 * Utility for validating whether a block is suitable for Block Shuffle gameplay.
 * Checks if blocks can be physically placed, stood on, and exist in the world.
 */
object BlockValidator {

    /**
     * Determines if a material is valid for Block Shuffle gameplay.
     * A block is valid if it:
     * - Is a solid block
     * - Can be placed in the world
     * - Can support players standing on it
     * - Is not a liquid, air, or fire
     * - Is not a non-interactable decoration
     */
    fun isValidGameBlock(material: Material): Boolean {

        if (material.isLegacy) return false

        // Exclude non-block materials
        if (!material.isBlock) return false

        // Exclude liquids and gases
        if (material.isOccluding.not()) return false

        // Exclude air and barrier blocks
        if (material == Material.AIR || material == Material.BARRIER || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
            return false
        }

        // Exclude fluids
        if (material == Material.LAVA || material == Material.WATER) {
            return false
        }

        // Exclude blocks that cannot be placed
        if (!canBePlaced(material)) return false

        // Exclude blocks that are too decorative or unsafe
        if (isExcludedDecorative(material)) return false

        return true
    }

    /**
     * Checks if a material can physically be placed by a player.
     */
    private fun canBePlaced(material: Material): Boolean {
        // Portal blocks, end portal frames, etc. cannot be placed
        val nameLower = material.name.lowercase()
        val excludedPatterns = listOf(
            "portal",
            "end_portal",
            "reinforced",
            "structure",
            "jigsaw",
            "command_block",
            "repeating_command_block",
            "chain_command_block",
            "spawner",
            "head",    // Skulls require entity/NBT data
            "infested" // Silverfish blocks
        )

        return !excludedPatterns.any { nameLower.contains(it) }
    }

    /**
     * Checks if a material should be excluded due to being decorative or problematic.
     */
    private fun isExcludedDecorative(material: Material): Boolean {
        val nameLower = material.name.lowercase()
        val excludedPatterns = listOf(
            "candle",           // Too small/decorative
            "glow_lichen",      // Decorative vegetation
            "vine",             // Players would fall through
            "kelp",             // Underwater plant
            "seagrass",         // Underwater plant
            "coral",            // Underwater decoration
            "coral_block",      // Underwater decoration
            "dead_coral",       // Underwater decoration
            "sea_lantern",      // Underwater decoration
            "lamp",             // Decorative lighting
            "lantern",          // Hanging/decorative
            "snow",             // Too thin to stand on reliably
            "attached_melon",   // Special block
            "attached_pumpkin", // Special block
            "mushroom_stem",    // Too fragile
            "nether_wart",      // Crop, too small
            "amethyst_cluster", // Too small
            "conduit",          // Hollow/unsafe
        )

        return excludedPatterns.any { nameLower.contains(it) }
    }

    /**
     * Gets a human-readable version of the material name.
     * Example: "DARK_OAK_PLANKS" -> "Dark Oak Planks"
     */
    fun getMaterialDisplayName(material: Material): String {
        return material.name
            .lowercase()
            .split("_")
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
    }

    /**
     * Checks if a material is full-sized (1 block tall, occupies full space).
     */
    fun isFullBlock(material: Material): Boolean {
        val nameLower = material.name.lowercase()

        // Stairs, slabs, carpets are not full blocks
        val nonFullPatterns = listOf(
            "stairs",
            "slab",
            "carpet",
            "wall",
            "fence",
            "gate",
            "door",
            "trapdoor",
            "leaves"
        )

        return !nonFullPatterns.any { nameLower.contains(it) } && material.isOccluding
    }

    /**
     * Checks if a material is waterloggable.
     */
    fun isWaterloggable(material: Material): Boolean {
        return material.name.contains("slab") ||
               material.name.contains("stairs") ||
               material.name.contains("wall") ||
               material.name.contains("fence") ||
               material.name.contains("gate") ||
               material.name.contains("trapdoor") ||
               material.name.contains("door") ||
               material.name.lowercase().contains("carpet") ||
               material.name.contains("leaves")
    }
}

