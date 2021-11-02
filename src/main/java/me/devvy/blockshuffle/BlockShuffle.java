//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package me.devvy.blockshuffle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class BlockShuffle extends JavaPlugin implements CommandExecutor, Listener {

    public static BlockShuffle getInstance() {
        return BlockShuffle.getPlugin(BlockShuffle.class);
    }

    private GameLoop gameTask;
    private final List<Material> validMaterials = new ArrayList<>();

    public List<Material> getValidMaterials() {
        return validMaterials;
    }

    public void onEnable() {

        this.getServer().getPluginManager().registerEvents(this, this);

        try {
            InputStream is = BlockShuffle.class.getResourceAsStream("/blocks.txt");
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);

            String line;
            while((line = br.readLine()) != null)
                this.validMaterials.add(Material.valueOf(line));

            br.close();
            isr.close();
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void start() {
        Objects.requireNonNull(Bukkit.getWorld("world")).setTime(0);
        Objects.requireNonNull(Bukkit.getWorld("world")).setDifficulty(Difficulty.NORMAL);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(GameMode.SURVIVAL);
            p.setHealth(20);
            p.setSaturation(20);
            p.setFoodLevel(20);
            p.setBedSpawnLocation(null, true);
            p.getInventory().clear();
            p.sendTitle(ChatColor.LIGHT_PURPLE + "Block Shuffle!", ChatColor.GRAY + "Get prepared!", 10, 50, 10);
            p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1, .8f);
        }

        stop();
        gameTask = new GameLoop();
        gameTask.runTaskTimer(this, 0, 20 / GameLoop.TASK_FREQUENCY);
        getServer().getPluginManager().registerEvents(gameTask, this);
    }

    public void stop() {
        if (gameTask == null)
            return;

        HandlerList.unregisterAll(gameTask);
        gameTask.stop();
        gameTask = null;

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(GameMode.CREATIVE);
            p.setPlayerListName(ChatColor.AQUA + p.getName());
        }
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("blockshuffle")) {
            if (args.length == 1) {
                if (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("tpstart")) {
                    if (gameTask != null) {
                        sender.sendMessage(ChatColor.GREEN + "Block Shuffle is already started.");
                        return false;
                    }

                    if (args[0].equalsIgnoreCase("tpstart")) {

                        boolean goodSpawn = false;
                        Location spawn = null;

                        while (!goodSpawn) {
                            int randX = (int) (Math.random() * 2000000 - 1000000);
                            int randZ = (int) (Math.random() * 2000000 - 1000000);
                            int randY = Objects.requireNonNull(Bukkit.getWorld("world")).getHighestBlockYAt(randX, randZ);
                            spawn = new Location(Bukkit.getWorld("world"), randX, randY, randZ);
                            System.out.println("[BlockShuffle] Testing " + randX + " / " + randY + " / " + randZ + " as a new spawnpoint...");

                            if (spawn.getBlock().getType() != Material.LAVA && spawn.getBlock().getType() != Material.WATER)
                                goodSpawn = true;
                        }

                        System.out.println("[BlockShuffle] Found spawnpoint!");
                        spawn.getWorld().setSpawnLocation(spawn);
                        for (Player p : Bukkit.getOnlinePlayers())
                            p.teleport(spawn.clone().add(0, 2, 0));
                    }

                    this.start();
                    sender.sendMessage(ChatColor.GREEN + "Block Shuffle started.");
                    return true;
                }

                if (args[0].equalsIgnoreCase("pause")) {
                    gameTask.setPaused(!gameTask.isPaused());
                    sender.sendMessage(ChatColor.GREEN + "Block Shuffle is now " + (gameTask.isPaused() ? "paused." : "unpaused."));
                    return true;
                }

                if (args[0].equalsIgnoreCase("stop")) {
                    stop();
                    sender.sendMessage(ChatColor.RED + "Block Shuffle stopped.");
                    return true;
                }

            } else if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
                if (gameTask != null) {
                    sender.sendMessage(ChatColor.GREEN + "Block Shuffle is already started.");
                    return false;
                }

                try {
                    int time = Integer.parseInt(args[1]);
                    start();  // TODO: support for time limit in command
                    sender.sendMessage(ChatColor.GREEN + "Block Shuffle started (" + time + ").");
                } catch (NumberFormatException var6) {
                    sender.sendMessage(ChatColor.RED + args[0] + " must be an integer.");
                }

                return true;
            }

            sender.sendMessage(ChatColor.RED + "Invalid usage. Please use:");
            sender.sendMessage(ChatColor.RED + "/blockshuffle start");
            sender.sendMessage(ChatColor.RED + "/blockshuffle stop");
            sender.sendMessage(ChatColor.RED + "/blockshuffle pause");
            return false;
        } else {
            return false;
        }
    }


}
