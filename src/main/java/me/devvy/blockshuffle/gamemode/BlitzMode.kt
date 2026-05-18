package me.devvy.blockshuffle.gamemode

import com.destroystokyo.paper.ParticleBuilder
import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent
import me.devvy.blockshuffle.BlockShuffle
import me.devvy.blockshuffle.config.GameConfig
import me.devvy.blockshuffle.service.GameMessenger
import me.devvy.blockshuffle.service.WorldManager
import me.devvy.blockshuffle.service.gamemode.PlayerHealthManager
import me.devvy.blockshuffle.service.gamemode.PlayerTimerManager
import me.devvy.blockshuffle.ui.BlitzShopMenu
import me.devvy.blockshuffle.util.ItemUtils
import me.devvy.blockshuffle.util.SimpleGlobalScoreboard
import me.devvy.blockshuffle.util.TextUtils
import me.devvy.blockshuffle.util.TextUtils.append
import me.devvy.blockshuffle.util.performOxidization
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minecraft.util.BlockUtil
import org.bukkit.Bukkit
import org.bukkit.Difficulty
import org.bukkit.GameRules
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Rotatable
import org.bukkit.block.data.type.Chest
import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.CopperGolem
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.DoubleChestInventory
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * Blitz Mode: Fast-paced individual timers with damage-based time loss.
 * Each player has their own countdown timer. Finding blocks adds time, damage removes time.
 * Taking lethal damage or running out of time results in elimination.
 */
class BlitzMode(
    private val plugin: BlockShuffle,
) : GameMode, Listener {

    companion object {
        val IGNORED_MULTIPLIER_DAMAGE_SOURCE = DamageSource.builder(DamageType.OUT_OF_WORLD).build()
    }

    val timerManager = PlayerTimerManager(
        GameConfig.BLITZ_STARTING_TIME_SECONDS,
        GameConfig.BLITZ_TIME_PER_BLOCK_SECONDS
    )
    private val healthManager = PlayerHealthManager(
        this,
        GameConfig.BLITZ_DAMAGE_TO_TIME_RATIO
    )
    private val messenger = GameMessenger()
    private val worldManager = WorldManager()

    private val assignedBlocks: MutableMap<UUID, Pair<Material, Long>> = HashMap()  // UUID -> (Material, assignTime)
    private val eliminatedPlayers: MutableSet<UUID> = HashSet()
    private val playerScores: MutableMap<UUID, Int> = HashMap()

    private var isPaused = false
    private var gameStarted = false
    private var isGameOver = false
    private var ticksElapsed = 0

    var damageMultiplier = 1.0

    // How many times (in seconds) should we up damage multiplier?
    private val damageMultiplierUpdateFrequency = 60
    // How high up should damage multiplier go up?
    private val damageMultiplierUpdateJump = .5

    private var scoreboard: SimpleGlobalScoreboard? = null

    override fun initialize() {
        // Register damage/death listener
        plugin.server.pluginManager.registerEvents(this, plugin)
        healthManager.register()

        if (scoreboard != null)
            scoreboard!!.cleanup()
        scoreboard = SimpleGlobalScoreboard(Bukkit.getScoreboardManager().mainScoreboard, Component.text("Blitz Shuffle", NamedTextColor.LIGHT_PURPLE))

        for (player in Bukkit.getOnlinePlayers()) {
            timerManager.initializePlayer(player)
            playerScores[player.uniqueId] = 0
            // Assign initial block to each player
            assignBlockToPlayer(player)
            scoreboard!!.display(player)
            setupPlayer(player)
        }

        ticksElapsed = 0
        gameStarted = true

        for (world in Bukkit.getWorlds()) {
            world.difficulty = Difficulty.HARD
            world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false)
        }
    }

    private fun setupPlayer(player: Player) {
        player.foodLevel = 20
        player.saturation = 20f
        player.inventory.clear()
        player.give(
            ItemUtils.withEfficiency(Material.GOLDEN_AXE, 5, "Blitz Axe"),
            ItemUtils.withEfficiency(Material.GOLDEN_PICKAXE, 5, "Blitz Pickaxe"),
            ItemUtils.withEfficiency(Material.GOLDEN_SHOVEL, 5, "Blitz Shovel"),
            ItemUtils.blitzWings(),
            ItemUtils.temporalFireworkItem(),
            ItemUtils.shoppingItem()
        )
    }

    private fun updateScoreboard() {

        val lines = mutableListOf<Component>()
        val alive = timerManager.getTrackedPlayers().toMutableList()
        val eliminated = eliminatedPlayers.toMutableList()
        alive.sortBy { p -> playerScores[p] ?: 0 }
        eliminated.sortBy { p -> playerScores[p] ?: 0 }

        lines.add(Component.empty())
        for (id in alive.reversed()) {
            val player = Bukkit.getPlayer(id) ?: continue
            val score = playerScores[id] ?: 0
            lines.add(append(
                Component.text("\uD83C\uDFC6$score ").color(NamedTextColor.YELLOW),
                player.name().color(NamedTextColor.GRAY),
                Component.space(),
                messenger.formatTime(timerManager.getTimeRemainingTicks(player), timerManager.getCurrentTimeDelta(player))
            ))
        }

        for (id in eliminated.reversed()) {
            val player = Bukkit.getPlayer(id) ?: continue
            val score = playerScores[id] ?: 0
            lines.add(append(
                Component.text("\uD83C\uDFC6$score ").color(NamedTextColor.YELLOW),
                player.name().color(NamedTextColor.DARK_GRAY),
                Component.space(),
                Component.text("☠☠☠", NamedTextColor.DARK_RED)
            ))
        }

        lines.add(Component.empty())
        lines.add(append(
            Component.text("Time Elapsed: ", NamedTextColor.GRAY),
            Component.text(TextUtils.formatTimeSimple(ticksElapsed), NamedTextColor.GREEN),
        ))

        // Cancer but essentially purple on updates, red otherwise
        val dmgMultColor = if (ticksElapsed / GameConfig.TASK_FREQUENCY % damageMultiplierUpdateFrequency == 0) NamedTextColor.LIGHT_PURPLE else NamedTextColor.RED
        lines.add(append(
            Component.text("Damage Multiplier: ", NamedTextColor.GRAY),
            Component.text("${damageMultiplier}x", dmgMultColor, TextDecoration.BOLD),
        ))

        scoreboard?.setLines(lines)
    }

    private fun updateDamageMultiplier() {

        // Only execute on exact seconds (not every tick)
        if (ticksElapsed % GameConfig.TASK_FREQUENCY != 0) return

        val secondsElapsed = ticksElapsed / GameConfig.TASK_FREQUENCY
        // Don't trigger at time zero (startup)
        if (secondsElapsed == 0) return
        // Only update on the configured frequency (e.g. every 60s)
        if (secondsElapsed % damageMultiplierUpdateFrequency != 0) return

        val tier = secondsElapsed / damageMultiplierUpdateFrequency
        val newMultiplier = 1.0 + (tier * damageMultiplierUpdateJump)
        // Only apply and notify when the multiplier actually changes (prevents duplicate sounds)
        if (newMultiplier != damageMultiplier) {
            damageMultiplier = newMultiplier
            // Play a short notification sound for all online players once
            for (p in Bukkit.getOnlinePlayers()) {
                p.playSound(p.location, Sound.BLOCK_TRIAL_SPAWNER_OPEN_SHUTTER, 1f, 1f)
            }
        }
    }

    private fun getTopScorers(): Set<UUID> {
        val max = playerScores.values.maxOrNull()
        return playerScores.filterValues { it == max }.keys
    }

    override fun tick() {
        if (!gameStarted) return

        updateDamageMultiplier()
        displayPlayerTimers()
        updateScoreboard()

        if (isPaused) return

        // Tick all player timers, eliminate those who expire
        for (uuid in timerManager.getTrackedPlayers().toList()) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            healthManager.setPlayerHealth(player, GameConfig.BLITZ_STARTING_TIME_SECONDS)
            if (timerManager.tickTimer(player)) {
                eliminatePlayer(player, "TIME_EXPIRED")
            }
        }

        // Check win condition: one player is left and they are top scorer.
        val activePlayers = Bukkit.getOnlinePlayers().filter { !eliminatedPlayers.contains(it.uniqueId) }
        if (activePlayers.size > 1)
            return

        // No players? End the game depending on how many people are in 1st place.
        if (activePlayers.isEmpty()) {
            val top = getTopScorers()
            val winner = Bukkit.getPlayer(top.first())
            if (top.size == 1 && winner != null)
                handleGameOverWinner(winner)
            else
                handleGameOverDraw()
            return
        }

        val alonePlayer = activePlayers.first()
        // One player is left. Are they in the top scorers? If so, end the game only if more players are online.
        val top = getTopScorers()
        if (top.size == 1 && top.contains(alonePlayer.uniqueId) && Bukkit.getOnlinePlayers().size > 1)
            handleGameOverWinner(alonePlayer)

        ticksElapsed++
    }

    override fun onPlayerMove(player: Player, location: Location) {
        if (isPaused || !gameStarted) return
        if (eliminatedPlayers.contains(player.uniqueId)) return

        val uuid = player.uniqueId
        val assignedData = assignedBlocks[uuid] ?: return
        val (assignedBlock, _) = assignedData

        val blockBelow = location.block.getRelative(BlockFace.DOWN).type
        val blockIn = location.block.type

        if (blockBelow == assignedBlock || blockIn == assignedBlock) {
            // Block found!
            messenger.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.0f)
            messenger.showBlockFoundTitle(player)

            // Add time bonus
            val bonus = timerManager.getTimePerBlock() + plugin.blockManager.getBlockDifficulty(assignedBlock) * GameConfig.BLITZ_TIME_BONUS_PER_DIFFICULTY
            timerManager.addTime(player, bonus)

            // Increase score
            playerScores[uuid] = (playerScores[uuid] ?: 0) + 1

            // Create visual/audio feedback
            messenger.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f)

            // Broadcast time bonus
            for (other in Bukkit.getOnlinePlayers()) {
                other.sendMessage(
                    Component.text()
                        .append(Component.text(player.name, NamedTextColor.AQUA))
                        .append(Component.text(" found a block! +$bonus seconds!", NamedTextColor.GREEN))
                        .build()
                )
            }

            // Assign new block immediately
            assignBlockToPlayer(player)
        }
    }

    override fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!gameStarted) return

        val player = event.player

        // Skip if already in game
        if (assignedBlocks.containsKey(player.uniqueId)) return

        // Initialize timer and score
        timerManager.initializePlayer(player)
        playerScores[player.uniqueId] = 0

        // Assign a block
        assignBlockToPlayer(player)
    }

    override fun onPlayerDamage(player: Player, damage: Double) {
        // Damage handling is delegated to PlayerHealthManager
    }

    override fun onPlayerDeath(player: Player) {
        // Death handling is delegated to PlayerHealthManager
        eliminatePlayer(player, "DEATH")
    }

    override fun cleanup() {
        isPaused = true
        gameStarted = false
        isGameOver = true
        HandlerList.unregisterAll(this)
        healthManager.unregister()
        worldManager.resetAllPlayersAfterGame(messenger)
        timerManager.clear()
        scoreboard?.cleanup()
        scoreboard = null
    }

    override fun isPaused(): Boolean {
        return isPaused
    }

    override fun setPaused(paused: Boolean) {
        isPaused = paused
    }

    override fun isGameOver(): Boolean {
        return isGameOver
    }

    /**
     * Displays each player's remaining time in the action bar.
     */
    private fun displayPlayerTimers() {
        val state = if (isPaused) "PAUSED" else "INGAME"
        for (player in Bukkit.getOnlinePlayers()) {
            if (eliminatedPlayers.contains(player.uniqueId))
                continue

            val timeRemaining = timerManager.getTimeRemainingTicks(player)
            val blockMaterial = assignedBlocks[player.uniqueId]?.first

            val score = playerScores[player.uniqueId] ?: 0
            messenger.sendDetailedActionBar(player, state, timeRemaining, score, blockMaterial, timeModifier = timerManager.getCurrentTimeDelta(player))
            // Play warning sounds
            messenger.playTimerWarningSfx(player, timerManager.getTimeRemainingTicks(player))

            // Heartbeat - frequency based on time remaining
            val sec = timeRemaining / GameConfig.TASK_FREQUENCY
            if (sec <= 10) {
                // 10 seconds or lower: every half second
                if (timeRemaining % 10 == 5 || timeRemaining % 10 == 0) {
                    player.playSound(player.location, Sound.ENTITY_WARDEN_HEARTBEAT, 1f, .75f)
                }
            } else if (sec <= 30) {
                // 10-30 seconds: every second
                if (timeRemaining % 10 == 0) {
                    player.playSound(player.location, Sound.ENTITY_WARDEN_HEARTBEAT, 1f, .75f)
                }
            } else if (sec <= 60) {
                // 30-60 seconds: every 2 seconds
                if (timeRemaining % 20 == 0) {
                    player.playSound(player.location, Sound.ENTITY_WARDEN_HEARTBEAT, 1f, .75f)
                }
            }
        }
    }

    /**
     * Assigns a random block to a player (with difficulty progression based on time).
     */
    private fun assignBlockToPlayer(player: Player) {
        // Use round-based difficulty progression (estimate round based on score)
        val round = (playerScores[player.uniqueId] ?: 0)
        val material = plugin.blockManager.getRandomBlockForRound(round)

        assignedBlocks[player.uniqueId] = Pair(material, System.currentTimeMillis())

        messenger.broadcastBlockAssignment(player, material)
        messenger.setPlayerInGame(player, material)
    }

    /**
     * Eliminates a player from the game.
     */
    private fun eliminatePlayer(player: Player, reason: String) {
        if (eliminatedPlayers.contains(player.uniqueId)) return

        eliminatedPlayers.add(player.uniqueId)
        val blockMaterial = assignedBlocks.remove(player.uniqueId)?.first ?: Material.DIRT
        timerManager.removePlayer(player)

        val reasonText = when (reason) {
            "TIME_EXPIRED" -> "Time expired"
            "DEATH" -> "Died"
            "TIME_OUT_FROM_DAMAGE" -> "Took too much damage"
            else -> reason
        }

        for (other in Bukkit.getOnlinePlayers()) {
            other.sendMessage(
                Component.text()
                    .append(Component.text(player.name, NamedTextColor.RED))
                    .append(Component.text(" eliminated! ($reasonText)", NamedTextColor.GRAY))
                    .build()
            )
        }

        messenger.playSound(player, Sound.ENTITY_GENERIC_DEATH, 1f, 1f)
        worldManager.eliminatePlayer(player, blockMaterial, messenger)
    }

    /**
     * Handles game over (draw).
     */
    private fun handleGameOverDraw() {
        for (player in Bukkit.getOnlinePlayers()) {
            messenger.showGameOverDrawTitle(player)
            messenger.playSound(player, Sound.ENTITY_ENDER_DRAGON_DEATH, 0.4f, 1f)
        }
        messenger.broadcastLeaderboard(playerScores)
        cleanup()
    }

    /**
     * Handles game over (winner).
     */
    private fun handleGameOverWinner(winner: Player) {
        for (player in Bukkit.getOnlinePlayers()) {
            messenger.showGameOverWinnerTitle(player, winner.name)
            messenger.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
        }
        messenger.broadcastLeaderboard(playerScores)
        cleanup()
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        onPlayerMove(event.player, event.to)
    }

    @EventHandler
    fun onUseFirework(event: PlayerElytraBoostEvent) {
        if (!timerManager.getTrackedPlayers().contains(event.player.uniqueId))
            return

        val firework = event.itemStack
        if (!ItemUtils.itemIsCustom(firework, ItemUtils.TEMPORAL_FIREWORK))
            return

        event.player.damage(ItemUtils.FIREWORK_COST.toDouble(), IGNORED_MULTIPLIER_DAMAGE_SOURCE)
        event.player.world.playSound(event.player.location, Sound.ENTITY_ENDERMAN_HURT, 1f, 1.25f)
        event.setShouldConsume(false)
        event.player.setCooldown(firework.type, 50)
    }

    @EventHandler
    fun onUseFireworkOnGround(event: PlayerInteractEvent) {

        if (event.action != Action.RIGHT_CLICK_BLOCK)
            return

        val item = event.item ?:
            return

        if (!ItemUtils.itemIsCustom(item, ItemUtils.TEMPORAL_FIREWORK))
            return

        event.isCancelled = true
    }

    @EventHandler
    fun onUseShop(event: PlayerInteractEvent) {

        val item = event.item ?:
            return

        if (!ItemUtils.itemIsCustom(item, ItemUtils.SHOP))
            return

        if (!timerManager.getTrackedPlayers().contains(event.player.uniqueId))
            return

        event.isCancelled = true
        val menu = BlitzShopMenu(event.player)
        menu.open()
    }

    @EventHandler
    fun onUseOxidizer(event: PlayerInteractEvent) {

        if (event.action != Action.RIGHT_CLICK_BLOCK)
            return

        val block = event.clickedBlock ?:
            return

        val item = event.item
        if (item == null || !ItemUtils.itemIsCustom(item, ItemUtils.OXIDIZER))
            return

        event.isCancelled = true
        val oxidizationResult = performOxidization(block.type)
        if (oxidizationResult == null) {
            event.player.sendMessage(Component.text("Oxidization failed! You can't oxidize this block!", NamedTextColor.RED))
            event.player.playSound(event.player.location, Sound.ENTITY_VEX_CHARGE, 1f, 0.5f)
            return
        }

        block.type = oxidizationResult

        event.player.sendMessage(Component.text("Block oxidized successfully!", NamedTextColor.GREEN))
        event.player.playSound(event.player.location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.5f)
        ParticleBuilder(Particle.CLOUD)
            .location(block.location.add(0.5, 0.5, 0.5))
            .count(20)
            .extra(0.2)
            .spawn()
        event.player.damage(ItemUtils.OXIDIZE_COST.toDouble(), IGNORED_MULTIPLIER_DAMAGE_SOURCE)
    }
}
