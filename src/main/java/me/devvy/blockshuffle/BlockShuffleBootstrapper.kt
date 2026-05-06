package me.devvy.blockshuffle

import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import me.devvy.blockshuffle.command.BlockShuffleCommand
import me.devvy.blockshuffle.command.ICommand
import me.devvy.blockshuffle.config.ConfigManager
import me.devvy.blockshuffle.service.BlockManager

@Suppress("unused")
class BlockShuffleBootstrapper : PluginBootstrap {

    override fun bootstrap(context: BootstrapContext) {
        // Get the plugin instance from the bootstrap context
        val commandsToRegister: Array<ICommand> = arrayOf(
            BlockShuffleCommand()
        )

        val manager: LifecycleEventManager<BootstrapContext> = context.lifecycleManager
        manager.registerEventHandler(
            LifecycleEvents.COMMANDS,
            LifecycleEventHandler { event: ReloadableRegistrarEvent<Commands> ->
                val commands = event.registrar()
                for (command in commandsToRegister) {
                    commands.register(command.getRoot())
                }
            })
    }
}