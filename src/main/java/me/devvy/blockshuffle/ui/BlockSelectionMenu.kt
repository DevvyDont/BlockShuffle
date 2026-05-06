package me.devvy.blockshuffle.ui

import me.devvy.blockshuffle.service.BlockManager
import me.devvy.blockshuffle.util.BlockValidator
import me.devvy.blockshuffle.util.TextUtils
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minecraft.util.TriState
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.java.JavaPlugin

/**
 * Paginated inventory UI for selecting and toggling blocks.
 * Displays block properties and manages admin block configuration.
 */
class BlockSelectionMenu(
    private val plugin: JavaPlugin,
    private val blockManager: BlockManager,
    private val player: Player
) : Listener {

    private val blocksPerPage = 45
    private var currentPage = 0
    private val allBlocks: List<Material> = blockManager.getAllValidGameBlocks()
    private val inventory: Inventory = Bukkit.createInventory(player, 54, Component.text("Block Manager"))
    private var filter: TriState = TriState.DEFAULT // Filter between default, enabled blocks, disabled blocks
    private var difficultyFilter: Int = 0 // 0 = all, 1-5 = specific difficulty

    // If true, we need to rebuild the blocks to display list. Otherwise we can reuse it.
    private var blockDisplayDirty = true
    private var blocksToShow = getBlocksToDisplay()
    private var blockConfigs = blockManager.getAllBlockConfigs()

    init {
        registerListeners()
        render()
        player.openInventory(inventory)
    }

    private fun getOrUpdateBlocksToDisplay(): List<Material> {
        if (!blockDisplayDirty)
            return allBlocks
        return getBlocksToDisplay()
    }

    private fun getBlocksToDisplay(): List<Material> {
        var blocksToShow: List<Material> = blockManager.getAllValidGameBlocks()
        if (filter == TriState.TRUE)
            blocksToShow = blockManager.getEnabledBlocks()
        else if (filter == TriState.FALSE)
            blocksToShow = blockManager.getDisabledBlocks()

        blockConfigs = blockManager.getAllBlockConfigs()
        if (difficultyFilter > 0) {
            blocksToShow = blocksToShow.filter { blockConfigs[it]?.difficulty == difficultyFilter }
        }

        blockDisplayDirty = false
        return blocksToShow
    }

    private fun render () {

        inventory.clear()

        for (i in inventory.size-9 until inventory.size)
            inventory.setItem(i, ItemStack(Material.BLACK_STAINED_GLASS_PANE))

        val blocksToShow: List<Material> = getOrUpdateBlocksToDisplay()
        inventory.setItem(46, createFilterButton())
        inventory.setItem(47, createDifficultyFilterButton())

        val totalPages = (blocksToShow.size + blocksPerPage - 1) / blocksPerPage
        if (currentPage >= totalPages)
            currentPage = 0

        // Add block items for this page
        val startIndex = currentPage * blocksPerPage
        val endIndex = minOf(startIndex + blocksPerPage, blocksToShow.size)

        var slot = 0
        for (i in startIndex until endIndex) {
            if (slot >= 45) break // Reserve bottom row for navigation
            inventory.setItem(slot, createBlockItem(blocksToShow[i]))
            slot++
        }

        // Add navigation buttons at the bottom (slots 45-53)
        inventory.setItem(45, createNavigationButton("« Previous", Material.ARROW, true))
        inventory.setItem(53, createNavigationButton("Next »", Material.ARROW, false))

        // Info button
        inventory.setItem(49, createInfoButton())
    }

    /**
     * Registers event listeners for this menu instance.
     */
    private fun registerListeners() {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /**
     * Unregisters this menu's event listeners.
     */
    private fun unregisterListeners() {
        // Note: We'll unregister on close
        HandlerList.unregisterAll(this)
    }

    /**
     * Creates an item representing a block with its properties and toggle state.
     */
    private fun createBlockItem(material: Material): ItemStack {
        val cfg = blockConfigs[material]
        val isEnabled = cfg?.enabled ?: false
        val difficulty = cfg?.difficulty ?: 1
        val displayName = BlockValidator.getMaterialDisplayName(material)

        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        meta.displayName(
            Component.text(displayName)
                .color(if (isEnabled) NamedTextColor.GREEN else NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true)
        )

        if (isEnabled)
            meta.setEnchantmentGlintOverride(true)

        val lore = mutableListOf<Component>()

        // Status line
        lore.add(Component.text("Status: ")
            .color(NamedTextColor.GRAY)
            .append(
                Component.text(if (isEnabled) "ENABLED" else "DISABLED")
                    .color(if (isEnabled) NamedTextColor.GREEN else NamedTextColor.RED)
            ))

        // Difficulty line
        lore.add(Component.text("Difficulty: ")
            .color(NamedTextColor.GRAY).append(TextUtils.difficultyStars(difficulty, true)))

        item.amount = difficulty

        // Block properties
        lore.add(Component.empty())
        val properties = mutableListOf<Component>()

        if (BlockValidator.isFullBlock(material)) {
            properties.add(Component.text("- FULL BLOCK", NamedTextColor.YELLOW))
        }

        if (BlockValidator.isWaterloggable(material)) {
            properties.add(Component.text("- WATERLOGGABLE", NamedTextColor.AQUA))
        }

        if (material.isBurnable) {
            properties.add(Component.text("- FLAMMABLE", NamedTextColor.RED))
        }

        if (material.isOccluding) {
            properties.add(Component.text("- OPAQUE", NamedTextColor.DARK_GRAY))
        }

        lore.addAll(properties.map { it })

        // Click instruction
        lore.add(Component.empty())
        lore.add(Component.text("Left click to toggle", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, true))
        lore.add(Component.text("Right click to change difficulty", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, true))

        meta.lore(lore)
        item.itemMeta = meta

        // Store block info in display name via custom model data or NBT if needed
        // For now, we'll identify by item position in the inventory

        return item
    }

    /**
     * Creates a navigation button item.
     */
    private fun createNavigationButton(label: String, material: Material, isPrevious: Boolean): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        meta.displayName(Component.text(label).color(NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true))

        val lore = mutableListOf<Component>()
        lore.add(Component.text(if (isPrevious) "Go to previous page" else "Go to next page")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, true))

        lore.add(Component.empty())
        val totalPages = (getOrUpdateBlocksToDisplay().size + blocksPerPage - 1) / blocksPerPage
        lore.add(Component.text("Page ${currentPage + 1} / $totalPages", NamedTextColor.YELLOW))

        meta.lore(lore)
        item.itemMeta = meta

        return item
    }

    private fun createFilterButton(): ItemStack {
        val item = ItemStack(Material.COMPARATOR)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.text("Filter Blocks", NamedTextColor.GRAY))
        meta.lore(listOf(
            Component.empty(),
            Component.text("> Default (ALL)", if (filter == TriState.DEFAULT) NamedTextColor.GREEN else NamedTextColor.DARK_GRAY),
            Component.text("> Enabled", if (filter == TriState.TRUE) NamedTextColor.GREEN else NamedTextColor.DARK_GRAY),
            Component.text("> Disabled", if (filter == TriState.FALSE) NamedTextColor.GREEN else NamedTextColor.DARK_GRAY),
        ))
        if (filter != TriState.DEFAULT)
            meta.setEnchantmentGlintOverride(true)
        item.itemMeta = meta
        return item
    }

    private fun createDifficultyFilterButton(): ItemStack {
        val item = ItemStack(Material.EXPERIENCE_BOTTLE)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.text("Filter by Difficulty", NamedTextColor.GOLD))
        val lore = mutableListOf<Component>()
        lore.add(Component.empty())
        lore.add(Component.text("> All Difficulties", if (difficultyFilter == 0) NamedTextColor.GREEN else NamedTextColor.DARK_GRAY))
        for (i in 1..5) {
            val stars = "★".repeat(i) + "☆".repeat(5 - i)
            lore.add(Component.text("> Difficulty $i: $stars", if (difficultyFilter == i) NamedTextColor.GREEN else NamedTextColor.DARK_GRAY))
        }
        if (difficultyFilter != 0)
            meta.setEnchantmentGlintOverride(true)
        meta.lore(lore)
        item.itemMeta = meta
        return item
    }

    /**
     * Creates an info button.
     */
    private fun createInfoButton(): ItemStack {
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta ?: return item

        meta.displayName(Component.text("Block Manager Info").color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true))

        val lore = mutableListOf<Component>()
        lore.add(Component.text("Total Blocks: ", NamedTextColor.GRAY)
            .append(Component.text(allBlocks.size.toString(), NamedTextColor.GREEN)))

        val enabledCount = allBlocks.count { blockConfigs[it]?.enabled == true }
        lore.add(Component.text("Enabled: ", NamedTextColor.GRAY)
            .append(Component.text(enabledCount.toString(), NamedTextColor.GREEN)))

        lore.add(Component.empty())
        lore.add(Component.text("Difficulty Distribution:", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true))
        for (diff in 1..5) {
            val count = allBlocks.count { blockConfigs[it]?.difficulty == diff && blockConfigs[it]?.enabled == true }
            val stars = "★".repeat(diff) + "☆".repeat(5 - diff)
            lore.add(Component.text("$stars: $count", NamedTextColor.YELLOW))
        }

        lore.add(Component.empty())
        lore.add(Component.text("Tips:", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true))
        lore.add(Component.text("• Left click blocks to toggle", NamedTextColor.GRAY))
        lore.add(Component.text("• Right click to change difficulty", NamedTextColor.GRAY))
        lore.add(Component.text("• Use filters to narrow down", NamedTextColor.GRAY))
        lore.add(Component.text("• Close to save changes", NamedTextColor.GRAY))

        meta.lore(lore)
        item.itemMeta = meta

        return item
    }

    /**
     * Handles inventory click events for this menu.
     */
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.inventory != event.view.topInventory)
            return
        if ((event.whoClicked as? Player) != player)
            return
        if (event.inventory != inventory)
            return

        event.isCancelled = true

        if (event.action != InventoryAction.PICKUP_ALL && event.action != InventoryAction.PICKUP_HALF)
            return

        val slot = event.rawSlot
        if (slot < 0 || slot >= event.inventory.size)
            return

        val currentItem = event.inventory.getItem(slot) ?: return

        when (slot) {
            45 -> {
                // Previous page button
                if (currentPage > 0) {
                    currentPage--
                    render()
                }
                player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 1.0f, .5f)
            }
            46 -> {
                filter = when (filter) {
                    TriState.DEFAULT -> TriState.TRUE
                    TriState.TRUE -> TriState.FALSE
                    TriState.FALSE -> TriState.DEFAULT
                }
                blockDisplayDirty = true
                currentPage = 0
                render()
                player.playSound(player.location, Sound.BLOCK_LEVER_CLICK, 1.0f, 1.0f)
            }
            47 -> {
                difficultyFilter = (difficultyFilter + 1) % 6 // 0 to 5
                currentPage = 0
                blockDisplayDirty = true
                render()
                player.playSound(player.location, Sound.BLOCK_LEVER_CLICK, 1.0f, 1.0f)
            }
            53 -> {
                // Next page button
                val totalPages = (getOrUpdateBlocksToDisplay().size + blocksPerPage - 1) / blocksPerPage
                if (currentPage + 1 < totalPages) {
                    currentPage++
                    render()
                }
                player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f)
            }
            49 -> {
                // Info button - do nothing, just refresh
                render()
            }
            else -> {
                // Block item clicked
                if (slot < 45) {
                    val clickedItem = inventory.getItem(slot) ?: return
                    val material = clickedItem.type

                    blockDisplayDirty = true
                    if (event.click == ClickType.RIGHT) {
                        // Cycle difficulty
                        val currentDifficulty = blockConfigs[material]?.difficulty ?: 1
                        val newDifficulty = (currentDifficulty % 5) + 1
                        blockManager.setBlockDifficulty(material, newDifficulty)
                        player.sendMessage(
                            Component.text()
                                .append(Component.text("${BlockValidator.getMaterialDisplayName(material)} difficulty: ", NamedTextColor.GOLD))
                                .append(Component.text("★".repeat(newDifficulty) + "☆".repeat(5 - newDifficulty), NamedTextColor.YELLOW))
                                .build()
                        )
                        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
                    } else {
                        // Toggle enabled/disabled
                        val oldState = blockConfigs[material]?.enabled ?: false
                        blockManager.toggleBlock(material)
                        player.sendMessage(
                            Component.text()
                                .append(Component.text("${BlockValidator.getMaterialDisplayName(material)}: ", NamedTextColor.GOLD))
                                .append(
                                    Component.text(
                                        if (!oldState) "ENABLED" else "DISABLED",
                                        if (!oldState) NamedTextColor.GREEN else NamedTextColor.RED
                                    )
                                )
                                .build()
                        )
                        player.playSound(player.location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f)
                    }
                    render() // Refresh to show updated state
                }
            }
        }
    }

    /**
     * Handles inventory close events to unregister listeners.
     */
    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.view.topInventory.holder != null)
            return
        if ((event.player as? Player) != player)
            return
        if (event.inventory != inventory)
            return

        // Unregister this listener instance
        unregisterListeners()
    }

    companion object {
        /**
         * Opens the block selection menu for a player.
         */
        fun openForPlayer(plugin: JavaPlugin, blockManager: BlockManager, player: Player) {
            BlockSelectionMenu(plugin, blockManager, player)
        }
    }
}
