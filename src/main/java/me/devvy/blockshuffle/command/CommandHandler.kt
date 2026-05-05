package me.devvy.blockshuffle.command

import me.devvy.blockshuffle.BlockShuffle
import me.devvy.blockshuffle.service.BlockManager
import me.devvy.blockshuffle.service.WorldManager
import me.devvy.blockshuffle.ui.BlockSelectionMenu
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Handles all command execution for the Block Shuffle plugin.
 */
class CommandHandler(
    private val plugin: BlockShuffle,
    private val blockManager: BlockManager
) : CommandExecutor {

    private val worldManager = WorldManager()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!command.name.equals("blockshuffle", ignoreCase = true)) return false

        return when {
            args.isEmpty() -> handleNoArgs(sender)
            args.size == 1 -> handleSingleArg(sender, args[0])
            args.size == 2 && args[0].equals("start", ignoreCase = true) -> handleStartWithTime(sender, args[1])
            else -> handleInvalidUsage(sender)
        }
    }

    private fun handleNoArgs(sender: CommandSender): Boolean {
        return handleInvalidUsage(sender)
    }

    private fun handleSingleArg(sender: CommandSender, arg: String): Boolean {
        return when {
            arg.equals("start", ignoreCase = true) -> handleStart(sender, false)
            arg.equals("tpstart", ignoreCase = true) -> handleStart(sender, true)
            arg.equals("pause", ignoreCase = true) -> handlePause(sender)
            arg.equals("stop", ignoreCase = true) -> handleStop(sender)
            arg.equals("blocks", ignoreCase = true) -> handleBlocks(sender)
            else -> handleInvalidUsage(sender)
        }
    }

    private fun handleStart(sender: CommandSender, shouldTeleport: Boolean): Boolean {
        if (plugin.isGameRunning()) {
            sender.sendMessage(Component.text("Block Shuffle is already started.", NamedTextColor.GREEN))
            return false
        }

        if (shouldTeleport) {
            val spawn = worldManager.findRandomSpawn()
            if (spawn == null) {
                sender.sendMessage(Component.text("Could not find a safe spawn location.", NamedTextColor.RED))
                return false
            }
            println("[BlockShuffle] Found spawnpoint!")
            spawn.world.spawnLocation = spawn
            worldManager.teleportAllPlayersToSpawn(spawn)
        }

        plugin.start()
        sender.sendMessage(Component.text("Block Shuffle started.", NamedTextColor.GREEN))
        return true
    }

    private fun handleStartWithTime(sender: CommandSender, timeArg: String): Boolean {
        if (plugin.isGameRunning()) {
            sender.sendMessage(Component.text("Block Shuffle is already started.", NamedTextColor.GREEN))
            return false
        }

        return try {
            val time = timeArg.toInt()
            plugin.start() // TODO: support for custom time limit in command
            sender.sendMessage(Component.text("Block Shuffle started ($time).", NamedTextColor.GREEN))
            true
        } catch (_: NumberFormatException) {
            sender.sendMessage(Component.text("$timeArg must be an integer.", NamedTextColor.RED))
            false
        }
    }

    private fun handlePause(sender: CommandSender): Boolean {
        if (!plugin.isGameRunning()) {
            sender.sendMessage(Component.text("No game is currently running.", NamedTextColor.RED))
            return false
        }

        val gameTask = plugin.getGameTask()
        gameTask?.setPaused(!gameTask.isPaused())

        val status = gameTask?.isPaused() ?: return false
        val message = "Block Shuffle is now " + (if (status) "paused." else "unpaused.")
        sender.sendMessage(Component.text(message, NamedTextColor.GREEN))
        return true
    }

    private fun handleStop(sender: CommandSender): Boolean {
        plugin.stop()
        sender.sendMessage(Component.text("Block Shuffle stopped.", NamedTextColor.RED))
        return true
    }

    private fun handleBlocks(sender: CommandSender): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED))
            return false
        }

        if (!sender.isOp) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED))
            return false
        }

        BlockSelectionMenu.openForPlayer(plugin, blockManager, sender)
        sender.sendMessage(Component.text("Opening block selection menu...", NamedTextColor.GREEN))
        return true
    }

    private fun handleInvalidUsage(sender: CommandSender): Boolean {
        sender.sendMessage(Component.text("Invalid usage. Please use:", NamedTextColor.RED))
        sender.sendMessage(Component.text("/blockshuffle start", NamedTextColor.RED))
        sender.sendMessage(Component.text("/blockshuffle tpstart", NamedTextColor.RED))
        sender.sendMessage(Component.text("/blockshuffle stop", NamedTextColor.RED))
        sender.sendMessage(Component.text("/blockshuffle pause", NamedTextColor.RED))
        sender.sendMessage(Component.text("/blockshuffle blocks", NamedTextColor.RED))
        return false
    }
}

