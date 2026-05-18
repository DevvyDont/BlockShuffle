package me.devvy.blockshuffle.ui

import me.devvy.blockshuffle.BlockShuffle
import me.devvy.blockshuffle.gamemode.BlitzMode
import me.devvy.blockshuffle.service.gamemode.PlayerTimerManager
import me.devvy.blockshuffle.util.ItemUtils
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory

class BlitzShopMenu(val player: Player) : Listener {

    private val CLOSE_BUTTON = 49

    private val BIOME_LOCATOR = 20
    private val BIOME_LOCATOR_COST = 300
    private val LOCATOR_RADIUS = 2500

    private val SATIATE_BUTTON = 22
    private val SATIATE_COST = 120

    private val OXIDIZER_BUTTON = 24
    private val OXIDIZER_COST = 90


    private val inventory: Inventory = Bukkit.createInventory(player, 54, Component.text("Blitz Shop"))

    init {
        BlockShuffle.getInstance().server.pluginManager.registerEvents(this, BlockShuffle.getInstance())
    }

    fun open() {
        render()
        player.openInventory(inventory)
    }

    fun render() {

        for (i in 0 until inventory.size)
            inventory.setItem(i, ItemUtils.withName(Material.BLACK_STAINED_GLASS_PANE, Component.empty()))

        inventory.setItem(CLOSE_BUTTON, ItemUtils.withName(Material.BARRIER, Component.text("Close", NamedTextColor.RED)))
        inventory.setItem(SATIATE_BUTTON, ItemUtils.withNameAndDescription(
            Material.BREAD,
            Component.text("Satiate", NamedTextColor.GOLD),
            Component.empty(),
            Component.text("Refills your hunger and saturation", NamedTextColor.GRAY),
            Component.text("Cost: ", NamedTextColor.GRAY).append(Component.text("-${SATIATE_COST}s", NamedTextColor.RED))
        ))
        inventory.setItem(BIOME_LOCATOR, ItemUtils.withNameAndDescription(
            Material.COMPASS,
            Component.text("Biome Locator", NamedTextColor.GOLD),
            Component.empty(),
            Component.text("Locates a biome in a $LOCATOR_RADIUS block radius", NamedTextColor.GRAY),
            Component.text("Cost: ", NamedTextColor.GRAY).append(Component.text("-${BIOME_LOCATOR_COST}s", NamedTextColor.RED))
        ))
        inventory.setItem(OXIDIZER_BUTTON, ItemUtils.withNameAndDescription(
            Material.CLOCK,
            Component.text("Oxidizer", NamedTextColor.GOLD),
            Component.empty(),
            Component.text("Speeds up the oxidization process of copper", NamedTextColor.GRAY),
            Component.text("Cost: ", NamedTextColor.GRAY).append(Component.text("-${OXIDIZER_COST}s", NamedTextColor.RED))
        ))
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {

        if (event.clickedInventory != inventory)
            return

        event.isCancelled = true

        if (event.whoClicked != player)
            return

        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)

        when (event.slot) {

            CLOSE_BUTTON -> {
                player.closeInventory()
            }

            BIOME_LOCATOR -> {
                player.closeInventory()
                val bld = BiomeLocatorDialog(player, BIOME_LOCATOR_COST, LOCATOR_RADIUS)
                val dialog = bld.create()
                Audience.audience(player).showDialog(dialog)
            }

            SATIATE_BUTTON -> {
                player.closeInventory()
                player.foodLevel = 20
                player.saturation = 20f
                player.damage(SATIATE_COST.toDouble(), BlitzMode.IGNORED_MULTIPLIER_DAMAGE_SOURCE)
                player.world.playSound(player.location, Sound.ENTITY_PLAYER_BURP, 1f, .5f)
                player.sendMessage(Component.text("You suddenly feel full!", NamedTextColor.GREEN))
            }

            OXIDIZER_BUTTON -> {
                player.closeInventory()
                player.sendMessage(Component.text("You were given an oxidizer! Right click a copper block to oxidize it!", NamedTextColor.GREEN))
                player.give(ItemUtils.oxidizer())
                player.damage(OXIDIZER_COST.toDouble(), BlitzMode.IGNORED_MULTIPLIER_DAMAGE_SOURCE)
            }
        }

    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.inventory != inventory)
            return

        HandlerList.unregisterAll(this)
        player.playSound(player.location, Sound.BLOCK_BELL_RESONATE, 1f, 1f)
    }
}