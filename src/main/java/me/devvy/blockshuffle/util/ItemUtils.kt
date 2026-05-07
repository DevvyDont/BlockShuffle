package me.devvy.blockshuffle.util

import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemRarity
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

object ItemUtils {

    val TEMPORAL_FIREWORK = "temporal_firework"
    val FIREWORK_COST = 5
    val KEY = NamespacedKey("bs", "item_id")

    fun tagItemAsCustom(item: ItemStack, tag: String) {
        item.editMeta { ctx ->
            ctx.persistentDataContainer.set(KEY, PersistentDataType.STRING, tag)
        }
    }

    fun itemIsCustom(item: ItemStack, tag: String): Boolean {
        val meta = item.itemMeta ?:
            return false
        val ctrTag = meta.persistentDataContainer.get(KEY, PersistentDataType.STRING)
        return ctrTag == tag
    }

    fun temporalFireworkItem(): ItemStack {
        val firework = ItemStack.of(Material.FIREWORK_ROCKET)
        firework.editMeta { ctx ->
            ctx.lore(listOf(
                Component.empty(),
                Component.text("Infinite (kinda) use firework!", NamedTextColor.YELLOW),
                Component.text("Cost: ", NamedTextColor.GRAY).append(Component.text("-${FIREWORK_COST}s",
                    NamedTextColor.RED))
            ))
        }
        firework.setData(DataComponentTypes.RARITY, ItemRarity.EPIC)
        firework.setData(DataComponentTypes.ITEM_NAME, Component.text("Temporal Firework"))
        firework.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1)
        tagItemAsCustom(firework, TEMPORAL_FIREWORK)
        return firework
    }

    fun blitzWings(): ItemStack {
        val wings = ItemStack(Material.ELYTRA)
        wings.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1)
        wings.addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)
        wings.setData(DataComponentTypes.UNBREAKABLE)
        return wings
    }
}