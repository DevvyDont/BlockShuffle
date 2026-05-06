package me.devvy.blockshuffle.util

import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockSupport

/**
 * Utility for validating whether a block is suitable for Block Shuffle gameplay.
 * Checks if blocks can be physically placed, stood on, and exist in the world.
 */
object BlockValidator {

    // Global cache of blocks we consider to be invalidated. Used to speed up UI rendering.
    val BLACKLIST_CACHE = mutableSetOf<Material>()
    var cacheBuilt = false

    init {
        // Pre-populate blacklist cache with known invalid blocks to speed up checks
        for (material in Material.entries) {
            if (!isValidGameBlock(material)) {
                BLACKLIST_CACHE.add(material)
            }
        }
        cacheBuilt = true
    }

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

        if (cacheBuilt)
            return !BLACKLIST_CACHE.contains(material)

        if (material.isLegacy) return false

        // Exclude non-block materials
        if (!material.isBlock) return false

        // Exclude manually blacklisted blocks that would cause issues
        when (material) {
            Material.AIR -> return false
            Material.BARRIER -> return false
            Material.CAVE_AIR -> return false
            Material.VOID_AIR -> return false
            else -> {}
        }

        // Exclude blocks that cannot be placed
        if (!canBePlaced(material))
            return false

        // Exclude blocks that are too decorative or unsafe
        if (isExcludedDecorative(material))
            return false

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
            "resin_clump",
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
            "bamboo_sapling",   // Decorative vegetation
            "beetroots",        // Decorative vegetation
            "carrots",          // Decorative vegetation
            "potatoes",         // Decorative vegetation
            "potted",           // Decorative vegetation
            "crop",             // Decorative vegetation
            "bush",             // Decorative vegetation
            "cocoa",            // Decorative vegetation
            "stem",             // Decorative vegetation
            "vine",             // Players would fall through
            "kelp",             // Underwater plant
            "seagrass",         // Underwater plant
            "coral",            // Underwater decoration
            "coral_block",      // Underwater decoration
            "dead_coral",       // Underwater decoration
            "sea_lantern",      // Underwater decoration
            "bubble",           // Underwater decoration
            "lamp",             // Decorative lighting
            "lantern",          // Hanging/decorative
            "banner",           // Hanging/decorative
            "snow",             // Too thin to stand on reliably
            "attached_melon",   // Special block
            "attached_pumpkin", // Special block
            "test",             // Special block
            "light",            // Special block
            "mushroom_stem",    // Too fragile
            "nether_wart",      // Crop, too small
            "amethyst_cluster", // Too small
            "conduit",          // Hollow/unsafe
            "wall_hanging",     // Not an item
            "wall_sign",        // Not an item
            "wall_torch",       // Not an item
            "torch",            // Not an item
            "gateway",          // Not an item
            "fire",             // Not an item
            "frosted_ice",      // Not an item
            "lava",             // Not an item
            "water",            // Not an item
            "moving",           // Not an item
            "wire",             // Not an item
            "wall",             // Not an item
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

