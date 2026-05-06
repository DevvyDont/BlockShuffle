package me.devvy.blockshuffle.command

import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack

/**
 * Interface for Brigadier command implementations.
 * Defines the contract that all plugin commands must implement.
 */
interface ICommand {
    /**
     * Get the root command node for this command.
     * @return The root LiteralCommandNode that defines the complete command structure
     */
    fun getRoot(): LiteralCommandNode<CommandSourceStack>
}