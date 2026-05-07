package me.devvy.blockshuffle.command

import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.devvy.blockshuffle.BlockShuffle
import me.devvy.blockshuffle.service.WorldManager
import me.devvy.blockshuffle.ui.BlockSelectionMenu
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player

/**
 * Brigadier command implementation for Block Shuffle.
 * Handles all game commands with proper type-safe argument validation and autocomplete.
 */
class BlockShuffleCommand(
) : ICommand {

    private val worldManager = WorldManager()

    private val plugin: BlockShuffle
        get() = BlockShuffle.getInstance()

    /**
     * Get the root of the command builder.
     * Returns the complete command tree for /blockshuffle with all subcommands.
     */
    override fun getRoot(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("blockshuffle")
            .then(blitzCommand())
            .then(classicCommand())
            .then(startCommand())
            .then(tpstartCommand())
            .then(pauseCommand())
            .then(stopCommand())
            .then(blocksCommand())
            .executes { context ->
                sendUsage(context.source)
                1
            }
            .build()
    }

    /**
     * `/blockshuffle blitz` - Next game will be blitz
     */
    private fun blitzCommand() = Commands.literal("blitz")
        .executes { context ->
            val sender = context.source.sender
            plugin.doBlitzMode = true
            sender.sendMessage(Component.text("Next game will use the blitz gamemode!", NamedTextColor.GREEN))
            1
        }

    /**
     * `/blockshuffle classic` - Next game will be classic
     */
    private fun classicCommand() = Commands.literal("classic")
        .executes { context ->
            val sender = context.source.sender
            plugin.doBlitzMode = false
            sender.sendMessage(Component.text("Next game will use the classic gamemode!", NamedTextColor.GREEN))
            1
        }

    /**
     * `/blockshuffle start` - Starts a new game without teleporting players.
     */
    private fun startCommand() = Commands.literal("start")
        .executes { context ->
            val sender = context.source.sender
            if (plugin.isGameRunning()) {
                sender.sendMessage(Component.text("Block Shuffle is already started.", NamedTextColor.RED))
                return@executes 0
            }

            plugin.start()
            sender.sendMessage(Component.text("Block Shuffle started.", NamedTextColor.GREEN))
            1
        }

    /**
     * `/blockshuffle tpstart` - Starts a new game and teleports players to a random spawn location.
     */
    private fun tpstartCommand() = Commands.literal("tpstart")
        .executes { context ->
            val sender = context.source.sender
            if (plugin.isGameRunning()) {
                sender.sendMessage(Component.text("Block Shuffle is already started.", NamedTextColor.RED))
                return@executes 0
            }

            val spawn = worldManager.findRandomSpawn()
            if (spawn == null) {
                sender.sendMessage(Component.text("Could not find a safe spawn location.", NamedTextColor.RED))
                return@executes 0
            }

            println("[BlockShuffle] Found spawnpoint!")
            spawn.world.spawnLocation = spawn
            worldManager.teleportAllPlayersToSpawn(spawn)

            plugin.start()
            sender.sendMessage(Component.text("Block Shuffle started.", NamedTextColor.GREEN))
            1
        }

    /**
     * `/blockshuffle pause` - Pauses or unpauses the current game.
     */
    private fun pauseCommand() = Commands.literal("pause")
        .executes { context ->
            val sender = context.source.sender
            if (!plugin.isGameRunning()) {
                sender.sendMessage(Component.text("No game is currently running.", NamedTextColor.RED))
                return@executes 0
            }

            val gameTask = plugin.getGameTask()
            gameTask?.setPaused(!gameTask.isPaused())

            val status = gameTask?.isPaused() ?: return@executes 0
            val message = "Block Shuffle is now " + (if (status) "paused." else "unpaused.")
            sender.sendMessage(Component.text(message, NamedTextColor.GREEN))
            1
        }

    /**
     * `/blockshuffle stop` - Stops the current game.
     */
    private fun stopCommand() = Commands.literal("stop")
        .executes { context ->
            plugin.stop()
            context.source.sender.sendMessage(Component.text("Block Shuffle stopped.", NamedTextColor.RED))
            1
        }

    /**
     * `/blockshuffle blocks` - Opens the block selection menu (player command only).
     */
    private fun blocksCommand() = Commands.literal("blocks")
        .requires { stack -> stack.sender is Player && (stack.sender as Player).isOp }
        .executes { context ->
            val sender = context.source.sender
            if (sender !is Player) {
                sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED))
                return@executes 0
            }
            BlockSelectionMenu.openForPlayer(plugin, sender)
            sender.sendMessage(Component.text("Opening block selection menu...", NamedTextColor.GREEN))
            1
        }

    /**
     * Sends the command usage information to the command source.
     */
    private fun sendUsage(source: CommandSourceStack) {
        val sender = source.sender
        sender.sendMessage(Component.text("Block Shuffle Commands:", NamedTextColor.RED))
        sender.sendMessage(Component.text("/blockshuffle start", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("/blockshuffle tpstart", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("/blockshuffle pause", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("/blockshuffle stop", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("/blockshuffle blocks", NamedTextColor.YELLOW))
    }
}