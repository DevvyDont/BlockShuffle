package me.devvy.blockshuffle;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class GameLoop extends BukkitRunnable implements Listener {

    public static String formatInterval(long t) {
        final long hr = TimeUnit.MILLISECONDS.toHours(t);
        final long min = TimeUnit.MILLISECONDS.toMinutes(t - TimeUnit.HOURS.toMillis(hr));
        final long sec = TimeUnit.MILLISECONDS.toSeconds(t - TimeUnit.HOURS.toMillis(hr) - TimeUnit.MINUTES.toMillis(min));
        final long ms = TimeUnit.MILLISECONDS.toMillis(t - TimeUnit.HOURS.toMillis(hr) - TimeUnit.MINUTES.toMillis(min) - TimeUnit.SECONDS.toMillis(sec));

        if (min > 0)
            return ChatColor.GREEN + String.format("%02d:%02d", min, sec);
        else if (sec >= 45)
            return ChatColor.YELLOW + String.format("%02d.%01ds", sec, ms / 100);
        else if (sec >= 30)
            return ChatColor.GOLD + String.format("%02d.%01ds", sec, ms / 100);
        else if (sec >= 10)
            return ChatColor.RED + String.format("%02d.%01ds", sec, ms / 100);
        else
            return  (ms / 100 > 4 ? ChatColor.DARK_RED.toString() : ChatColor.DARK_GRAY.toString()) + ChatColor.BOLD.toString() + String.format("%02d.%01ds", sec, ms / 100);
    }

    public enum GameState {
        PREPARE,
        PAUSED,
        INGAME;
    }

    public final static int TASK_FREQUENCY = 10;  // How many times a second this task runs

    private final int TIME_LIMIT = 300;
    private final int PREPARE_TIME_LIMIT = 180;

    Map<UUID, Material> assignedMaterialMap = new HashMap<>();
    List<UUID> failedPlayers = new ArrayList<>();
    Map<UUID, Integer> scoreTracker = new HashMap<>();

    private GameState state = GameState.PREPARE;
    private boolean paused = false;
    int timer = PREPARE_TIME_LIMIT * TASK_FREQUENCY;
    private int score = -1;

    public GameLoop() {
        for (Player p : Bukkit.getOnlinePlayers())
            scoreTracker.put(p.getUniqueId(), 0);
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        state = paused ? GameState.PAUSED : (score >= 0 ? GameState.INGAME : GameState.PREPARE);
    }

    private void increaseScore(Player player) {
        if (scoreTracker.containsKey(player.getUniqueId()))
            scoreTracker.put(player.getUniqueId(), scoreTracker.get(player.getUniqueId()) + 1);
        else
            scoreTracker.put(player.getUniqueId(), 1);
    }

    private net.md_5.bungee.api.ChatColor getPlacementColor(int index) {
        switch (index) {
            case 0:
                return net.md_5.bungee.api.ChatColor.of(java.awt.Color.YELLOW);
            case 1:
                return net.md_5.bungee.api.ChatColor.of(new java.awt.Color(230, 230, 230));
            case 2:
                return net.md_5.bungee.api.ChatColor.of(new java.awt.Color(200, 126, 80));
            default:
                return net.md_5.bungee.api.ChatColor.GRAY;
        }
    }

    private String getPlacementSuffix(int index) {

        char place = String.valueOf(index+1).charAt(String.valueOf(index).length() - 1);

        switch (place) {
            case '0':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                return "th";
            case '1':
                return "st";
            case '2':
                return "nd";
            case '3':
                return "rd";
            default:
                return "??";

        }
    }

    private void printStats() {

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.YELLOW.toString() + ChatColor.BOLD + "               Leaderboard");
        Bukkit.broadcastMessage(ChatColor.GREEN + "----------------------------------------");
        Bukkit.broadcastMessage("");
        List<UUID> orderedPlayers = new ArrayList<>(scoreTracker.keySet());
        Collections.shuffle(orderedPlayers);
        orderedPlayers.sort((o1, o2) -> {
            if (scoreTracker.get(o1) < scoreTracker.get(o2))
                return 1;
            else if (scoreTracker.get(o1).equals(scoreTracker.get(o2)))
                return 0;
            else
                return -1;
        });

        for (int i = 0; i < orderedPlayers.size(); i++) {
            UUID id = orderedPlayers.get(i);
            Bukkit.broadcastMessage(getPlacementColor(i).toString() + ChatColor.BOLD + (i + 1) + getPlacementSuffix(i) + ChatColor.GRAY + ": " + ChatColor.AQUA + Bukkit.getOfflinePlayer(id).getName() + ChatColor.GRAY + " - " + ChatColor.LIGHT_PURPLE + scoreTracker.get(id) + " blocks");
            Bukkit.broadcastMessage("");
        }
        Bukkit.broadcastMessage(ChatColor.GREEN + "----------------------------------------");

    }

    private void doBlockCheck(Player player) {
        boolean isOn = player.getLocation().getBlock().getRelative(BlockFace.DOWN).getType() == assignedMaterialMap.getOrDefault(player.getUniqueId(), null);
        if (isOn) {
            player.getWorld().playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, .5F, 1.0F);
            Bukkit.broadcastMessage(ChatColor.AQUA + "" + ChatColor.BOLD + player.getName() + ChatColor.GREEN + " found their block!");
            player.setPlayerListName(ChatColor.GREEN + "SAFE! " + ChatColor.GRAY + player.getName());
            player.sendTitle(ChatColor.GREEN + "Safe!", ChatColor.GRAY + "You're in the next round!", 10, 40, 20);
            increaseScore(player);
            assignedMaterialMap.remove(player.getUniqueId());
        }

    }

    private void displayTime() {

        double rawTimeLeft = (double)timer / TASK_FREQUENCY;
        double secondsLeft = Math.round(rawTimeLeft * 10) / 10.;
        int simpleSecondsLeft = (int) secondsLeft;

        String timeDisplay = formatInterval((long) (rawTimeLeft * 1000));

        for (Player player : Bukkit.getOnlinePlayers()) {

            switch (state) {

                case PAUSED:
                    player.sendActionBar(ChatColor.DARK_RED + "PAUSED " + ChatColor.DARK_GRAY + "| " + ChatColor.RED + timeDisplay);
                    break;

                case PREPARE:
                    player.sendActionBar(ChatColor.GRAY + "Assigning blocks in: " + ChatColor.RED + timeDisplay);

                    if ((simpleSecondsLeft == 30 || simpleSecondsLeft == 60) && timer % TASK_FREQUENCY == 0)
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING,1, .5f);
                    else if (simpleSecondsLeft <= 3 && timer % TASK_FREQUENCY == 0)
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING,1, 1.8f - simpleSecondsLeft * .15f);
                    else if (simpleSecondsLeft <= 5 && timer % TASK_FREQUENCY == 0)
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING,1, 1f);
                    else if (simpleSecondsLeft <= 10 && timer % TASK_FREQUENCY == 0)
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING,1, .75f);
                    break;

                case INGAME:
                    Material assignedMat = assignedMaterialMap.getOrDefault(player.getUniqueId(), null);
                    String matPrefix = assignedMat != null ? (ChatColor.GRAY + "Stand on: " + ChatColor.RED + ChatColor.BOLD + WordUtils.capitalizeFully(assignedMat.name().toLowerCase().replace("_", " ")) + ChatColor.GRAY + " | ") : "";
                    player.sendActionBar(matPrefix + timeDisplay);


                    if ((simpleSecondsLeft == 30 || simpleSecondsLeft == 60) && timer % TASK_FREQUENCY == 0)
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING,1, .5f);
                    else if (simpleSecondsLeft <= 3 && timer % TASK_FREQUENCY == 0)
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING,1, 1.8f - simpleSecondsLeft * .15f);
                    else if (simpleSecondsLeft <= 5 && timer % TASK_FREQUENCY == 0)
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING,1, 1f);
                    else if (simpleSecondsLeft <= 10 && timer % TASK_FREQUENCY == 0)
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING,1, .75f);
                    break;

            }

        }

    }

    private void pickBlock(Player player) {
        // Get a random block
        List<Material> mats = BlockShuffle.getInstance().getValidMaterials();
        int randomIndex = (int) (Math.random() * mats.size());
        Material material = mats.get(randomIndex);

        String name = material.name().toLowerCase().replace("_", " ");
        boolean vowel = "aeiou".indexOf(name.charAt(0)) != -1;

        Bukkit.broadcastMessage(ChatColor.AQUA + ChatColor.BOLD.toString() + player.getName() + ChatColor.YELLOW + " must find and stand on " + (vowel ? "an " : "a ") + ChatColor.RED + ChatColor.BOLD + WordUtils.capitalizeFully(name) + ChatColor.YELLOW + ".");

        assignedMaterialMap.put(player.getUniqueId(), material);
        player.setPlayerListName(ChatColor.RED + ChatColor.BOLD.toString() +  "[" + name.toUpperCase() + "] " + ChatColor.AQUA + player.getName());
    }

    public void stop() {
        paused = true;
        this.cancel();

        for (Player p : Bukkit.getOnlinePlayers())
            p.setPlayerListName(ChatColor.AQUA + p.getName());

        new BukkitRunnable() {
            @Override
            public void run() {
                BlockShuffle.getInstance().stop();
            }
        }.runTaskLater(BlockShuffle.getInstance(), 20*5);
    }

    public void run() {

        if (timer >= 0)
            displayTime();

        // If the game is paused for whatever reason we just need to show the clock
        if (paused)
            return;

        if (timer == TASK_FREQUENCY * -7) {
            timer = TIME_LIMIT * TASK_FREQUENCY;
            return;
        }


        if (state == GameState.PREPARE) {

            if (timer == 0) {
                timer = TIME_LIMIT * TASK_FREQUENCY;
                state = GameState.INGAME;
            }
            else
                timer--;
            return;
        }

        // Is the timer at the beginning?
        if (this.timer == TIME_LIMIT * TASK_FREQUENCY) {

            List<Player> playersStillIn = new ArrayList<>();

            // Loop through everyone online
            for (Player p : Bukkit.getOnlinePlayers()) {
                // If they haven't failed, give them a new block
                if (!failedPlayers.contains(p.getUniqueId()))
                    playersStillIn.add(p);
            }

            // Is the game over as a draw?
            if (playersStillIn.isEmpty()) {

                for (Player player : Bukkit.getOnlinePlayers()){
                    player.sendTitle(ChatColor.RED + "GAME OVER!", ChatColor.GRAY + "It was a draw!!!", 10, 100, 40);
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, .4f, 1);
                }
                stop();
                printStats();
                return;
            }
            // Is the game over due to one person alive?
            else if (playersStillIn.size() == 1) {
                for (Player player : Bukkit.getOnlinePlayers()){
                    player.sendTitle(ChatColor.RED + "GAME OVER!", ChatColor.AQUA + playersStillIn.get(0).getName() + ChatColor.GRAY + " won!", 10, 100, 40);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
                }
                stop();
                printStats();
                return;
            }

            score++;

            // Loop through everyone online
            for (Player p : Bukkit.getOnlinePlayers()) {
                // If they haven't failed, give them a new block
                if (playersStillIn.contains(p))
                    pickBlock(p);
                else
                    assignedMaterialMap.remove(p.getUniqueId());

                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, 1, 1);
                p.sendTitle(ChatColor.GOLD + "Round " + (score + 1), ChatColor.RED.toString() + playersStillIn.size() + ChatColor.GRAY +" players remain...", 10, 40, 40);
            }
        }

        // Is the timer over?
        if (this.timer == 0) {


            for (UUID uuid : assignedMaterialMap.keySet()) {

                Player p = Bukkit.getPlayer(uuid);
                if (p == null) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                    Bukkit.broadcastMessage(ChatColor.GRAY + "" + ChatColor.BOLD + op.getName() + ChatColor.DARK_GRAY + " was offline and failed to find their block!");
                    continue;
                }

                String name = assignedMaterialMap.get(uuid).name().toLowerCase().replace("_", " ");
                boolean vowel = "aeiou".indexOf(name.charAt(0)) != -1;

                Bukkit.broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + p.getName() + ChatColor.DARK_GRAY + " failed to find " + (vowel ? "an " : "a ") + ChatColor.RED + ChatColor.BOLD + WordUtils.capitalizeFully(name) + ChatColor.DARK_GRAY +"!");
                p.damage(0);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BAT_DEATH, .75f, .5f);
                p.setGameMode(GameMode.SPECTATOR);

                for (ItemStack is : p.getInventory().getContents())
                    if (is != null)
                        p.getWorld().dropItemNaturally(p.getEyeLocation().subtract(0, .25, 0), is);

                failedPlayers.add(uuid);
                p.setPlayerListName(ChatColor.RED + "FAILED " + p.getName());
            }

            this.timer = TIME_LIMIT * TASK_FREQUENCY;
            return;
        }

        --this.timer;

        // Has everyone found their block this run?
        if (timer > 0 && assignedMaterialMap.isEmpty())
            this.timer = -1;
    }

    @EventHandler
    public void onPlayerMoveEvent(PlayerMoveEvent event) {

        if (paused)
            return;

        if (!assignedMaterialMap.containsKey(event.getPlayer().getUniqueId()))
            return;

        if (!event.getFrom().getBlock().equals(event.getTo().getBlock()))
            doBlockCheck(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {

        if (failedPlayers.contains(event.getPlayer().getUniqueId()))
            return;

        if (assignedMaterialMap.containsKey(event.getPlayer().getUniqueId()))
            return;

        if (state != GameState.PREPARE) {
            pickBlock(event.getPlayer());
            scoreTracker.put(event.getPlayer().getUniqueId(), 0);
        }

    }
}
