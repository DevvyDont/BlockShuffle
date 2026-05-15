package me.devvy.blockshuffle.util

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemLore
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
    val SHOP = "portable_shop"
    val KEY = NamespacedKey("bs", "item_id")

    fun withName(material: Material, name: Component): ItemStack {
        val item = ItemStack(material)
        item.setData(DataComponentTypes.ITEM_NAME, name)
        return item
    }

    fun withNameAndDescription(material: Material, name: Component, vararg lore: Component): ItemStack {
        val item = ItemStack(material)
        item.setData(DataComponentTypes.ITEM_NAME, name)
        item.setData(DataComponentTypes.LORE, ItemLore.lore().addLines(lore.toList()))
        return item
    }

    fun withEfficiency(material: Material, efficency: Int, name: String? = null): ItemStack {
        val item = ItemStack(material)
        item.addUnsafeEnchantment(Enchantment.EFFICIENCY, efficency)
        if (name != null)
            item.setData(DataComponentTypes.ITEM_NAME, Component.text(name))
        item.setData(DataComponentTypes.RARITY, ItemRarity.EPIC)
        return item
    }

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

    fun shoppingItem(): ItemStack {
        val item = ItemStack(Material.FIREWORK_STAR)
        item.editMeta { ctx ->
            ctx.lore(listOf(
                Component.empty(),
                Component.text("Right click to open the shop!", NamedTextColor.YELLOW),
            ))
        }
        item.setData(DataComponentTypes.RARITY, ItemRarity.EPIC)
        item.setData(DataComponentTypes.ITEM_NAME, Component.text("Port-a-Shop"))
        item.setData(DataComponentTypes.ITEM_MODEL, ItemStack.of(Material.BELL).getData(DataComponentTypes.ITEM_MODEL)!!)
        item.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1)
        tagItemAsCustom(item, SHOP)
        return item
    }

    fun blitzWings(): ItemStack {
        val wings = ItemStack(Material.ELYTRA)
        wings.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1)
        wings.addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)
        wings.setData(DataComponentTypes.UNBREAKABLE)
        wings.setData(DataComponentTypes.RARITY, ItemRarity.EPIC)
        return wings
    }
}