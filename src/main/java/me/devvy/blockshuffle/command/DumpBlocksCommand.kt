package me.devvy.blockshuffle.command

import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.devvy.blockshuffle.BlockShuffle
import me.devvy.blockshuffle.config.ConfigManager
import me.devvy.blockshuffle.service.BlockManager
import me.devvy.blockshuffle.util.BlockValidator
import org.bukkit.Material
import org.bukkit.block.Block
import java.io.File
import kotlin.io.path.Path

class DumpBlocksCommand : ICommand {

    /**
     * Get the root command node for this command.
     * @return The root LiteralCommandNode that defines the complete command structure
     */
    override fun getRoot(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("dumpblocks")
            .executes { context ->
                val sender = context.source.sender

                val valid = mutableListOf<Material>()
                for (b in Material.entries)
                    if (BlockValidator.isValidGameBlock(b))
                        valid.add(b)

                val dataFolder = BlockShuffle.getInstance().dataFolder.absolutePath
                val file = File("$dataFolder/blocks_dump.txt")
                file.writeText("")

                for (m in valid)
                    file.appendText("${m.name}\n")
                sender.sendMessage("Dumping ${valid.size} valid blocks to blocks_dump.txt...")

                val pl = BlockShuffle.getInstance()
                val bm = BlockManager(pl, ConfigManager(pl))

                val registered = bm.getAllValidGameBlocks()
                val leftover = registered.subtract(valid.toSet())
                val leftoversFile = File("$dataFolder/leftovers.txt")
                leftoversFile.writeText("")

                for (m in leftover)
                    leftoversFile.appendText("${m.name}\n")
                sender.sendMessage("Dumping ${leftover.size} leftover blocks to leftovers.txt...")

                1
            }
            .build()
    }
}