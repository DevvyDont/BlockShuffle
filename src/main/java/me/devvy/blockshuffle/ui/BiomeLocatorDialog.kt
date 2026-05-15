package me.devvy.blockshuffle.ui

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.dialog.DialogResponseView
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import me.devvy.blockshuffle.BlockShuffle
import me.devvy.blockshuffle.gamemode.BlitzMode
import me.devvy.blockshuffle.util.TextUtils
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.Biome
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemRarity
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.CompassMeta

class BiomeLocatorDialog(val player: Player, val cost: Int, val radius: Int) {

    private val biomeKeyMap = mutableMapOf<String, Biome>()

    fun biomes(): List<Biome> {
        val ret = mutableListOf<Biome>()
        for (biome in RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME))
            ret.add(biome)
        ret.sortWith { b1, b2 ->  b1.key().value().compareTo(b2.key().value()) }
        return ret
    }

    fun create(): Dialog {

        biomeKeyMap.clear()
        val inputs = mutableListOf<DialogInput>()
        for (biome in biomes()) {
            val key = biome.key.value()
            biomeKeyMap[key] = biome
            inputs.add(DialogInput.bool(key, Component.text(TextUtils.capitalizeFully(biome.key().value().replace("_", " ")))).build())
        }

        return Dialog.create{builder ->
            builder.empty()
                .base(DialogBase.builder(Component.text("Biome Locator"))
                    .canCloseWithEscape(true)
                    .inputs(inputs)
                    .build())
                .type(DialogType.confirmation(
                    ActionButton.create(
                        Component.text("Confirm"),
                        Component.text("Click to search for your selected biomes!"),
                        100,
                        DialogAction.customClick(this::callback, ClickCallback.Options.builder().uses(1).build())
                    ),
                    ActionButton.create(
                        Component.text("Cancel"),
                        Component.text("Click to close"),
                        100,
                        null
                    )
                ))
        }
    }

    private fun callback(response: DialogResponseView, audience: Audience) {

        val toQuery = mutableListOf<Biome>()
        for (biome in biomeKeyMap.keys)
            if (response.getBoolean(biome) ?: false)
                toQuery.add(biomeKeyMap[biome]!!)

        val result = player.world.locateNearestBiome(player.location, this.radius, *toQuery.toTypedArray())

        if (result == null) {
            player.sendMessage(Component.text("Could not find a biome you were looking for!", NamedTextColor.RED))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 1f)
            return
        }

        val loc = result.location
        loc.y = 300.0
        loc.block.type = Material.LODESTONE
        val compass = ItemStack.of(Material.COMPASS)
        val biomeName = TextUtils.capitalizeFully(result.biome.key().value().replace("_", " "))
        compass.setData(DataComponentTypes.ITEM_NAME, Component.text("Biome Locator ($biomeName)"))
        compass.setData(DataComponentTypes.RARITY, ItemRarity.EPIC)
        val meta = compass.itemMeta as CompassMeta
        meta.isLodestoneTracked = true
        meta.lodestone = loc
        meta.lore(listOf<Component>(
            Component.empty(),
            Component.text("Biome: ", NamedTextColor.GRAY).append(Component.text(biomeName, NamedTextColor.GOLD)),
            Component.text("Coordinates: ", NamedTextColor.GRAY).append(Component.text("${loc.x.toInt()} - ${loc.z.toInt()}", NamedTextColor.GOLD)),
        ))
        compass.itemMeta = meta
        player.give(compass)
        val distance = player.location.distance(result.location)
        player.sendMessage(Component.text("Found a $biomeName biome ${distance.toInt()} blocks away!", NamedTextColor.GREEN))
        player.playSound(player.location, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 1f)

        player.damage(this.cost.toDouble(), BlitzMode.IGNORED_MULTIPLIER_DAMAGE_SOURCE)
    }

}